package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.IValueCode;
import org.serialthreads.transformer.strategies.AbstractMethodTransformer;
import org.serialthreads.transformer.strategies.StackFrameCapture;
import org.serialthreads.transformer.strategies.MetaInfo;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.MethodCode.*;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;

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
    return "(" + THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")" + Type.VOID_TYPE;
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
    return desc.substring(0, index) + THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")" + Type.VOID_TYPE;
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
  protected void voidReturns() {
    Type returnType = Type.getReturnType(method.desc);
    if (returnType.getSort() == Type.VOID) {
      // Method already has return type void
      return;
    }

    logger.debug("      Replacing returns");

    InsnList instructions = method.instructions;

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    IValueCode returnTypeCode = code(returnType);
    for (AbstractInsnNode returnInstruction : returnInstructions(method)) {
      AbstractInsnNode previous = previousInstruction(returnInstruction);
      if (isTailCall(previous)) {
        // Tail call optimization:
        // The return value has already been saved into the thread by the capture code of the called method
        instructions.set(returnInstruction, new InsnNode(RETURN));
        logger.debug("        Optimized tail call to {}", methodName((MethodInsnNode) previous));
      } else {
        // Default case:
        // Save return value into the thread
        instructions.insert(returnInstruction, returnTypeCode.pushReturnValue(localThread));
        instructions.remove(returnInstruction);
      }
    }
  }

  //
  // Capture code inserted after method calls
  //

  @Override
  protected InsnList dummyReturn() {
    // Always use void returns, because all methods have been change to use void returns
    InsnList result = new InsnList();
    result.add(new InsnNode(RETURN));
    return result;
  }

  @Override
  protected void createCaptureCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner) {
    logger.debug("      Creating capture code for method call to {}", methodName(methodCall));

    if (!needsFrame() && isTailCall(metaInfo)) {
      // No frame needed -> no capture code
      // Tail call -> no need for separate early return statement
      // So no extra code at all is needed here
      logger.debug("        Optimized no frame tail call");
      return;
    }

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    LabelNode normal = new LabelNode();

    InsnList capture = new InsnList();

    // if not serializing "GOTO" normal
    capture.add(new VarInsnNode(ALOAD, localThread));
    capture.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    capture.add(new JumpInsnNode(IFEQ, normal));

    // capture frame and return early
    capture.add(StackFrameCapture.pushToFrame(method, methodCall, metaInfo, localFrame));
    capture.add(pushMethodToFrame(position, containsMoreThanOneMethodCall, suppressOwner, localPreviousFrame, localFrame));
    capture.add(new InsnNode(RETURN));

    // normal execution
    capture.add(normal);
    // restore return value of call, if any.
    // but not for tail calls, because the return value already has been stored in the stack by the called method
    if (isNotVoid(methodCall)) {
      if (isTailCall(metaInfo)) {
        logger.debug("        Optimized tail call");
      } else {
        capture.add(code(Type.getReturnType(methodCall.desc)).popReturnValue(localThread));
      }
    }

    // insert capture code
    method.instructions.insert(methodCall, capture);
  }

  //
  // Restore code to be able to resume a method call
  //

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
    if (isSelfCall(methodCall, metaInfo)) {
      // self call: owner == this
      restore.add(new VarInsnNode(ALOAD, 0));
    } else if (isNotStatic(clonedCall)) {
      // get owner
      restore.add(new VarInsnNode(ALOAD, localFrame));
      restore.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
      restore.add(new TypeInsnNode(CHECKCAST, clonedCall.owner));
    }

    // jump to cloned method call with thread and frame as arguments
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new VarInsnNode(ALOAD, localFrame));
    restore.add(clonedCall);

    // if not serializing "GOTO" normal, but restore the frame first
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    restore.add(new JumpInsnNode(IFEQ, restoreFrame));

    // early return, the frame already has been captured
    restore.add(new InsnNode(RETURN));

    // restore frame to be able to resume normal execution of method
    restore.add(restoreFrame);

    // restore stack "under" the returned value, if any
    restore.add(StackFrameCapture.popFromFrame(method, methodCall, metaInfo, localFrame));
    // restore return value of call, if any, but not for tail calls
    if (isNotVoid(methodCall)) {
      if (isTailCall(metaInfo)) {
        logger.debug("        Tail call optimized");
      } else {
        restore.add(code(Type.getReturnType(methodCall.desc)).popReturnValue(localThread));
      }
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
   * Fix maxs of method.
   * These have not to be exact (but may not be too small!), because it is just for debugging purposes.
   */
  protected void fixMaxs() {
    method.maxLocals += 1;
    // TODO 2009-10-11 mh: recalculate minimum maxs
    method.maxStack = Math.max(method.maxStack + 2, 5);
  }
}
