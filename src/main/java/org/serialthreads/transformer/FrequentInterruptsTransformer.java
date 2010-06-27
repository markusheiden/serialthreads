package org.serialthreads.transformer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.context.StackFrame;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ACC_TRANSIENT;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.serialthreads.transformer.code.MethodCode.dummyArguments;
import static org.serialthreads.transformer.code.MethodCode.dummyReturnStatement;
import static org.serialthreads.transformer.code.MethodCode.firstLocal;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.isInterface;
import static org.serialthreads.transformer.code.MethodCode.isNotStatic;
import static org.serialthreads.transformer.code.MethodCode.isNotVoid;
import static org.serialthreads.transformer.code.MethodCode.isRun;
import static org.serialthreads.transformer.code.MethodCode.isSelfCall;
import static org.serialthreads.transformer.code.MethodCode.methodName;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;

/**
 * Class adapter executing byte code enhancement of all methods.
 * The signature of all interruptible methods will not be changed.
 * This transformation needs a static thread holder, so SimpleSerialThreadManager has to be used.
 */
public class FrequentInterruptsTransformer extends AbstractTransformer
{
  public static final String STRATEGY = "FREQUENT";

  /**
   * Constructor.
   *
   * @param classInfoCache class cache to use
   */
  public FrequentInterruptsTransformer(IClassInfoCache classInfoCache)
  {
    super(classInfoCache, StackFrame.DEFAULT_FRAME_SIZE);
  }

  @Override
  public String toString()
  {
    return "Transformer " + STRATEGY;
  }

  @Override
  protected List<MethodNode> doTransformMethod(ClassNode clazz, MethodNode method) throws AnalyzerException
  {
    if (isAbstract(method))
    {
      // abstract methods need no transformation
      return Collections.emptyList();
    }

    Map<MethodInsnNode, Integer> methodCalls = interruptibleMethodCalls(method.instructions);
    if (methodCalls.isEmpty())
    {
      // no interruptible calls -> nothing to do
      return Collections.emptyList();
    }

    if (isRun(clazz, method, classInfoCache))
    {
      // take special care of run method
      return transformRun(clazz, method, methodCalls);
    }

    // "standard" transformation of interruptible methods
    return transformMethod(clazz, method, methodCalls);
  }

  /**
   * Transform IRunnable.run() method.
   *
   * @param clazz class to alter
   * @param method run method to transform
   * @param methodCalls all method calls in the method
   * @return transformed methods
   */
  private List<MethodNode> transformRun(ClassNode clazz, MethodNode method, Map<MethodInsnNode, Integer> methodCalls) throws AnalyzerException
  {
    LocalVariablesShifter.shift(firstLocal(method), 3, method);
    Frame[] frames = analyze(clazz, method);

    replaceReturns(clazz, method);
    List<InsnList> restoreCodes = insertCaptureCode(clazz, method, frames, methodCalls, true);
    createRestoreHandlerRun(clazz, method, restoreCodes);
    fixMaxs(method);

    return Arrays.asList(method);
  }

  /**
   * Transform interruptible method.
   *
   * @param clazz class to alter
   * @param method run method to transform
   * @param methodCalls all method calls in the method
   * @return transformed methods
   */
  private List<MethodNode> transformMethod(ClassNode clazz, MethodNode method, Map<MethodInsnNode, Integer> methodCalls) throws AnalyzerException
  {
    LocalVariablesShifter.shift(firstLocal(method), 3, method);
    Frame[] frames = analyze(clazz, method);

    List<InsnList> restoreCodes = insertCaptureCode(clazz, method, frames, methodCalls, false);
    createRestoreHandlerMethod(clazz, method, restoreCodes);
    fixMaxs(method);

    return Arrays.asList(method);
  }

  /**
   * Fix maxs of method.
   * These have not to be exact (but may not be too small!), because it is just for debugging purposes.
   *
   * @param method method to alter
   */
  protected void fixMaxs(MethodNode method)
  {
    method.maxLocals += 1;
    // TODO 2009-10-11 mh: recalculate minimum maxs
    method.maxStack = Math.max(method.maxStack + 2, 5);
  }

  @Override
  protected void afterTransformation(ClassNode clazz, List<MethodNode> constructors)
  {
    if (isInterface(clazz) || implementTransformedRunnable(clazz, constructors))
    {
      return;
    }

    if (log.isDebugEnabled())
    {
      log.debug("  Creating stack");
    }

    // TODO 2008-09-23 mh: just for classes which contain at least one not static, interruptible method?
    // TODO 2008-09-25 mh: make protected and do not create, when a subclass already has this field?
    clazz.fields.add(new FieldNode(ACC_PRIVATE + ACC_TRANSIENT + ACC_FINAL + ACC_SYNTHETIC, THREAD, THREAD_IMPL_DESC, THREAD_IMPL_DESC, null));
  }

  //
  // Capture code inserted after method calls
  //

