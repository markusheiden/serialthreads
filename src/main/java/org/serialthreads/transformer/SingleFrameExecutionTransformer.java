package org.serialthreads.transformer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
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
import org.serialthreads.transformer.code.MethodNodeCopier;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.serialthreads.transformer.code.MethodCode.firstLocal;
import static org.serialthreads.transformer.code.MethodCode.firstParam;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.isInterface;
import static org.serialthreads.transformer.code.MethodCode.isInterrupt;
import static org.serialthreads.transformer.code.MethodCode.isNotStatic;
import static org.serialthreads.transformer.code.MethodCode.isNotVoid;
import static org.serialthreads.transformer.code.MethodCode.isRun;
import static org.serialthreads.transformer.code.MethodCode.isSelfCall;
import static org.serialthreads.transformer.code.MethodCode.methodName;
import static org.serialthreads.transformer.code.MethodCode.returnInstructions;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;

/**
 * Transformer implementing serial threads by executing single frames.
 */
@SuppressWarnings({"UnusedAssignment", "UnusedParameters", "UnusedDeclaration"})
public class SingleFrameExecutionTransformer extends AbstractTransformer
{
  public static final String STRATEGY = "SINGLE_FRAME_EXECUTION";

  private static final String METHOD_HANDLE_NAME = Type.getType(MethodHandle.class).getInternalName();
  private static final String METHOD_HANDLE_DESC = Type.getType(MethodHandle.class).getDescriptor();
  private static final String LOOKUP_NAME = Type.getType(Lookup.class).getInternalName();
  private static final String LOOKUP_DESC = Type.getType(Lookup.class).getDescriptor();

  /**
   * Constructor.
   *
   * @param classInfoCache class cache to use
   */
  public SingleFrameExecutionTransformer(IClassInfoCache classInfoCache)
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
    if ((isInterface(clazz) || isAbstract(method)) && isRun(clazz, method, classInfoCache))
    {
      // do not transform IRunnable.run() itself
      return Collections.emptyList();
    }

