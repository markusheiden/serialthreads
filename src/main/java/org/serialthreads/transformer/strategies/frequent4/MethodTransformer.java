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
  // Capture and restore code inserted after method calls.
  //

  @Override
  protected LabelNode createCaptureAndRestoreCode(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, boolean restore) {
    if (metaInfo.tags.contains(TAG_INTERRUPT)) {
      return createCaptureAndRestoreCodeForInterrupt(methodCall, metaInfo, position, suppressOwner, restore);
    } else if (isTailCall(metaInfo)) {
      return createCaptureAndRestoreCodeForMethodTail(methodCall, metaInfo, position, restore);
    } else {
      return createCaptureAndRestoreCodeForMethod(methodCall, metaInfo, position, suppressOwner, restore);
    }
  }

  /**
   * Insert frame capturing and restore code for interrupts.
   *
   * @param methodCall Method call to generate capturing code for.
   * @param metaInfo Meta information about method call.
   * @param position Position of method call in method.
   * @param suppressOwner Suppress capturing of owner?
   * @param restore Generate restore code too?.
   * @return Label to restore code, or null, if no restore code has been generated.
   */
  protected LabelNode createCaptureAndRestoreCodeForInterrupt(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, boolean restore) {
    logger.debug("      Creating capture code for interrupt");

    InsnList instructions = new InsnList();

    // Capture frame and return early.
    instructions.add(captureFrame(methodCall, metaInfo));
    // frame.method = position;
    instructions.add(setMethod(position));
    // We are serializing.
    instructions.add(methodReturn(true));

    // Restore code to continue.
    LabelNode restoreLabel = new LabelNode();
    if (restore) {
      instructions.add(restoreLabel);

      // Restore frame.
      instructions.add(restoreFrame(methodCall, metaInfo));
      // Continue.
    }

    // Replace dummy call of interrupt method by capture and restore code.
    method.instructions.insert(methodCall, instructions);
    method.instructions.remove(methodCall);

    return restoreLabel;
  }

  /**
   * Insert frame capturing and restore code after method calls.
   *
   * @param methodCall Method call to generate capturing code for.
   * @param metaInfo Meta information about method call.
   * @param position Position of method call in method.
   * @param suppressOwner Suppress capturing of owner?
   * @param restore Generate restore code too?.
   * @return Label to restore code, or null, if no restore code has been generated.
   */
  protected LabelNode createCaptureAndRestoreCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, boolean restore) {
    logger.debug("      Creating capture code for method call to {}", methodName(methodCall));

    final int localFrame = localFrame();

    LabelNode normal = new LabelNode();

    InsnList instructions = new InsnList();

    // If not serializing "GOTO" normal.
    instructions.add(new JumpInsnNode(IFEQ, normal));

    // Capture frame and return early.
    instructions.add(captureFrame(methodCall, metaInfo));
    // frame.method = position;
    instructions.add(setMethod(position));
    // We are already serializing.
    instructions.add(methodReturn(true));

    // Restore code to continue.
    LabelNode restoreLabel = new LabelNode();
    if (restore) {
      instructions.add(restoreLabel);

      LabelNode restoreFrame = new LabelNode();

      // Call interrupted method.
      instructions.add(pushOwner(methodCall, metaInfo));
      // Jump to cloned method call with thread and frame as arguments.
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(copyMethodCall(methodCall));

      // If not serializing "GOTO" normal, but restore the frame first.
      instructions.add(new JumpInsnNode(IFEQ, restoreFrame));

      // Early return, the frame already has been captured.
      // We are already serializing.
      instructions.add(methodReturn(true));

      // Restore frame to be able to resume normal execution of the method.
      instructions.add(restoreFrame);

      // Restore stack "under" the returned value, if any.
      instructions.add(restoreFrame(methodCall, metaInfo));
    }

    // Normal execution.
    instructions.add(normal);
    // Restore return value of call, if any.
    if (isNotVoid(methodCall)) {
      // FIXME markus 2018-01-14: Get thread!
      int localThread = localThread();
      instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "stack", THREAD_IMPL_DESC));
      instructions.add(new VarInsnNode(ASTORE, localThread));
      instructions.add(code(Type.getReturnType(methodCall.desc)).popReturnValue(localThread));
    }

    // Insert capture code.
    method.instructions.insert(methodCall, instructions);

    return restoreLabel;
  }

  /**
   * Insert frame capturing and restore code after method tail calls.
   *
   * @param methodCall Method call to generate capturing code for.
   * @param position Position of method call in method.
   * @param restore Generate restore code too?.
   * @return Label to restore code, or null, if no restore code has been generated.
   */
  private LabelNode createCaptureAndRestoreCodeForMethodTail(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean restore) {
    logger.debug("      Creating capture code for method tail call to {}", methodName(methodCall));

    final int localFrame = localFrame();

    InsnList instructions = new InsnList();

    // Early exit for tail calls.
    // The return value needs not to be restored, because it has already been stored by the cloned call.
    // frame.method = position;
    instructions.add(setMethod(position));
    // The serializing flag is already on the stack from the cloned call.
    // return serializing;
    instructions.add(methodReturn(null));

    // Restore code to continue.
    LabelNode restoreLabel = new LabelNode();
    if (restore) {
      instructions.add(restoreLabel);

      // Call interrupted method.
      instructions.add(pushOwner(methodCall, metaInfo));
      // Jump to cloned method call with thread and frame as arguments.
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(copyMethodCall(methodCall));
      // Early exit for tail calls.
      // The return value needs not to be restored, because it has already been stored by the cloned call.
      // The serializing flag is already on the stack from the cloned call.
      instructions.add(methodReturn(null));
    }

    // Insert capture code.
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
    // TODO 2018-01-21 markus: Why not "> 1"?
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
   * Ignores run() methods, because they have a special return value handling.
   *
   * @param metaInfo Meta information about method call
   */
  protected final boolean isTailCall(MetaInfo metaInfo) {
    return metaInfo != null && metaInfo.tags.contains(TAG_TAIL_CALL) &&
      !isRun(clazz, method, classInfoCache);
  }

  //
  //
  //

  /**
   * Create return instruction for method.
   *
   * @param serializing Serializing flag. Null means the serializing flag is already on the stack.
   */
  private InsnList methodReturn(Boolean serializing) {
    InsnList result = new InsnList();
    if (isRun(clazz, method, classInfoCache)) {
      result.add(new InsnNode(RETURN));
    } else if (serializing != null) {
      result.add(new InsnNode(serializing? ICONST_1 : ICONST_0));
      result.add(new InsnNode(IRETURN));
    } else {
      // serializing flag is already on the stack.
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
