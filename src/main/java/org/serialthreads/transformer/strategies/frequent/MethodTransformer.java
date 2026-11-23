package org.serialthreads.transformer.strategies.frequent;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.LocalVariablesShifter;
import org.serialthreads.transformer.strategies.AbstractMethodTransformer;
import org.serialthreads.transformer.strategies.MetaInfo;

import static org.objectweb.asm.Opcodes.IFEQ;
import static org.serialthreads.transformer.code.MethodCode.dummyArguments;
import static org.serialthreads.transformer.code.MethodCode.dummyReturnStatement;
import static org.serialthreads.transformer.code.MethodCode.firstLocal;
import static org.serialthreads.transformer.code.MethodCode.isNotVoid;
import static org.serialthreads.transformer.code.MethodCode.methodName;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_INTERRUPT;

/**
 * Base class for method transformers of {@link FrequentInterruptsTransformer}.
 */
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
     * Maximum size of a return value stored in the local holding the return value.
     */
    private int maxReturnValueSize = 0;

    /**
     * Constructor.
     *
     * @param clazz
     *         Class to transform.
     * @param method
     *         Method to transform.
     * @param classInfoCache
     *         Class cache to use.
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

    //
    // Capture and restore code inserted after method calls.
    //

    @Override
    protected LabelNode createCaptureAndRestoreCode(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean restore) {
        if (metaInfo.tags.contains(TAG_INTERRUPT)) {
            return createCaptureAndRestoreCodeForInterrupt(methodCall, metaInfo, position, restore);
        } else {
            return createCaptureAndRestoreCodeForMethod(methodCall, metaInfo, position, restore);
        }
    }

    /**
     * Insert frame capturing and restore code for interrupts.
     *
     * @param methodCall
     *         Method call to generate capturing code for.
     * @param metaInfo
     *         Meta information about method call.
     * @param position
     *         Position of method call in method.
     * @param restore
     *         Generate restore code too?.
     * @return Label to restore code, or null, if no restore code has been generated.
     */
    protected LabelNode createCaptureAndRestoreCodeForInterrupt(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean restore) {
        logger.debug("      Creating capture code for interrupt");

        var instructions = new InsnList();

        // Remove the owner of the replaced interrupt method call, if any.
        instructions.add(popOwner(methodCall));
        // Capture frame and return early.
        instructions.add(threadCode.captureFrame(method, methodCall, metaInfo, localFrame));
        // frame.method = position;
        instructions.add(setMethod(localFrame, position));
        // previousFrame.owner = this;
        instructions.add(setOwner(localPreviousFrame));
        // thread.serializing = true;
        instructions.add(threadCode.setSerializing(localThread, true));
        // return;
        instructions.add(dummyReturnStatement(method));

        // Restore code to continue.
        var restoreLabel = new LabelNode();
        if (restore) {
            instructions.add(restoreLabel);

            // Stop deserializing.
            instructions.add(threadCode.setSerializing(localThread, false));
            // Restore frame.
            instructions.add(threadCode.restoreFrame(method, methodCall, metaInfo, localFrame));
            // Continue.
        }

        // Replace dummy call of interrupt method by capture and restore code.
        replace(methodCall, instructions);

        return restoreLabel;
    }

    /**
     * Insert frame capturing and restore code after method calls.
     *
     * @param methodCall
     *         Method call to generate capturing code for.
     * @param metaInfo
     *         Meta information about method call.
     * @param position
     *         Position of method call in method.
     * @param restore
     *         Generate restore code too?.
     * @return Label to restore code, or null, if no restore code has been generated.
     */
    protected LabelNode createCaptureAndRestoreCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean restore) {
        logger.debug("      Creating capture code for method call to {}", methodName(methodCall));

        var normal = new LabelNode();

        var instructions = new InsnList();

        // if (!thread.serializing) "GOTO" normal.
        instructions.add(threadCode.pushSerializing(localThread));
        instructions.add(new JumpInsnNode(IFEQ, normal));

        // Get rid of dummy return value of called method first.
        if (isNotVoid(methodCall)) {
            instructions.add(code(Type.getReturnType(methodCall.desc)).pop());
        }

        // Capture frame and return early.
        instructions.add(threadCode.captureFrame(method, methodCall, metaInfo, localFrame));
        // frame.method = position;
        instructions.add(setMethod(localFrame, position));
        // previousFrame.owner = this;
        instructions.add(setOwner(localPreviousFrame));
        // return;
        instructions.add(dummyReturnStatement(method));

        // Restore code to continue.
        var restoreLabel = new LabelNode();
        if (restore) {
            instructions.add(restoreLabel);

            var clonedCall = copyMethodCall(methodCall);

            // Introduce new local holding the return value.
            var localReturnValue = method.maxLocals;

            // Label "normal" points to the code directly after the method call.
            var restoreFrame = new LabelNode();

            // Call interrupted method.
            instructions.add(pushOwner(methodCall, metaInfo, localFrame));
            // Push arguments on stack and jump to method call.
            // TODO 2008-08-22 mh: Restore locals by passing them as arguments, if possible?
            instructions.add(dummyArguments(methodCall));
            instructions.add(clonedCall);

            // if (!thread.serializing) "GOTO" normal, but restore the frame first.
            instructions.add(threadCode.pushSerializing(localThread));
            instructions.add(new JumpInsnNode(IFEQ, restoreFrame));

            // Early return, the frame already has been captured.
            instructions.add(dummyReturnStatement(method));

            // Restore frame to be able to resume normal execution of method.
            instructions.add(restoreFrame);

            // TODO 2009-11-26 mh: Remove me?
            // Set the current frame, because the next called method will need it.
            // thread.frame = frame;
            instructions.add(threadCode.setFrame(localThread, localFrame));

            // Restore stack "under" the returned value, if any.
            // TODO 2009-10-17 mh: Avoid restore, if method returns directly after returning from called method???
            var returnType = Type.getReturnType(clonedCall.desc);
            var needToSaveReturnValue = isNotVoid(clonedCall) && metaInfo.frameAfter.getStackSize() > 1;
            if (needToSaveReturnValue) {
                instructions.add(code(returnType).store(localReturnValue));
                maxReturnValueSize = Math.max(maxReturnValueSize, returnType.getSize());
            }
            instructions.add(threadCode.restoreFrame(method, clonedCall, metaInfo, localFrame));
            if (needToSaveReturnValue) {
                instructions.add(code(returnType).load(localReturnValue));
            }
        }

        // Normal execution.
        instructions.add(normal);

        // TODO 2009-11-26 mh: Remove me?
        // thread.frame = frame;
        instructions.add(threadCode.setFrame(localThread, localFrame));

        // Insert capture code.
        method.instructions.insert(methodCall, instructions);

        return restoreLabel;
    }

    /**
     * Copies method call.
     *
     * @param methodCall
     *         Method call.
     */
    private MethodInsnNode copyMethodCall(MethodInsnNode methodCall) {
        return (MethodInsnNode) methodCall.clone(null);
    }

    //
    //
    //

    /**
     * Fix maxs of method.
     * These do not have to be exact (but may not be too small!), because it is just for debugging purposes.
     */
    protected void fixMaxs() {
        // Reserve space for the local holding the return value, if needed.
        method.maxLocals += maxReturnValueSize;
        // TODO 2009-10-11 mh: Recalculate minimum maxs.
        method.maxStack = Math.max(method.maxStack + 2, 5);
    }
}
