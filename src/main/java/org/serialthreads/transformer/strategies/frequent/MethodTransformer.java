package org.serialthreads.transformer.strategies.frequent;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.AbstractMethodTransformer;
import org.serialthreads.transformer.strategies.MetaInfo;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.MethodCode.*;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;

/**
 * Base class for method transformers of {@link org.serialthreads.transformer.strategies.frequent3.FrequentInterruptsTransformer3}.
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

  //
  // Capture code inserted after method calls
  //

  @Override
  protected void createCaptureCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner) {
    logger.debug("      Creating capture code for method call to {}", methodName(methodCall));

    int local = firstLocal(method);
    final int localThread = local++;
    final int localPreviousFrame = local++;
    final int localFrame = local++;

    LabelNode normal = new LabelNode();

    InsnList capture = new InsnList();

    // if not serializing "GOTO" normal
    capture.add(new VarInsnNode(ALOAD, localThread));
    capture.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    capture.add(new JumpInsnNode(IFEQ, normal));

    // capture frame and return early
    capture.add(pushToFrame(methodCall, metaInfo, localFrame));
    capture.add(pushMethodToFrame(position, containsMoreThanOneMethodCall, suppressOwner || isSelfCall(methodCall, metaInfo.frameBefore), localPreviousFrame, localFrame));
    capture.add(dummyReturnStatement(method));

    // normal execution
    capture.add(normal);

    // TODO 2009-11-26 mh: remove me?
    // thread.frame = frame;
    capture.add(new VarInsnNode(ALOAD, localThread));
    capture.add(new VarInsnNode(ALOAD, localFrame));
    capture.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));

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

    int local = firstLocal(method);
    final int localThread = local++;
    final int localPreviousFrame = local++;
    final int localFrame = local++;
    final int localReturnValue = method.maxLocals;

    // label "normal" points the code directly after the method call
    LabelNode normal = new LabelNode();
    method.instructions.insert(methodCall, normal);
    LabelNode restoreFrame = new LabelNode();

    InsnList restore = new InsnList();

    // call interrupted method
    if (isSelfCall(methodCall, metaInfo.frameBefore)) {
      restore.add(new VarInsnNode(ALOAD, 0));
    } else if (isNotStatic(clonedCall)) {
      // get owner
      restore.add(new VarInsnNode(ALOAD, localFrame));
      restore.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
      restore.add(new TypeInsnNode(CHECKCAST, clonedCall.owner));
    }

    // push arguments on stack and jump to method call
    // TODO 2008-08-22 mh: restore locals by passing them as arguments, if possible?
    restore.add(dummyArguments(methodCall));
    restore.add(clonedCall);

    // if not serializing "GOTO" normal, but restore the frame first
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    restore.add(new JumpInsnNode(IFEQ, restoreFrame));

    // early return, the frame already has been captured
    restore.add(dummyReturnStatement(method));

    // restore frame to be able to resume normal execution of method
    restore.add(restoreFrame);

    // TODO 2009-11-26 mh: remove me?
    // set the current frame, because the next called method will need it
    // thread.frame = frame
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new VarInsnNode(ALOAD, localFrame));
    restore.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));

    // restore stack "under" the returned value, if any
    // TODO 2009-10-17 mh: avoid restore, if method returns directly after returning from called method???
    final boolean needToSaveReturnValue = isNotVoid(clonedCall) && metaInfo.frameAfter.getStackSize() > 1;
    if (needToSaveReturnValue) {
      restore.add(code(Type.getReturnType(clonedCall.desc)).store(localReturnValue));
    }
    restore.add(popFromFrame(clonedCall, metaInfo, localFrame));
    if (needToSaveReturnValue) {
      restore.add(code(Type.getReturnType(clonedCall.desc)).load(localReturnValue));
    }
    restore.add(new JumpInsnNode(GOTO, normal));

    return restore;
  }

  /**
   * Copies method call.
   *
   * @param methodCall method call
   */
  private MethodInsnNode copyMethodCall(MethodInsnNode methodCall) {
    return (MethodInsnNode) methodCall.clone(null);
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
