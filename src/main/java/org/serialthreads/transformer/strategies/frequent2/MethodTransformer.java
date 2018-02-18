package org.serialthreads.transformer.strategies.frequent2;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.AbstractMethodTransformer;
import org.serialthreads.transformer.strategies.MetaInfo;

import static org.objectweb.asm.Opcodes.ALOAD;
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
   * Local holding the thread.
   * This is the parameter holding the thread in original methods.
   */
  protected final int localThread() {
    return local(0);
  }

  /**
   * Add names for added locals.
   */
  protected void nameLocals() {
    nameLocal(localThread(), THREAD_IMPL_DESC, "thread");
    nameLocal(localPreviousFrame(), FRAME_IMPL_DESC, "previousFrame");
    nameLocal(localFrame(), FRAME_IMPL_DESC, "frame");
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
  // Capture and restore code inserted after method calls.
  //

  @Override
  protected LabelNode createCaptureAndRestoreCode(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, boolean restore) {
    if (metaInfo.tags.contains(TAG_INTERRUPT)) {
      return createCaptureAndRestoreCodeForInterrupt(methodCall, metaInfo, position, suppressOwner, restore);
    } else {
      return createCaptureAndRestoreCodeForMethod(methodCall, metaInfo, position, suppressOwner, restore);
    }
  }

  /**
   * Insert frame capturing and restore code for interrupts.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @param position position of method call in method
   * @param suppressOwner suppress capturing of owner?
   * @param restore Generate restore code too?.
   * @return Label to restore code, or null, if no restore code has been generated.
   */
  protected LabelNode createCaptureAndRestoreCodeForInterrupt(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, boolean restore) {
    logger.debug("      Creating capture code for interrupt");

    InsnList instructions = new InsnList();

    // Capture frame and return early.
    instructions.add(threadCode.captureFrame(methodCall, metaInfo, localFrame()));
    // frame.method = position;
    instructions.add(setMethod(position));
    // previousFrame.owner = this;
    instructions.add(setOwner(methodCall, metaInfo, suppressOwner));
    // thread.serializing = true;
    instructions.add(threadCode.setSerializing(localThread(), true));
    // return;
    instructions.add(dummyReturnStatement(method));

    // Restore code to continue.
    LabelNode restoreLabel = new LabelNode();
    if (restore) {
      instructions.add(restoreLabel);

      // Stop deserializing.
      instructions.add(threadCode.setSerializing(localThread(), false));
      // Restore frame.
      instructions.add(threadCode.restoreFrame(methodCall, metaInfo, localFrame()));
      // Continue.
    }

    // Replace dummy call of interrupt method by capture and restore code.
    replace(methodCall, instructions);

    return restoreLabel;
  }

  /**
   * Insert frame capturing and restore code after method calls.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @param position position of method call in method
   * @param suppressOwner suppress capturing of owner?
   * @param restore Generate restore code too?.
   * @return Label to restore code, or null, if no restore code has been generated.
   */
  protected LabelNode createCaptureAndRestoreCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, boolean restore) {
    logger.debug("      Creating capture code for method call to {}", methodName(methodCall));

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    LabelNode normal = new LabelNode();

    InsnList instructions = new InsnList();

    // if (!thread.serializing) "GOTO" normal.
    instructions.add(threadCode.pushSerializing(localThread));
    instructions.add(new JumpInsnNode(IFEQ, normal));

    // get rid of dummy return value of called method first
    if (isNotVoid(methodCall)) {
      instructions.add(code(Type.getReturnType(methodCall.desc)).pop());
    }

    // Capture frame and return early.
    instructions.add(threadCode.captureFrame(methodCall, metaInfo, localFrame));
    // frame.method = position;
    instructions.add(setMethod(position));
    // previousFrame.owner = this;
    instructions.add(setOwner(methodCall, metaInfo, suppressOwner));
    // return;
    instructions.add(dummyReturnStatement(method));

    // Restore code to continue.
    LabelNode restoreLabel = new LabelNode();
    if (restore) {
      instructions.add(restoreLabel);

      // Introduce new local holding the return value
      final int localReturnValue = method.maxLocals;

      LabelNode restoreFrame = new LabelNode();

      // call interrupted method
      instructions.add(pushOwner(methodCall, metaInfo));
      // jump to cloned method call with thread and frame as arguments
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      MethodInsnNode clonedCall = copyMethodCall(methodCall);
      instructions.add(clonedCall);

      // if (!thread.serializing) "GOTO" normal, but restore the frame first.
      instructions.add(threadCode.pushSerializing(localThread));
      instructions.add(new JumpInsnNode(IFEQ, restoreFrame));

      // early return, the frame already has been captured
      instructions.add(dummyReturnStatement(method));

      // restore frame to be able to resume normal execution of method
      instructions.add(restoreFrame);

      // TODO 2009-11-26 mh: remove me?
      // set the current frame, because the next called method will need it
      // thread.frame = frame;
      instructions.add(threadCode.setFrame(localThread, localFrame));

      // restore stack "under" the returned value, if any
      // TODO 2009-10-17 mh: avoid restore, if method returns directly after returning from called method???
      final boolean needToSaveReturnValue = isNotVoid(clonedCall) && metaInfo.frameAfter.getStackSize() > 1;
      if (needToSaveReturnValue) {
        instructions.add(code(Type.getReturnType(clonedCall.desc)).store(localReturnValue));
      }
      instructions.add(threadCode.restoreFrame(clonedCall, metaInfo, localFrame));
      if (needToSaveReturnValue) {
        instructions.add(code(Type.getReturnType(clonedCall.desc)).load(localReturnValue));
      }
    }

    // normal execution
    instructions.add(normal);

    // TODO 2009-11-26 mh: remove me?
    // thread.frame = frame;
    instructions.add(threadCode.setFrame(localThread, localFrame));

    // insert capture code
    method.instructions.insert(methodCall, instructions);

    return restoreLabel;
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