    if (isAbstract(method))
    {
      // change signature of abstract methods
      return transformAbstract(clazz, method);
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
   * Transform abstract method (includes interface methods).
   *
   * @param clazz class to alter
   * @param method method to transform
   * @return transformed methods
   */
  private List<MethodNode> transformAbstract(ClassNode clazz, MethodNode method)
  {
    // create a copy of the method with shortened arguments
    MethodNode copy = copyMethod(clazz, method);
    copy.desc = changeCopyDesc(method.desc);

    if (log.isDebugEnabled())
    {
      log.debug("      Copied abstract method " + methodName(clazz, copy));
    }

    // add thread and previousFrame arguments to the original method
    method.desc = changeDesc(method.desc);

    return Arrays.asList(method, copy);
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
    addThreadAndFrame(clazz, method, methodCalls.keySet());
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
//    clazz.fields.add(new FieldNode(ASM4, ACC_PRIVATE, changeCopyName(method.name, method.desc), METHOD_HANDLE_DESC, METHOD_HANDLE_NAME, null));

    LocalVariablesShifter.shift(firstLocal(method), 3, method);
    Frame[] frames = analyze(clazz, method);

    // create copy of method with shortened signature
    MethodNode copy = copyMethod(clazz, method);
    voidReturns(clazz, copy);
    Map<MethodInsnNode, Integer> copyMethodCalls = interruptibleMethodCalls(copy.instructions);
    List<InsnList> restoreCodes = insertCaptureCode(clazz, copy, frames, copyMethodCalls, true);
    createRestoreHandlerCopy(clazz, copy, restoreCodes);
    addThreadAndFrame(clazz, copy, copyMethodCalls.keySet());
    copy.desc = changeCopyDesc(method.desc);
    fixMaxs(copy);

    if (log.isDebugEnabled())
    {
      log.debug("      Copied concrete method " + methodName(clazz, copy));
    }

    voidReturns(clazz, method);
    insertCaptureCode(clazz, method, frames, methodCalls, false);
    createRestoreHandlerMethod(clazz, method);
    addThreadAndFrame(clazz, method, methodCalls.keySet());
    method.desc = changeDesc(method.desc);
    fixMaxs(method);

    return Arrays.asList(method, copy);
  }

  /**
   * Copies a method and adds it to the class.
   * Its arguments will be shortened by the caller later on.
   *
   * @param clazz class to add copied method to
   * @param method method to copy
   */
  private MethodNode copyMethod(ClassNode clazz, MethodNode method)
  {
    MethodNode copy = MethodNodeCopier.copy(method);
    copy.name = changeCopyName(method.name, method.desc);

    clazz.methods.add(copy);

    return copy;
  }

  /**
   * Additionally push thread and previous frame arguments onto the stack for all interruptible method calls.
   * Alters the method descriptor to reflect these changes.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param methodCalls interruptible method calls
   */
  private void addThreadAndFrame(ClassNode clazz, MethodNode method, Set<MethodInsnNode> methodCalls)
  {
    InsnList instructions = method.instructions;

    int local = firstLocal(method);
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
    final int localFrame = local++;

    for (MethodInsnNode methodCall : methodCalls)
    {
      if (!isRun(methodCall, classInfoCache) && !isInterrupt(methodCall, classInfoCache))
      {
        instructions.insertBefore(methodCall, new VarInsnNode(ALOAD, localThread));
        instructions.insertBefore(methodCall, new VarInsnNode(ALOAD, localFrame));
        methodCall.desc = changeDesc(methodCall.desc);
      }
    }
  }

  /**
   * Replace returns with void returns.
   * The return value will be captured in the previous frame.
   *
   * @param clazz class to transform
   * @param method method to transform
   */
  private void voidReturns(ClassNode clazz, MethodNode method)
  {
    Type returnType = Type.getReturnType(method.desc);
    if (returnType.getSort() == Type.VOID)
    {
      // Method already has return type void
      return;
    }

    InsnList instructions = method.instructions;

    int local = firstLocal(method);
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
    final int localFrame = local++;

    for (AbstractInsnNode returnInstruction : returnInstructions(method))
    {
      instructions.insert(returnInstruction, code(returnType).pushReturnValue(localPreviousFrame));
      instructions.remove(returnInstruction);
    }
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

  //
  // Capture code inserted after method calls
  //

  @Override
  protected InsnList dummyReturn(MethodNode method)
  {
    // Always use void returns, because all methods have been change to use void returns
    InsnList result = new InsnList();
    result.add(new InsnNode(RETURN));
    return result;
  }

  @Override
  protected void createCaptureCodeForMethod(ClassNode clazz, MethodNode method, Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner)
  {
    if (log.isDebugEnabled())
    {
      log.debug("      Creating capture code for method call to " + methodName(methodCall));
    }

    int local = firstLocal(method);
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
    final int localFrame = local++;

    LabelNode normal = new LabelNode();

    InsnList capture = new InsnList();

    // if not serializing "GOTO" normal
    capture.add(new VarInsnNode(ALOAD, localThread));
    capture.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    capture.add(new JumpInsnNode(IFEQ, normal));

    // capture frame and return early
    capture.add(pushToFrame(method, methodCall, frameAfter, localFrame));
    capture.add(pushMethodToFrame(method, position, containsMoreThanOneMethodCall, suppressOwner, localPreviousFrame, localFrame));
    capture.add(new InsnNode(RETURN));

    // normal execution
    capture.add(normal);
    // restore return value of call, if any
    if (isNotVoid(methodCall))
    {
      capture.add(code(Type.getReturnType(methodCall.desc)).popReturnValue(localFrame));
    }

    // insert capture code
    method.instructions.insert(methodCall, capture);
  }

  @Override
  protected InsnList popReturnValue(MethodInsnNode methodCall)
  {
    // There is no return value, because all method have been change to void returns
    return new InsnList();
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
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
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
      // self call: owner == this
      restore.add(new VarInsnNode(ALOAD, 0));
    }
    else if (isNotStatic(clonedCall))
    {
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
    restore.add(popFromFrame(method, methodCall, frameAfter, localFrame));
    if (isNotVoid(methodCall))
    {
      restore.add(code(Type.getReturnType(methodCall.desc)).popReturnValue(localFrame));
    }
    restore.add(new JumpInsnNode(GOTO, normal));

    return restore;
  }

  /**
   * Copies method call and changes the signature.
   *
   * @param methodCall method call
   */
  private MethodInsnNode copyMethodCall(MethodInsnNode methodCall)
  {
    MethodInsnNode result = (MethodInsnNode) methodCall.clone(null);
    result.name = changeCopyName(methodCall.name, methodCall.desc);
    result.desc = changeCopyDesc(methodCall.desc);

    return result;
  }

  /**
   * Change the name of a copied method.
   * Computes an unique name based on the name and the descriptor.
   *
   * @param name name of method
   * @param desc parameters
   * @return changed name
   */
  private String changeCopyName(String name, String desc)
  {
    return name + "$$" + desc.replaceAll("[()\\[/;]", "_") + "$$";
  }

  /**
   * Change the parameters of a copied method to (Thread, Frame).
   *
   * @param desc parameters
   * @return changed parameters
   */
  private String changeCopyDesc(String desc)
  {
    return "(" + THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")" + Type.VOID_TYPE;
  }

  /**
   * Change parameters of a method.
   * Inserts thread and frame as additional parameters at the end.
   *
   * @param desc parameters
   * @return changed parameters
   */
  private String changeDesc(String desc)
  {
    int index = desc.indexOf(")");
    return desc.substring(0, index) + THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")" + Type.VOID_TYPE;
  }

  //
  // Restore handlers at start of method
  //

  /**
   * Insert frame restoring code at the begin of an interruptible method.
   *
   * @param clazz class to alter
   * @param method method to alter
   */
  protected void createRestoreHandlerMethod(ClassNode clazz, MethodNode method)
  {
    if (log.isDebugEnabled())
    {
      log.debug("    Creating restore handler for method");
    }

    int local = firstLocal(method);
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
    final int localFrame = local++;

    LabelNode normal = new LabelNode();

    // frame = previousFrame.next
    InsnList getFrame = new InsnList();
    getFrame.add(new VarInsnNode(ALOAD, localPreviousFrame));
    getFrame.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    getFrame.add(new InsnNode(DUP));
    getFrame.add(new JumpInsnNode(IFNONNULL, normal));

    getFrame.add(new InsnNode(POP));
    // frame = thread.addFrame(previousFrame);
    getFrame.add(new VarInsnNode(ALOAD, localThread));
    getFrame.add(new VarInsnNode(ALOAD, localPreviousFrame));
    getFrame.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, "addFrame", "(" + FRAME_IMPL_DESC + ")" + FRAME_IMPL_DESC));

    getFrame.add(normal);
    getFrame.add(new VarInsnNode(ASTORE, localFrame));

    method.instructions.insertBefore(method.instructions.getFirst(), getFrame);
  }

