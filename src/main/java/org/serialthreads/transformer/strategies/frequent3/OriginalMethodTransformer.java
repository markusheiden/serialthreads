package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.isNotStatic;

/**
 * Method transformer for original methods.
 */
@SuppressWarnings({"UnusedAssignment"})
class OriginalMethodTransformer extends MethodTransformer {
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected OriginalMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
    super(clazz, method, classInfoCache);
  }

  /**
   * Transform method.
   *
   * @return Transformed method
   * @throws AnalyzerException In case of incorrect byte code of the original method
   */
  public MethodNode transform() throws AnalyzerException {
    boolean concrete = !isAbstract(method);
    if (concrete) {
      shiftLocals();
      analyze();

      replaceReturns();
      insertCaptureCode();
      // updateExceptionTableForOriginalMethod();  // TODO: May not be needed
      createRestoreHandlerMethod();
      addThreadAndFrame();
      fixMaxs();
      nameLocals();
    }

    method.desc = changeDesc(method.desc);

    return method;
  }

  /**
   * Update exception table to cover inserted capture code in original method.
   * When an interruptible method call is inside a try-catch block, the capture code
   * inserted after the call must also be covered by the same exception handler.
   */
  private void updateExceptionTableForOriginalMethod() {
    if (method.tryCatchBlocks == null || method.tryCatchBlocks.isEmpty()) {
      return;
    }

    logger.debug("      Updating exception table for original method");

    // For each exception handler, extend it to cover capture code
    for (var tryCatchBlock : method.tryCatchBlocks) {
      var start = tryCatchBlock.start;
      var end = tryCatchBlock.end;

      // Find the last interruptible method call within this try block
      MethodInsnNode lastCallInBlock = null;
      for (var methodCall : interruptibleMethodCalls) {
        if (isInstructionInRange(methodCall, start, end)) {
          lastCallInBlock = methodCall;
        }
      }

      // If we found calls in this block, extend the end to cover capture code
      if (lastCallInBlock != null) {
        // Find the "normal" label which marks the end of capture code
        var normalLabel = findNormalLabel(lastCallInBlock);
        if (normalLabel != null) {
          tryCatchBlock.end = normalLabel;
          logger.debug("        Extended exception handler to cover capture code for {}", lastCallInBlock.name);
        }
      }
    }
  }

  /**
   * Find the "normal" execution label after capture code for a method call.
   *
   * @param methodCall Method call instruction.
   * @return Normal execution label, or null if not found.
   */
  private LabelNode findNormalLabel(MethodInsnNode methodCall) {
    // After a method call in original method (without restore), the pattern is:
    // methodCall -> IFEQ -> (capture code) -> serializing label -> RETURN -> normal label -> (restore return value)
    // We want to find the "normal" label
    var current = methodCall.getNext();
    int seenReturns = 0;
    while (current != null && seenReturns < 2) {
      // Look for the pattern: after we see a return, the next label is likely "normal"
      if (current.getOpcode() == org.objectweb.asm.Opcodes.IRETURN ||
          current.getOpcode() == org.objectweb.asm.Opcodes.RETURN) {
        seenReturns++;
        // After the return(s), look for the next label
        if (seenReturns == 1) {
          var next = current.getNext();
          while (next != null) {
            if (next instanceof LabelNode label) {
              return label;
            }
            next = next.getNext();
            // Stop if we hit another real instruction
            if (next != null && next.getOpcode() >= 0) {
              break;
            }
          }
        }
      }
      current = current.getNext();
    }
    return null;
  }

  /**
   * Check if an instruction is within the range defined by start and end labels.
   *
   * @param instruction Instruction to check.
   * @param start Start label of the range.
   * @param end End label of the range.
   * @return True if instruction is in range.
   */
  private boolean isInstructionInRange(AbstractInsnNode instruction, LabelNode start, LabelNode end) {
    var current = start.getNext();
    while (current != null && current != end) {
      if (current == instruction) {
        return true;
      }
      current = current.getNext();
    }
    return false;
  }

  /**
   * Insert frame restoring code at the begin of an interruptible method.
   */
  private void createRestoreHandlerMethod() {
    logger.debug("    Creating restore handler for method");

    var instructions = new InsnList();

    if (isNotStatic(method)) {
      // previousFrame.owner = this;
      instructions.add(threadCode.setOwner(localPreviousFrame));
    }

    if (needsFrame()) {
      // frame = previousFrame.next; // etc.
      instructions.add(threadCode.getNextFrame(localPreviousFrame, localFrame, true));
    } else {
      // Reuse previousFrame for return value.
      // frame = previousFrame;
      instructions.add(new VarInsnNode(ALOAD, localPreviousFrame));
      instructions.add(new VarInsnNode(ASTORE, localFrame));
    }

    method.instructions.insertBefore(method.instructions.getFirst(), instructions);
  }
}
