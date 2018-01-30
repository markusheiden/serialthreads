package org.serialthreads.transformer.strategies.frequent4;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
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
      nameAddedLocals();
      analyze();

      replaceReturns();
      insertCaptureCode();
      createRestoreHandlerMethod();
      addFrame();
      fixMaxs();
    }

    method.desc = changeDesc(method.desc);

    return method;
  }

  /**
   * Insert frame restoring code at the begin of an interruptible method.
   */
  private void createRestoreHandlerMethod() {
    logger.debug("    Creating restore handler for method");

    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    InsnList instructions = new InsnList();

    if (isNotStatic(method)) {
      // previousFrame.owner = this;
      instructions.add(threadCode.setOwner(localPreviousFrame));
    }

    if (needsFrame()) {
      // frame = previousFrame.next; // etc.
      instructions.add(threadCode.getNextFrame(localPreviousFrame, localFrame, true));
    } else {
      // localFrame = localPreviousFrame;
      instructions.add(new VarInsnNode(ALOAD, localPreviousFrame));
      instructions.add(new VarInsnNode(ASTORE, localFrame));
    }

    method.instructions.insertBefore(method.instructions.getFirst(), instructions);
  }
}
