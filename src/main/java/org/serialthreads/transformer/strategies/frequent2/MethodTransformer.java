package org.serialthreads.transformer.strategies.frequent2;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.AbstractMethodTransformer;
import org.serialthreads.transformer.strategies.MetaInfo;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.serialthreads.transformer.code.MethodCode.dummyReturnStatement;
import static org.serialthreads.transformer.code.MethodCode.escapeForMethodName;
import static org.serialthreads.transformer.code.MethodCode.isNotVoid;
import static org.serialthreads.transformer.code.MethodCode.methodName;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_INTERRUPT;

/**
 * Base class for method transformers of {@link FrequentInterruptsTransformer2}.
 */
@SuppressWarnings({"UnusedAssignment", "UnusedDeclaration"})
abstract class MethodTransformer extends AbstractMethodTransformer {
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected MethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
    super(clazz, method, classInfoCache);
  }

  /**
   * Change the name of a copied method.
   *
   * @param name name of method
   * @param desc parameters
   * @return changed name
   */
  protected String changeCopyName(String name, String desc) {
    return name + "$$" + escapeForMethodName(desc) + "$$";
  }

  /**
   * Change parameters of a copied method.
   *
   * @param desc parameters
   * @return changed parameters
   */
  protected String changeCopyDesc(String desc) {
    return "(" + THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")" + Type.getReturnType(desc);
  }

  //
  // Capture code inserted after method calls
  //

  @Override
  protected void createCaptureCode(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, InsnList restoreCode) {
    if (metaInfo.tags.contains(TAG_INTERRUPT)) {
      createCaptureCodeForInterrupt(methodCall, metaInfo, position, suppressOwner, restoreCode);
    } else {
      createCaptureCodeForMethod(methodCall, metaInfo, position, suppressOwner, restoreCode);
    }
  }

  /**
   * Insert frame capturing code when starting an interrupt.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @param position position of method call in method
   * @param suppressOwner suppress capturing of owner?
   * @param restoreCode Restore code. Null if none required.
   */
  protected void createCaptureCodeForInterrupt(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, InsnList restoreCode) {
    logger.debug("      Creating capture code for interrupt");

    InsnList capture = new InsnList();

    // Capture frame and return early.
    capture.add(captureFrame(methodCall, metaInfo));
    // frame.method = position;
    capture.add(setMethod(position));
    // previousFrame.owner = this;
    capture.add(setOwner(methodCall, metaInfo, suppressOwner));
    // thread.serializing = true;
    capture.add(threadCode.setSerializing(localThread(), true));
    // return;
    capture.add(dummyReturnStatement(method));

    // Restore code to continue.
    capture.add(restoreCode);

    // Replace dummy call of interrupt method by capture code.
    method.instructions.insert(methodCall, capture);
    method.instructions.remove(methodCall);
  }

  /**
   * Insert frame capturing code after returning from a method call.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @param position position of method call in method
   * @param suppressOwner suppress capturing of owner?
   * @param restoreCode Restore code. Null if none required.
   */
  protected void createCaptureCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, InsnList restoreCode) {
    logger.debug("      Creating capture code for method call to {}", methodName(methodCall));

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    LabelNode normal = new LabelNode();

    InsnList capture = new InsnList();

    // if (!thread.serializing) "GOTO" normal.
    capture.add(threadCode.pushSerializing(localThread));
    capture.add(new JumpInsnNode(IFEQ, normal));

    // get rid of dummy return value of called method first
    if (isNotVoid(methodCall)) {
      capture.add(code(Type.getReturnType(methodCall.desc)).pop());
    }

    // Capture frame and return early.
    capture.add(captureFrame(methodCall, metaInfo));
    // frame.method = position;
    capture.add(setMethod(position));
    // previousFrame.owner = this;
    capture.add(setOwner(methodCall, metaInfo, suppressOwner));
    // return;
    capture.add(dummyReturnStatement(method));

    // Restore code to continue.
    capture.add(restoreCode);

    // normal execution
    capture.add(normal);

    // TODO 2009-11-26 mh: remove me?
    // thread.frame = frame;
    capture.add(threadCode.setFrame(localThread, localFrame));

    // insert capture code
    method.instructions.insert(methodCall, capture);
  }

  //
  // Restore code to be able to resume a method call
  //

  @Override
  protected InsnList createRestoreCode(MethodInsnNode methodCall, MetaInfo metaInfo) {
    return metaInfo.tags.contains(TAG_INTERRUPT) ?
      createRestoreCodeForInterrupt(methodCall, metaInfo) :
      createRestoreCodeForMethod(methodCall, metaInfo);
  }

  /**
   * Create restore code for ending an interrupt.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @return restore code
   */
  protected InsnList createRestoreCodeForInterrupt(MethodInsnNode methodCall, MetaInfo metaInfo) {
    logger.debug("      Creating restore code for interrupt");

    LabelNode normal = new LabelNode();
    method.instructions.insert(methodCall, normal);

    InsnList restoreCode = new InsnList();
    // Stop deserializing.
    restoreCode.add(threadCode.setSerializing(localThread(), false));
    // Restore frame.
    restoreCode.add(restoreFrame(methodCall, metaInfo));
    // Resume.
    restoreCode.add(new JumpInsnNode(GOTO, normal));

    return restoreCode;
  }

  /**
   * Create method specific frame restore code.
   *
   * @param methodCall method call to generate restore code for
   * @param metaInfo Meta information about method call
   * @return restore code
   */
  protected InsnList createRestoreCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo) {
    logger.debug("      Creating restore code for method call to {}", methodName(methodCall));

    MethodInsnNode clonedCall = copyMethodCall(methodCall);

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();
    // Introduce new local holding the return value
    final int localReturnValue = method.maxLocals;

    // label "normal" points the code directly after the method call
    LabelNode normal = new LabelNode();
    method.instructions.insert(methodCall, normal);
    LabelNode restoreFrame = new LabelNode();

    InsnList restoreCode = new InsnList();

    // call interrupted method
    restoreCode.add(pushOwner(methodCall, metaInfo));
    // jump to cloned method call with thread and frame as arguments
    restoreCode.add(new VarInsnNode(ALOAD, localThread));
    restoreCode.add(new VarInsnNode(ALOAD, localFrame));
    restoreCode.add(clonedCall);

    // if (!thread.serializing) "GOTO" normal, but restore the frame first.
    restoreCode.add(threadCode.pushSerializing(localThread));
    restoreCode.add(new JumpInsnNode(IFEQ, restoreFrame));

    // early return, the frame already has been captured
    restoreCode.add(dummyReturnStatement(method));

    // restore frame to be able to resume normal execution of method
    restoreCode.add(restoreFrame);

    // TODO 2009-11-26 mh: remove me?
    // set the current frame, because the next called method will need it
    // thread.frame = frame;
    restoreCode.add(threadCode.setFrame(localThread, localFrame));

    // restore stack "under" the returned value, if any
    // TODO 2009-10-17 mh: avoid restore, if method returns directly after returning from called method???
    final boolean needToSaveReturnValue = isNotVoid(clonedCall) && metaInfo.frameAfter.getStackSize() > 1;
    if (needToSaveReturnValue) {
      restoreCode.add(code(Type.getReturnType(clonedCall.desc)).store(localReturnValue));
    }
    restoreCode.add(restoreFrame(clonedCall, metaInfo));
    if (needToSaveReturnValue) {
      restoreCode.add(code(Type.getReturnType(clonedCall.desc)).load(localReturnValue));
    }
    restoreCode.add(new JumpInsnNode(GOTO, normal));

    return restoreCode;
  }

  /**
   * Copies method call and changes the signature.
   *
   * @param methodCall method call
   */
  private MethodInsnNode copyMethodCall(MethodInsnNode methodCall) {
    MethodInsnNode result = (MethodInsnNode) methodCall.clone(null);
    result.name = changeCopyName(methodCall.name, methodCall.desc);
    result.desc = changeCopyDesc(methodCall.desc);

    return result;
  }

  //
  //
  //

  /**
   * Fix maxs of method.
   * These have not to be exact (but may not be too small!), because it is just for debugging purposes.
   */
  protected void fixMaxs() {
    method.maxLocals += 1;
    // TODO 2009-10-11 mh: recalculate minimum maxs
    method.maxStack = Math.max(method.maxStack + 2, 5);
  }
}
