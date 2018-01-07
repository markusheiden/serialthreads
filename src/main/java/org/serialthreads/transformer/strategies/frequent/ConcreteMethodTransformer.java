package org.serialthreads.transformer.strategies.frequent;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * Method transformer for concrete methods.
 */
@SuppressWarnings({"UnusedAssignment"})
class ConcreteMethodTransformer extends MethodTransformer {
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected ConcreteMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
    super(clazz, method, classInfoCache);
  }

  /**
   * Transform method.
   *
   * @return Transformed method
   * @throws AnalyzerException In case of incorrect byte code of the original method
   */
  public MethodNode transform() throws AnalyzerException {
    shiftLocals();
    analyze();

    List<LabelNode> restores = insertCaptureCode(false);
    createRestoreHandlerMethod(restores);
    fixMaxs();

    return method;
  }

  /**
   * Insert frame restoring code at the begin of an interruptible method.
   *
   * @param restores Labels pointing to the generated restore codes for method calls.
   */
  private void createRestoreHandlerMethod(List<LabelNode> restores) {
    assert !restores.isEmpty() : "Precondition: !restores.isEmpty()";

    logger.debug("    Creating restore handler for method");

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    LabelNode normal = new LabelNode();
    LabelNode getThread = new LabelNode();

    // previousFrame = thread.frame;
    InsnList getFrame = new InsnList();
    getFrame.add(new VarInsnNode(ALOAD, localThread));
    getFrame.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));
    getFrame.add(new VarInsnNode(ASTORE, localPreviousFrame));

    InsnList restore = new InsnList();

    // frame = previousFrame.next;
    getFrame.add(new VarInsnNode(ALOAD, localPreviousFrame));
    getFrame.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    getFrame.add(new InsnNode(DUP));
    getFrame.add(new JumpInsnNode(IFNONNULL, normal));

    getFrame.add(new InsnNode(POP));
    // frame = thread.addFrame(previousFrame);
    getFrame.add(new VarInsnNode(ALOAD, localThread));
    getFrame.add(new VarInsnNode(ALOAD, localPreviousFrame));
    getFrame.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, "addFrame", "(" + FRAME_IMPL_DESC + ")" + FRAME_IMPL_DESC, false));

    getFrame.add(normal);
    getFrame.add(new VarInsnNode(ASTORE, localFrame));

    // TODO 2009-11-26 mh: remove me?
    // thread.frame = frame;
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new VarInsnNode(ALOAD, localFrame));
    restore.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));

    // if not serializing "GOTO" normal
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    restore.add(new JumpInsnNode(IFEQ, getThread));

    // else restore code dispatcher
    InsnList getMethod = new InsnList();
    getMethod.add(new VarInsnNode(ALOAD, localFrame));
    getMethod.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
    restore.add(restoreCodeDispatcher(getMethod, restores, 0));

    // insert label for normal body of method
    restore.add(getThread);

    // insert generated byte code
    insertMethodGetThreadStartCode(localThread, getFrame, restore);
  }
}
