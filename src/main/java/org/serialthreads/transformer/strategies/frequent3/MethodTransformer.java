package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.AbstractMethodTransformer;
import org.serialthreads.transformer.strategies.MetaInfo;
import org.serialthreads.transformer.strategies.StackFrameCapture;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.MethodCode.*;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_TAIL_CALL;

/**
 * Base class for method transformers of {@link FrequentInterruptsTransformer3}.
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
   * Computes an unique name based on the name and the descriptor.
   *
   * @param name name of method
   * @param desc parameters
   * @return changed name
   */
  protected String changeCopyName(String name, String desc) {
    return name + "$$" + escapeForMethodName(desc) + "$$";
  }

  /**
   * Change the parameters of a copied method to (Thread, Frame).
   *
   * @param desc parameters
   * @return changed parameters
   */
  protected String changeCopyDesc(String desc) {
    return "(" + THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")" + Type.BOOLEAN_TYPE;
  }

  /**
   * Change parameters of a method.
   * Inserts thread and frame as additional parameters at the end.
   *
   * @param desc parameters
   * @return changed parameters
   */
  protected String changeDesc(String desc) {
    int index = desc.indexOf(")");
    return desc.substring(0, index) + THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")" + Type.BOOLEAN_TYPE;
  }

  /**
   * Additionally push thread and previous frame arguments onto the stack for all interruptible method calls.
   * Alters the method descriptor to reflect these changes.
   */
  protected void addThreadAndFrame() {
    InsnList instructions = method.instructions;

    final int localThread = localThread();
    final int localFrame = localFrame();

    for (MethodInsnNode methodCall : interruptibleMethodCalls) {
      if (!isRun(methodCall, classInfoCache) && !classInfoCache.isInterrupt(methodCall)) {
        instructions.insertBefore(methodCall, new VarInsnNode(ALOAD, localThread));
        instructions.insertBefore(methodCall, new VarInsnNode(ALOAD, localFrame));
        methodCall.desc = changeDesc(methodCall.desc);
      }
    }
  }

  /**
   * Replace returns with void returns.
   * The return value will be captured in the previous frame.
   */
  protected void replaceReturns() {
    logger.debug("      Replacing returns");

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    Type returnType = Type.getReturnType(method.desc);
    for (AbstractInsnNode returnInstruction : returnInstructions(method)) {
      AbstractInsnNode previous = previousInstruction(returnInstruction);
      InsnList replacement = new InsnList();
      if (isTailCall(previous)) {
        // Tail call optimization:
        // The return value has already been saved into the thread by the capture code of the called method.
        replacement.add(methodReturn(false));
        logger.debug("        Optimized tail call to {}", methodName((MethodInsnNode) previous));
      } else {
        if (returnType.getSort() != Type.VOID) {
          // Default case:
          // Save return value into the thread
          replacement.add(code(returnType).pushReturnValue(localThread));
        }
        replacement.add(methodReturn(false));
      }

      method.instructions.insert(returnInstruction, replacement);
      method.instructions.remove(returnInstruction);
    }
  }

  //
  // Capture code inserted after method calls
  //


  @Override
  protected InsnList startSerializing() {
    // Interrupt starts serializing.
    return methodReturn(true);
  }

  @Override
  protected void createCaptureCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner) {
    logger.debug("      Creating capture code for method call to {}", methodName(methodCall));

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    LabelNode normal = new LabelNode();

    InsnList capture = new InsnList();

    if (isTailCall(metaInfo)) {
      // Early exit for tail calls.
      // The return value needs not to be restored, because it has already been stored by the cloned call.
      // The serializing flag is already on the stack from the cloned call.
      logger.debug("        Optimized tail call");
      if (hasMoreThanOneMethodCall()) {
        capture.add(pushMethodToFrame(methodCall, metaInfo, position, suppressOwner));
      }
      capture.add(new InsnNode(IRETURN));
      method.instructions.insert(methodCall, capture);
      return;
    }

    // if not serializing "GOTO" normal
    capture.add(new JumpInsnNode(IFEQ, normal));

    // capture frame and return early
    capture.add(StackFrameCapture.pushToFrame(method, methodCall, metaInfo, localFrame));
    capture.add(pushMethodToFrame(methodCall, metaInfo, position, suppressOwner));
    // We are already serializing
    capture.add(methodReturn(true));

    // normal execution
    capture.add(normal);
    // restore return value of call, if any.
    // but not for tail calls, because the return value already has been stored in the stack by the called method
    if (isNotVoid(methodCall)) {
      capture.add(code(Type.getReturnType(methodCall.desc)).popReturnValue(localThread));
    }

    // insert capture code
    method.instructions.insert(methodCall, capture);
  }

  //
  // Restore code to be able to resume a method call
  //

  @Override
  protected InsnList stopDeserializing() {
    // Nothing to do, because the serializing flag is not used anymore.
    return new InsnList();
  }

  @Override
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

    InsnList restore = new InsnList();

    // call interrupted method
    restore.add(StackFrameCapture.popOwnerFromFrame(methodCall, metaInfo, localFrame));
    // jump to cloned method call with thread and frame as arguments
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new VarInsnNode(ALOAD, localFrame));
    restore.add(clonedCall);

    // Early exit for tail calls.
    // The return value needs not to be restored, because it has already been stored by the cloned call.
    // The serializing flag is already on the stack from the cloned call.
    if (isTailCall(metaInfo)) {
      restore.add(new InsnNode(IRETURN));
      logger.debug("        Tail call optimized");
      return restore;
    }

    // if not serializing "GOTO" normal, but restore the frame first
    restore.add(new JumpInsnNode(IFEQ, restoreFrame));

    // Early return, the frame already has been captured.
    // We are already serializing.
    restore.add(methodReturn(true));

    // restore frame to be able to resume normal execution of method
    restore.add(restoreFrame);

    // restore stack "under" the returned value, if any
    restore.add(StackFrameCapture.popFromFrame(method, methodCall, metaInfo, localFrame));
    // restore return value of call, if any, but not for tail calls
    if (isNotVoid(methodCall)) {
      restore.add(code(Type.getReturnType(methodCall.desc)).popReturnValue(localThread));
    }
    restore.add(new JumpInsnNode(GOTO, normal));

    return restore;
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

  /**
   * Does the method need a frame for storing its state?.
   * <p/>
   * A frame is not needed, if following conditions apply:
   * - There is just one interruptible method call (-> no need to store the method index)
   * - The method call is a self call (-> no need to store the method owner)
   * - The method is a tail call (-> needs no storing of locals and stack)
   * The third condition is somewhat suboptimal,
   * but for first the easiest way to implement the detection, that neither the locals nor the stack is needed / used.
   */
  protected final boolean needsFrame() {
    if (interruptibleMethodCalls.size() != 1) {
      return true;
    }

    MethodInsnNode methodCall = interruptibleMethodCalls.iterator().next();
    MetaInfo metaInfo = metaInfos.get(methodCall);
    return !isSelfCall(methodCall, metaInfo) || !isTailCall(metaInfo);
  }

  /**
   * Check if the given instruction is a tail call.
   *
   * @param instruction Instruction
   */
  protected final boolean isTailCall(AbstractInsnNode instruction) {
    MetaInfo metaInfo = metaInfos.get(instruction);
    return metaInfo != null && isTailCall(metaInfo);
  }

  /**
   * Check if the given instruction is a tail call.
   *
   * @param metaInfo Meta information about method call
   */
  protected final boolean isTailCall(MetaInfo metaInfo) {
    return metaInfo != null && metaInfo.tags.contains(TAG_TAIL_CALL);
  }

  //
  //
  //

  /**
   * Create return instruction for method.
   * Returns the given serializing flag, or in case of the run method uses a void return.
   *
   * @param serializing Serializing flag.
   */
  private InsnList methodReturn(boolean serializing) {
    InsnList result = new InsnList();
    if (isRun(clazz, method, classInfoCache)) {
      result.add(new InsnNode(RETURN));
    } else {
      result.add(new InsnNode(serializing? ICONST_1 : ICONST_0));
      result.add(new InsnNode(IRETURN));
    }
    return result;
  }

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