  /**
   * Insert frame restoring code at the begin of a copied method.
   *
   * @param clazz class to alter
   * @param method method to alter
   * @param restoreCodes restore codes for all method calls in the method
   */
  protected void createRestoreHandlerCopy(ClassNode clazz, MethodNode method, List<InsnList> restoreCodes)
  {
    assert !restoreCodes.isEmpty() : "Precondition: !restoreCodes.isEmpty()";

    if (log.isDebugEnabled())
    {
      log.debug("    Creating restore handler for copied method");
    }

    int param = firstParam(method);
    final int paramThread = param++;
    final int paramPreviousFrame = param++;

    int local = firstLocal(method);
    final int localThread = local++;
    final int localPreviousFrame = local++;
    final int localFrame = local++;

    InsnList restore = new InsnList();

    // TODO 2011-10-04 mh: Avoid this copy, it is needed just for capturing the return value. Fix order of copies?
    restore.add(new VarInsnNode(ALOAD, paramPreviousFrame));
    restore.add(new VarInsnNode(ASTORE, localPreviousFrame));

    // frame = previousFrame.next
    restore.add(new VarInsnNode(ALOAD, paramPreviousFrame));
    restore.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localFrame));

    // thread = currentThread;
    // TODO 2009-10-22 mh: how to avoid this copy???
    restore.add(new VarInsnNode(ALOAD, paramThread));
    restore.add(new VarInsnNode(ASTORE, localThread));

    // restore code dispatcher
    InsnList getMethod = new InsnList();
    getMethod.add(new VarInsnNode(ALOAD, localFrame));
    getMethod.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
    restore.add(restoreCodeDispatcher(getMethod, restoreCodes, 0));

    method.instructions.insertBefore(method.instructions.getFirst(), restore);
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
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
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

    // restore code dispatcher
    InsnList restore = new InsnList();

    // thread = this.$$thread$$;
    restore.add(new VarInsnNode(ALOAD, 0));
    restore.add(new FieldInsnNode(GETFIELD, clazz.name, THREAD, THREAD_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localThread));

    // frame = thread.first;
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "first", FRAME_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localFrame));

    // no previous frame needed in run, because there may not be a previous frame

    // restore code dispatcher
    InsnList getMethod = new InsnList();
    getMethod.add(new VarInsnNode(ALOAD, localFrame));
    getMethod.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
    restore.add(restoreCodeDispatcher(getMethod, restoreCodes, -1));

    method.instructions.insertBefore(method.instructions.getFirst(), restore);
  }
}