  @Override
  protected void createCaptureCodeForMethod(ClassNode clazz, MethodNode method, Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner)
  {
    if (log.isDebugEnabled())
    {
      log.debug("      Creating capture code for method call to " + methodName(methodCall));
    }

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
    capture.add(pushToFrame(method, methodCall, frameAfter, localFrame));
    capture.add(pushMethodToFrame(method, position, containsMoreThanOneMethodCall, suppressOwner || isSelfCall(methodCall, frameBefore), localPreviousFrame, localFrame));
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
  protected InsnList createRestoreCodeForMethod(MethodNode method, Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter)
  {
    if (log.isDebugEnabled())
    {
      log.debug("      Creating restore code for method call to " + methodName(methodCall));
    }

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
    if (isSelfCall(methodCall, frameBefore))
    {
      restore.add(new VarInsnNode(ALOAD, 0));
    }
    else if (isNotStatic(clonedCall))
    {
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
    final boolean needToSaveReturnValue = isNotVoid(clonedCall) && frameAfter.getStackSize() > 1;
    if (needToSaveReturnValue)
    {
      restore.add(code(Type.getReturnType(clonedCall.desc)).store(localReturnValue));
    }
    restore.add(popFromFrame(method, clonedCall, frameAfter, localFrame));
    if (needToSaveReturnValue)
    {
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
  private MethodInsnNode copyMethodCall(MethodInsnNode methodCall)
  {
    return (MethodInsnNode) methodCall.clone(null);
  }

  /**
   * Insert frame restoring code at the begin of an interruptible method.
   *
   * @param clazz class to alter
   * @param method method to alter
   * @param restoreCodes restore codes for all method calls in the method
   */
  protected void createRestoreHandlerMethod(ClassNode clazz, MethodNode method, List<InsnList> restoreCodes)
  {
    assert !restoreCodes.isEmpty() : "Precondition: !restoreCodes.isEmpty()";

    if (log.isDebugEnabled())
    {
      log.debug("    Creating restore handler for method");
    }

    int local = firstLocal(method);
    final int localThread = local++;
    final int localPreviousFrame = local++;
    final int localFrame = local++;

    LabelNode normal = new LabelNode();

    // previousFrame = thread.frame;
    InsnList getFrame = new InsnList();
    getFrame.add(new VarInsnNode(ALOAD, localThread));
    getFrame.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));
    getFrame.add(new VarInsnNode(ASTORE, localPreviousFrame));

    InsnList restore = new InsnList();

    // frame = previousFrame.next;
    getFrame.add(new VarInsnNode(ALOAD, localPreviousFrame));
    getFrame.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    getFrame.add(new VarInsnNode(ASTORE, localFrame));

    // TODO 2009-11-26 mh: remove me?
    // thread.frame = frame;
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new VarInsnNode(ALOAD, localFrame));
    restore.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));

    // if not serializing "GOTO" normal
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    restore.add(new JumpInsnNode(IFEQ, normal));

    // else restore code dispatcher
    InsnList getMethod = new InsnList();
    getMethod.add(new VarInsnNode(ALOAD, localFrame));
    getMethod.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
    restore.add(restoreCodeDispatcher(getMethod, restoreCodes, 0));

    // insert label for normal body of method
    restore.add(normal);

    // insert generated byte code
    insertMethodGetThreadStartCode(clazz, method, localThread, getFrame, restore);
  }

  /**
   * Insert frame restoring code at the begin of the run() method.
   *
   * @param clazz class to alter
   * @param method method to alter
   * @param restoreCodes restore codes for all method calls in the method
   */
  protected void createRestoreHandlerRun(ClassNode clazz, MethodNode method, List<InsnList> restoreCodes)
  {
    assert !restoreCodes.isEmpty() : "Precondition: !restoreCodes.isEmpty()";

    if (log.isDebugEnabled())
    {
      log.debug("    Creating restore handler for run");
    }

    int local = firstLocal(method);
    final int localThread = local++;
    final int localPreviousFrame = local++;
    final int localFrame = local++;

    // dummy startup restore code to avoid to check thread.serializing.
    // empty frames are expected to have method = -1.
    InsnList startRestoreCode = new InsnList();
    // reset method to 0 for the case that there is just one normal restore code, because
    // if there is just one normal restore code, the method index will not be captured.
    // so we set the correct one (0) for this case.
    if (restoreCodes.size() <= 1)
    {
      startRestoreCode.add(new VarInsnNode(ALOAD, localFrame));
      startRestoreCode.add(new InsnNode(ICONST_0));
      startRestoreCode.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
    }
    // implicit goto to normal code, because this restore code will be put at the end of the restore code dispatcher
    restoreCodes.add(0, startRestoreCode);

    InsnList restore = new InsnList();

    // thread = this.$$thread$$;
    restore.add(new VarInsnNode(ALOAD, 0));
    restore.add(new FieldInsnNode(GETFIELD, clazz.name, THREAD, THREAD_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localThread));

    // frame = thread.first;
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "first", FRAME_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localFrame));

    // TODO 2009-11-26 mh: remove me?
    // thread.frame = frame;
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new VarInsnNode(ALOAD, localFrame));
    restore.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));

    // no previous frame needed in run, because there may not be a previous frame

    // restore code dispatcher
    InsnList getMethod = new InsnList();
    getMethod.add(new VarInsnNode(ALOAD, localFrame));
    getMethod.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
    restore.add(restoreCodeDispatcher(getMethod, restoreCodes, -1));

    method.instructions.insertBefore(method.instructions.getFirst(), restore);
  }
}
