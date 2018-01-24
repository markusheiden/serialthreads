package org.serialthreads.transformer.strategies.frequent4;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.AbstractMethodTransformer;
import org.serialthreads.transformer.strategies.MetaInfo;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.MethodCode.*;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_INTERRUPT;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_TAIL_CALL;

/**
 * Base class for method transformers of {@link FrequentInterruptsTransformer4}.
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
    return "(" + FRAME_IMPL_DESC + ")" + Type.BOOLEAN_TYPE;
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
    return desc.substring(0, index) + FRAME_IMPL_DESC + ")" + Type.BOOLEAN_TYPE;
  }

  /**
   * Additionally push previous frame argument onto the stack for all interruptible method calls.
   * Alters the method descriptor to reflect these changes.
   */
  protected void addFrame() {
    InsnList instructions = method.instructions;

    final int localFrame = localFrame();

    for (MethodInsnNode methodCall : interruptibleMethodCalls) {
      if (!isRun(methodCall, classInfoCache) && !classInfoCache.isInterrupt(methodCall)) {
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
          // Save return value into the thread.
          // FIXME markus 2018-01-14: Get thread!
          int localThread = localThread();
          replacement.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "stack", THREAD_IMPL_DESC));
          replacement.add(new VarInsnNode(ASTORE, localThread));
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
    // We are serializing.
    capture.add(methodReturn(true));

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

    if (isTailCall(metaInfo)) {
      createCaptureCodeForMethodTail(methodCall, position, restoreCode);
      return;
    }

    final int localFrame = localFrame();

    LabelNode normal = new LabelNode();

    InsnList capture = new InsnList();

    // If not serializing "GOTO" normal.
    capture.add(new JumpInsnNode(IFEQ, normal));

    // Capture frame and return early.
    capture.add(captureFrame(methodCall, metaInfo));
    // frame.method = position;
    capture.add(setMethod(position));
    // We are already serializing.
    capture.add(methodReturn(true));

    // Restore code to continue.
    capture.add(restoreCode);

    // Normal execution.
    capture.add(normal);
    // Restore return value of call, if any.
    if (isNotVoid(methodCall)) {
      // FIXME markus 2018-01-14: Get thread!
      int localThread = localThread();
      capture.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "stack", THREAD_IMPL_DESC));
      capture.add(new VarInsnNode(ASTORE, localThread));
      capture.add(code(Type.getReturnType(methodCall.desc)).popReturnValue(localThread));
    }

    // Insert capture code.
    method.instructions.insert(methodCall, capture);
  }

  /**
   * Insert frame capturing code after returning from a method tail call.
   *
   * @param methodCall method call to generate capturing code for.
   * @param position position of method call in method.
   * @param restoreCode Restore code. Null if none required.
   */
  private void createCaptureCodeForMethodTail(MethodInsnNode methodCall, int position, InsnList restoreCode) {
    InsnList capture = new InsnList();

    // Early exit for tail calls.
    // The return value needs not to be restored, because it has already been stored by the cloned call.
    // The serializing flag is already on the stack from the cloned call.
    logger.debug("        Optimized tail call");
    capture.add(setMethod(position));
    capture.add(new InsnNode(IRETURN));

    capture.add(restoreCode);

    // Insert capture code.
    method.instructions.insert(methodCall, capture);
  }

  @Override
  public InsnList setOwner(MethodInsnNode methodCall, MetaInfo metaInfo, boolean suppressOwner) {
    return new InsnList();
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

    final int localFrame = localFrame();
    // Introduce new local holding the return value.
    final int localReturnValue = method.maxLocals;

    // Label "normal" points the code directly after the method call.
    LabelNode normal = new LabelNode();
    method.instructions.insert(methodCall, normal);
    LabelNode restoreFrame = new LabelNode();

    InsnList restoreCode = new InsnList();

    // Call interrupted method.
    restoreCode.add(pushOwner(methodCall, metaInfo));
    // Jump to cloned method call with thread and frame as arguments.
    restoreCode.add(new VarInsnNode(ALOAD, localFrame));
    restoreCode.add(clonedCall);

    // Early exit for tail calls.
    // The return value needs not to be restored, because it has already been stored by the cloned call.
    // The serializing flag is already on the stack from the cloned call.
    if (isTailCall(metaInfo)) {
      restoreCode.add(new InsnNode(IRETURN));
      logger.debug("        Tail call optimized");
      return restoreCode;
    }

    // If not serializing "GOTO" normal, but restore the frame first.
    restoreCode.add(new JumpInsnNode(IFEQ, restoreFrame));

    // Early return, the frame already has been captured.
    // We are already serializing.
    restoreCode.add(methodReturn(true));

    // Restore frame to be able to resume normal execution of the method.
    restoreCode.add(restoreFrame);

    // Restore stack "under" the returned value, if any.
    restoreCode.add(restoreFrame(methodCall, metaInfo));
    // Due to insertion point of the restore code, the following code is already directly after the insertion point:
    // Restore return value of call, if any.
    // if (isNotVoid(methodCall)) {
    //  restoreCode.add(code(Type.getReturnType(methodCall.desc)).popReturnValue(localFrame));
    // }
    // restoreCode.add(new JumpInsnNode(GOTO, normal));

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

  /**
   * Does the method need a frame for storing its state?.
   * <p/>
   * A frame is not needed, if following conditions apply:
   * - There is just one interruptible method call (-> no need to store the method index)
   * - The method call is a self call (-> no need to store the method owner)
   * - The method is a tail call (-> needs no storing of locals and stack)
   * The third condition is somewhat suboptimal,
   * but for now the easiest way to implement the detection, that neither the locals nor the stack is needed / used.
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
