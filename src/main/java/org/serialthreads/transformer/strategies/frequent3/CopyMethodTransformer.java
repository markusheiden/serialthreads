package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.MethodNodeCopier;

import java.util.List;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.methodName;

/**
 * Method transformer for copied methods.
 */
@SuppressWarnings({"UnusedAssignment"})
class CopyMethodTransformer extends MethodTransformer {
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected CopyMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
    super(clazz, MethodNodeCopier.copy(method), classInfoCache);
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
      nameAddedLocals();
      analyze();

      replaceReturns();
      List<LabelNode> restores = insertCaptureAndRestoreCode(true);
      createRestoreHandlerCopy(restores);
      addThreadAndFrame();
      fixMaxs();
    }

    method.name = changeCopyName(method.name, method.desc);
    method.desc = changeCopyDesc(method.desc);
    clazz.methods.add(method);

    logger.debug("      Copied {} method {}", concrete? "concrete" : "abstract", methodName(clazz, method));

    return method;
  }

  /**
   * Insert frame restoring code at the begin of a copied method.
   *
   * @param restores Labels pointing to the generated restore codes for method calls.
   */
  private void createRestoreHandlerCopy(List<LabelNode> restores) {
    assert !restores.isEmpty() : "Precondition: !restoreCodes.isEmpty()";

    logger.debug("    Creating restore handler for copied method");

    final int paramThread = paramThread();
    final int paramPreviousFrame = paramPreviousFrame();

    final int localThread = localThread();
    final int localFrame = localFrame();

    InsnList instructions = new InsnList();

    if (needsFrame()) {
      // frame = previousFrame.next; // etc.
      instructions.add(threadCode.getNextFrame(paramPreviousFrame, localFrame, false));
    } else {
      // Reuse previousFrame for return value.
      // frame = previousFrame;
      instructions.add(new VarInsnNode(ALOAD, paramPreviousFrame));
      instructions.add(new VarInsnNode(ASTORE, localFrame));
    }

    if (paramThread != localThread) {
      // Move thread to the correct local.
      instructions.add(new VarInsnNode(ALOAD, paramThread));
      instructions.add(new VarInsnNode(ASTORE, localThread));
    }

    // Restore code dispatcher.
    instructions.add(restoreCodeDispatcher(pushMethod(), restores, 0));

    method.instructions.insertBefore(method.instructions.getFirst(), instructions);
  }
}
