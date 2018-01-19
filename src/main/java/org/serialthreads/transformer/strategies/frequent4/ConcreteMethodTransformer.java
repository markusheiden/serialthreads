package org.serialthreads.transformer.strategies.frequent4;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.MethodCode.isNotStatic;

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
    nameAddedLocals();
    analyze();

    replaceReturns();
    insertCaptureCode();
    createRestoreHandlerMethod();
    addFrame();
    fixMaxs();

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

    InsnList getFrame = new InsnList();

    if (isNotStatic(method)) {
      // previousFrame.owner = this
      getFrame.add(new VarInsnNode(ALOAD, localPreviousFrame));
      getFrame.add(new VarInsnNode(ALOAD, 0));
      getFrame.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
    }

    getFrame.add(new VarInsnNode(ALOAD, localPreviousFrame));
    if (needsFrame()) {
      LabelNode normal = new LabelNode();

      // frame = previousFrame.next
      getFrame.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
      getFrame.add(new InsnNode(DUP));
      getFrame.add(new JumpInsnNode(IFNONNULL, normal));

      getFrame.add(new InsnNode(POP));
      // frame = previousFrame.addFrame();
      getFrame.add(stackFrameCode.nextFrame(localPreviousFrame));

      getFrame.add(normal);
    }
    getFrame.add(new VarInsnNode(ASTORE, localFrame));

    method.instructions.insertBefore(method.instructions.getFirst(), getFrame);
  }
}
