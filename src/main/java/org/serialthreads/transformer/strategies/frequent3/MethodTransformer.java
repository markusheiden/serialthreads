package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.LocalVariablesShifter;
import org.serialthreads.transformer.strategies.AbstractMethodTransformer;
import org.serialthreads.transformer.strategies.MetaInfo;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.VOID;
import static org.serialthreads.transformer.code.MethodCode.*;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_INTERRUPT;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_TAIL_CALL;

/**
 * Base class for method transformers of {@link FrequentInterruptsTransformer3}.
 */
@SuppressWarnings({"UnusedAssignment", "UnusedDeclaration"})
abstract class MethodTransformer extends AbstractMethodTransformer {
  /**
   * Local holding the thread.
   */
  protected final int localThread;

  /**
   * Local holding the previous frame.
   */
  protected final int localPreviousFrame;

  /**
   * Local holding the current frame.
   */
  protected final int localFrame;

  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected MethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
    super(clazz, method, classInfoCache);

    this.localThread = local(0);
    this.localPreviousFrame = local(1);
    this.localFrame = local(2);
  }

  /**
   * Shift index of the locals to get place for the three needed new locals.
   * Local 0: thread, local 1: previous frame, local 2: current frame.
   */
  protected void shiftLocals() {
    LocalVariablesShifter.shift(firstLocal(method), 3, method);
  }

  /**
   * Add names for added locals.
   */
  protected void nameLocals() {
    nameLocal(localThread, THREAD_IMPL_DESC, "thread");
    nameLocal(localPreviousFrame, FRAME_IMPL_DESC, "previousFrame");
    nameLocal(localFrame, FRAME_IMPL_DESC, "frame");
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
    return "(" + THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")" + BOOLEAN_TYPE;
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
    return desc.substring(0, index) + THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")" + BOOLEAN_TYPE;
  }

  /**
   * Additionally push thread and previous frame arguments onto the stack for all interruptible method calls.
   * Alters the method descriptor to reflect these changes.
   */
  protected void addThreadAndFrame() {
    var instructions = method.instructions;

    for (var methodCall : interruptibleMethodCalls) {
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

    var returnType = Type.getReturnType(method.desc);
    for (var returnInstruction : returnInstructions(method)) {
      var previous = previousInstruction(returnInstruction);
      var replacement = new InsnList();
      if (isTailCall(previous)) {
        // Tail call optimization:
        // The return value has already been saved into the thread by the capture code of the called method.
        replacement.add(methodReturn(false));
        logger.debug("        Optimized tail call to {}", methodName((MethodInsnNode) previous));
      } else {
        if (returnType.getSort() != VOID) {
          // Default case:
          // Save return value into the thread.
          replacement.add(code(returnType).pushReturnValue(localThread));
        }
        replacement.add(methodReturn(false));
      }

      replace(returnInstruction, replacement);
    }
  }

  //
  // Capture and restore code inserted after method calls.
  //

  @Override
  protected LabelNode createCaptureAndRestoreCode(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, boolean restore) {
    if (metaInfo.tags.contains(TAG_INTERRUPT)) {
      return createCaptureAndRestoreCodeForInterrupt(methodCall, metaInfo, position, restore);
    } else if (isTailCall(metaInfo)) {
      return createCaptureAndRestoreCodeForMethodTail(methodCall, metaInfo, position, restore);
    } else {
      return createCaptureAndRestoreCodeForMethod(methodCall, metaInfo, position, restore);
    }
  }

  /**
   * Insert frame capturing and restore code for interrupts.
   *
   * @param methodCall Method call to generate capturing code for.
   * @param metaInfo Meta information about method call.
   * @param position Position of method call in method.
   * @param restore Generate restore code too?.
   * @return Label to restore code, or null, if no restore code has been generated.
   */
  protected LabelNode createCaptureAndRestoreCodeForInterrupt(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean restore) {
    logger.debug("      Creating capture code for interrupt");

    var instructions = new InsnList();

    // Capture frame and return early.
    instructions.add(threadCode.captureFrame(methodCall, metaInfo, localFrame));
    // frame.method = position;
    instructions.add(setMethod(localFrame, position));
    // We are serializing.
    instructions.add(methodReturn(true));

    // Restore code to continue.
    var restoreLabel = new LabelNode();
    if (restore) {
      instructions.add(restoreLabel);

      // Restore frame.
      instructions.add(threadCode.restoreFrame(methodCall, metaInfo, localFrame));
      // Continue.
    }

    // Replace dummy call of interrupt method by capture and restore code.
    replace(methodCall, instructions);

    return restoreLabel;
  }

  /**
   * Insert frame capturing and restore code after method calls.
   *
   * @param methodCall Method call to generate capturing code for.
   * @param metaInfo Meta information about method call.
   * @param position Position of method call in method.
   * @param restore Generate restore code too?.
   * @return Label to restore code, or null, if no restore code has been generated.
   */
  protected LabelNode createCaptureAndRestoreCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean restore) {
    logger.debug("      Creating capture code for method call to {}", methodName(methodCall));

    var normal = new LabelNode();
    var serializing = new LabelNode();

    var instructions = new InsnList();

    // If not serializing "GOTO" normal.
    instructions.add(new JumpInsnNode(IFEQ, normal));

    // Capture frame and return early.
    instructions.add(threadCode.captureFrame(methodCall, metaInfo, localFrame));
    // frame.method = position;
    instructions.add(setMethod(localFrame, position));

    // We are already serializing.
    instructions.add(serializing);
    instructions.add(methodReturn(true));

    // Restore code to continue.
    var restoreLabel = new LabelNode();
    if (restore) {
      instructions.add(restoreLabel);

      // Call interrupted method.
      instructions.add(callCopyMethod(methodCall, metaInfo));
      // If serializing, return early, the frame already has been captured.
      instructions.add(new JumpInsnNode(IFNE, serializing));

      // Restore stack "under" the returned value, if any.
      instructions.add(threadCode.restoreFrame(methodCall, metaInfo, localFrame));
      // Continue.
    }

    // Normal execution.
    instructions.add(normal);
    // Restore return value of call, if any.
    if (isNotVoid(methodCall)) {
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
   * @param metaInfo Meta information about method call.
   * @param position Position of method call in method.
   * @param restore Generate restore code too?.
   * @return Label to restore code, or null, if no restore code has been generated.
   */
  private LabelNode createCaptureAndRestoreCodeForMethodTail(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean restore) {
    logger.debug("      Creating capture code for method tail call to {}", methodName(methodCall));

    var instructions = new InsnList();

    // Early exit for tail calls.
    // The return value needs not to be restored, because it has already been stored by the method call.

    // frame.method = position;
    instructions.add(setMethod(localFrame, position));
    // The serializing flag is already on the stack from the method call.
    // return serializing;
    instructions.add(methodReturn(null));

    // Restore code to continue.
    var restoreLabel = new LabelNode();
    if (restore) {
      instructions.add(restoreLabel);

      // Call interrupted method.
      instructions.add(callCopyMethod(methodCall, metaInfo));
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
   * Call copy method.
   *
   * @param methodCall Method call to call copy of.
   * @param metaInfo Meta information about method call.
   */
  private InsnList callCopyMethod(MethodInsnNode methodCall, MetaInfo metaInfo) {
    var callCopyMethod = (MethodInsnNode) methodCall.clone(null);
    callCopyMethod.name = changeCopyName(methodCall.name, methodCall.desc);
    callCopyMethod.desc = changeCopyDesc(methodCall.desc);

    var instructions = new InsnList();

    // Push owner onto stack.
    instructions.add(pushOwner(methodCall, metaInfo, localFrame));
    // Push thread and frame (as arguments) onto stack.
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    // Call interrupted method.
    instructions.add(callCopyMethod);

    return instructions;
  }

  /**
   * Does the method need a frame for storing its state?.
   * <p>
   * A frame is not needed, if following conditions apply:
   * - There is just one interruptible method call (-> no need to store the method index)
   * - The method call is a static or self call (-> no need to store the method owner)
   * - The method call is a tail call (-> needs no storing of locals and stack)
   * The third condition is somewhat suboptimal,
   * but for now the easiest way to implement the detection,
   * that neither the locals nor the stack is needed / used.
   */
  protected final boolean needsFrame() {
    if (interruptibleMethodCalls.isEmpty()) {
      return false;
    }
    if (interruptibleMethodCalls.size() > 1) {
      return true;
    }

    var methodCall = interruptibleMethodCalls.iterator().next();
    var metaInfo = metaInfos.get(methodCall);
    return
      !(isSelfCall(methodCall, metaInfo) || isStatic(methodCall)) ||
      !isTailCall(metaInfo);
  }

  /**
   * Check if the given instruction is a tail call.
   *
   * @param instruction Instruction
   */
  protected final boolean isTailCall(AbstractInsnNode instruction) {
    var metaInfo = metaInfos.get(instruction);
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
    var instructions = new InsnList();
    if (isRun(clazz, method, classInfoCache)) {
      instructions.add(new InsnNode(RETURN));
    } else if (serializing != null) {
      instructions.add(new InsnNode(serializing? ICONST_1 : ICONST_0));
      instructions.add(new InsnNode(IRETURN));
    } else {
      // serializing flag is already on the stack.
      instructions.add(new InsnNode(IRETURN));
    }
    return instructions;
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
