package org.serialthreads.transfomer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.transfomer.context.infrequent.DynamicContext;
import org.serialthreads.transformer.interruptable.IIsInterruptableCache;
import org.serialthreads.transformer.strategies.AbstractTransformer;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.MethodCode.*;

/**
 * Class adapter executing byte code enhancement of all methods.
 * <p/>
 * TODO 2008-08-29 mh: Add line numbers to generated code?
 * TODO 2008-08-29 mh: Name local variables in generated code?
 * TODO 2008-08-29 mh: Need to fix longs and doubles (size==2)?
 * TODO 2008-08-30 mh: Check all Type#getSort() to handle boolean, char, byte, short too
 */
public class InfrequentInterruptsTransformer extends AbstractTransformer {
  public static final String STRATEGY = "INFREQUENT";

  protected static final String THREAD_IMPL_NAME = Type.getType(DynamicContext.class).getInternalName();
  protected static final String THREAD_IMPL_DESC = Type.getType(DynamicContext.class).getDescriptor();

  /**
   * Constructor.
   *
   * @param isInterruptableCache class cache to use
   */
  public InfrequentInterruptsTransformer(IIsInterruptableCache isInterruptableCache) {
    super(DynamicContext.class, DynamicContext.class, isInterruptableCache);
  }

  @Override
  public String toString() {
    return "Transformer " + STRATEGY;
  }

  @Override
  protected void afterTransformation(ClassNode clazz, List<MethodNode> constructors) {
    if (isInterface(clazz) || implementTransformedRunnable(clazz, constructors)) {
      return;
    }

    logger.debug("  Creating context");

    // TODO 2008-09-23 mh: just for classes which contain at least one not static, interruptable method?
    // TODO 2008-09-25 mh: make protected and do not create, when a subclass already has this field?
    clazz.fields.add(new FieldNode(ACC_PRIVATE + ACC_TRANSIENT + ACC_FINAL + ACC_SYNTHETIC, THREAD, THREAD_IMPL_DESC, THREAD_IMPL_DESC, null));
  }

  @Override
  protected List<MethodNode> doTransformMethod(ClassNode clazz, MethodNode method) throws AnalyzerException {
    Map<MethodInsnNode, Integer> methodCalls = interruptableMethodCalls(method.instructions);
    if (isAbstract(method) || methodCalls.isEmpty()) {
      return Collections.emptyList();
    }

    Frame[] frames = analyze(clazz, method);

    List<InsnList> restoreCodes = insertCaptureCode(clazz, method, frames, methodCalls);
    createRestoreCode(clazz, method, restoreCodes);
    fixMaxs(method);

    return Arrays.asList(method);
  }

  /**
   * Fix maxs of method.
   * These have not to be exact (but may not be too small!), because it is just for debugging purposes.
   *
   * @param method method to alter
   */
  protected void fixMaxs(MethodNode method) {
    method.maxLocals++;
    // 2 extra stack elements + 1 extra element due to possible dup2 to optimization overhead
    method.maxStack = Math.max(method.maxStack + 2 + 1, 5);
  }

