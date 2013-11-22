package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.LocalVariablesShifter;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.MethodCode.firstLocal;

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
   * @exception AnalyzerException In case of incorrect byte code of the original method
   */
  public MethodNode transform() throws AnalyzerException {
    LocalVariablesShifter.shift(firstLocal(method), 3, method);
    analyze();

    voidReturns();
    insertCaptureCode(false);
    createRestoreHandlerMethod();
    addThreadAndFrame();
    fixMaxs();

    method.desc = changeDesc(method.desc);

    return method;
  }

  /**
   * Insert frame restoring code at the begin of an interruptible method.
   */
  private void createRestoreHandlerMethod() {
    logger.debug("    Creating restore handler for method");

    int local = firstLocal(method);
    final int localThread = local++; // == param thread
    final int localPreviousFrame = local++; // == param previousFrame
    final int localFrame = local++;

    LabelNode normal = new LabelNode();

    // frame = previousFrame.next
    InsnList getFrame = new InsnList();
    getFrame.add(new VarInsnNode(ALOAD, localPreviousFrame));
    getFrame.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    getFrame.add(new InsnNode(DUP));
    getFrame.add(new JumpInsnNode(IFNONNULL, normal));

    getFrame.add(new InsnNode(POP));
    // frame = thread.addFrame(previousFrame);
    getFrame.add(new VarInsnNode(ALOAD, localThread));
    getFrame.add(new VarInsnNode(ALOAD, localPreviousFrame));
    getFrame.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, "addFrame", "(" + FRAME_IMPL_DESC + ")" + FRAME_IMPL_DESC));

    getFrame.add(normal);
    getFrame.add(new VarInsnNode(ASTORE, localFrame));

    method.instructions.insertBefore(method.instructions.getFirst(), getFrame);
  }
}