  @Override
  protected void createCaptureCodeForInterrupt(ClassNode clazz, MethodNode method, Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter, int position, boolean containsMoreThanOneMethodCall) {
    logger.debug("      Creating capture code for interrupt");

    final int localThread = method.maxLocals;

    LabelNode normal = new LabelNode();

    InsnList capture = new InsnList();

    //
    // capture code and stack / locals restore code
    //

    // start capturing now
    // $$thread$$.serializing = true;
    capture.add(new VarInsnNode(ALOAD, localThread));
    capture.add(new InsnNode(ICONST_1));
    capture.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "serializing", "Z"));

    // capture frame and return early
    capture.add(pushToFrame(method, methodCall, frameAfter, localThread));
    capture.add(pushMethodToFrame(clazz, method, position, containsMoreThanOneMethodCall, "pushMethod", localThread));
    capture.add(dummyReturnStatement(method));

    // normal execution
    capture.add(normal);

    // insert capture code and remove interrupt call if any
    method.instructions.insert(methodCall, capture);
    method.instructions.remove(methodCall);
  }

  @Override
  protected void createCaptureCodeForMethod(ClassNode clazz, MethodNode method, Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter, int position, boolean containsMoreThanOneMethodCall) {
    logger.debug("      Creating capture code for method call");

    final int localThread = method.maxLocals;

    LabelNode normal = new LabelNode();

    InsnList capture = new InsnList();

    //
    // capture code and stack / locals restore code
    //

    // if ($$thread$$.serializing), else "GOTO" normal
    capture.add(new VarInsnNode(ALOAD, localThread));
    capture.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    capture.add(new JumpInsnNode(IFEQ, normal));

    // capture frame and return early
    capture.add(pushToFrame(method, methodCall, frameAfter, localThread));
    capture.add(pushMethodToFrame(clazz, method, position, containsMoreThanOneMethodCall, "pushMethod", localThread));
    capture.add(dummyReturnStatement(method));

    // normal execution
    capture.add(normal);

    // insert capture code and remove interrupt call if any
    method.instructions.insert(methodCall, capture);
  }

  @Override
  protected InsnList createRestoreCodeForInterrupt(MethodNode method, MethodInsnNode methodCall, Frame frame) {
    logger.debug("      Creating restore code for interrupt");

    final int localThread = method.maxLocals;

    LabelNode normal = new LabelNode();
    method.instructions.insert(methodCall, normal);

    InsnList restoreCode = new InsnList();

    restoreCode.add(popFromFrame(method, methodCall, frame, localThread));

    // stop restore
    restoreCode.add(new VarInsnNode(ALOAD, localThread));
    restoreCode.add(new InsnNode(ICONST_0));
    restoreCode.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    restoreCode.add(new JumpInsnNode(GOTO, normal));

    return restoreCode;
  }

  @Override
  protected InsnList createRestoreCodeForMethod(MethodNode method, Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter) {
    logger.debug("      Creating restore code for method call");

    final boolean isCallNotStatic = isNotStatic(methodCall);

    final int localThread = method.maxLocals;

    LabelNode normal = new LabelNode();
    method.instructions.insertBefore(methodCall, normal);

    InsnList restoreCode = popFromFrame(method, methodCall, frameAfter, localThread);

    // call interrupted method
    if (isCallNotStatic) {
      // pop owner
      restoreCode.add(new VarInsnNode(ALOAD, localThread));
      restoreCode.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, "popOwner", "()" + OBJECT_DESC));
      restoreCode.add(new TypeInsnNode(CHECKCAST, methodCall.owner));
    }

    // push arguments on stack and jump to method call
    // TODO 2008-08-22 mh: restore locals by passing them as arguments, if possible?
    restoreCode.add(dummyArguments(methodCall));
    // TODO 2009-11-21 mh: clone call, optimize
    restoreCode.add(new JumpInsnNode(GOTO, normal));

    return restoreCode;
  }

  /**
   * Insert frame restoring code at the begin of a method.
   *
   * @param clazz class to alter
   * @param method method to alter
   * @param restoreCodes restore codes for all method calls in the method
   */
  protected void createRestoreCode(ClassNode clazz, MethodNode method, List<InsnList> restoreCodes) {
    Assert.isTrue(!restoreCodes.isEmpty(), "Precondition: !restoreCodes.isEmpty()");

    logger.debug("    Creating retore code for method");

    final int localThread = method.maxLocals;

    LabelNode normal = new LabelNode();

    InsnList isSerializing = new InsnList();
    isSerializing.add(new VarInsnNode(ALOAD, localThread));
    isSerializing.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    isSerializing.add(new JumpInsnNode(IFEQ, normal));

    InsnList instructions = new InsnList();

    InsnList getMethod = new InsnList();
    getMethod.add(new VarInsnNode(ALOAD, localThread));
    getMethod.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, "popMethod", "()I"));
    instructions.add(restoreCodeDispatcher(getMethod, restoreCodes, 0));

    // insert label for normal body of method
    instructions.add(normal);

    // insert generated byte code
    insertMethodGetThreadStartCode(clazz, method, localThread, isSerializing, instructions);
  }
}
