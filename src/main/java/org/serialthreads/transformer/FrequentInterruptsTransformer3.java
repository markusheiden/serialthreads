package org.serialthreads.transformer;

import org.objectweb.asm.Type;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.serialthreads.transformer.code.MethodCode.dummyReturnStatement;
import static org.serialthreads.transformer.code.MethodCode.firstLocal;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.isInterface;
import static org.serialthreads.transformer.code.MethodCode.isInterrupt;
import static org.serialthreads.transformer.code.MethodCode.isNotStatic;
import static org.serialthreads.transformer.code.MethodCode.isNotVoid;
import static org.serialthreads.transformer.code.MethodCode.isRun;
import static org.serialthreads.transformer.code.MethodCode.isSelfCall;
import static org.serialthreads.transformer.code.MethodCode.methodName;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;

/**
 * Class adapter executing byte code enhancement of all methods.
 * For efficiency all interruptible methods will be copied with a reduced signature.
 * The thread and frame will be added to the signature of all interruptible methods.
 * This transformation needs no static thread holder, SimpleSerialThreadManager2 can be used.
 */
public class FrequentInterruptsTransformer3 extends AbstractTransformer
{
  public static final String STRATEGY = "FREQUENT3";

  /**
   * Constructor.
   *
   * @param classInfoCache class cache to use
   */
  public FrequentInterruptsTransformer3(IClassInfoCache classInfoCache)
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
    if (isInterface(clazz) && isRun(clazz, method, classInfoCache))
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
    // create copy of method with shortened arguments
    MethodNode copy = copyMethod(clazz, method);
    copy.desc = changeCopyDesc(method.desc);

    // add thread and previousFrame arguments to original method
    if (!isRun(clazz, method, classInfoCache))
    {
      method.desc = changeDesc(method.desc);
    }

    return Arrays.asList(copy);
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
    LocalVariablesShifter.shift(firstLocal(method), 3, method);
    Frame[] frames = analyze(clazz, method);

    // create copy of method with shortened signature
    MethodNode copy = copyMethod(clazz, method);
    Map<MethodInsnNode, Integer> copyMethodCalls = interruptibleMethodCalls(copy.instructions);
    List<InsnList> restoreCodes = insertCaptureCode(clazz, copy, frames, copyMethodCalls, true);
    createRestoreHandlerCopy(clazz, copy, restoreCodes);
    addThreadAndFrame(clazz, copy, copyMethodCalls.keySet());
    copy.desc = changeCopyDesc(method.desc);
    fixMaxs(copy);

    insertCaptureCode(clazz, method, frames, methodCalls, false);
    createRestoreHandlerMethod(clazz, method);
    addThreadAndFrame(clazz, method, methodCalls.keySet());
    method.desc = changeDesc(method.desc);
    fixMaxs(method);

    return Arrays.asList(method, copy);
  }

  /**
   * Copies a method, changes its signature and adds it to the class.
   *
   * @param clazz class to add copied method to
   * @param method method to copy
   */
  private MethodNode copyMethod(ClassNode clazz, MethodNode method)
  {
    MethodNode copy = MethodNodeCopier.copy(method);
    copy.name = changeCopyName(method.name, method.desc);

    //noinspection unchecked
    clazz.methods.add(copy);

    if (log.isDebugEnabled())
    {
      String kind = isAbstract(method) ? "abstract" : "concrete";
      log.debug("      Copied " + kind + " method " + methodName(clazz, copy));
    }

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
   * Fix maxs of method.
   * These have not to be exact (but may not be too small!), because it is just for debugging purposes.
   *
   * @param method method to alter
   */
  protected void fixMaxs(MethodNode method)
  {
    method.maxLocals += 2;
    // TODO 2009-10-11 mh: recalculate minimum maxs
    method.maxStack = Math.max(method.maxStack + 2, 5);
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
    capture.add(dummyReturnStatement(method));

    // normal execution
    capture.add(normal);

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
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
    final int localFrame = local++;
    final int localReturnValue = method.maxLocals + 1;

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
    restore.add(dummyReturnStatement(method));

    // restore frame to be able to resume normal execution of method
    restore.add(restoreFrame);

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
   * Change parameters of a copied method.
   *
   * @param desc parameters
   * @return changed parameters
   */
  private String changeCopyDesc(String desc)
  {
    return "(" + THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")" + Type.getReturnType(desc);
  }

  /**
   * Change parameters of a method.
   *
   * @param desc parameters
   * @return changed parameters
   */
  private String changeDesc(String desc)
  {
    return desc.replace(")", THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")");
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

    // frame = previousFrame.next
    InsnList getFrame = new InsnList();
    getFrame.add(new VarInsnNode(ALOAD, localPreviousFrame));
    getFrame.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    getFrame.add(new VarInsnNode(ASTORE, localFrame));

    // TODO 2009-12-01 mh: fix / reenable dynamic frame resize
    // getFrame.add(new VarInsnNode(ALOAD, localFrame));
    // getFrame.add(IntValueCode.push(255));
    // getFrame.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FRAME_IMPL_NAME, "resize", "(I)V"));

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

    int param = isNotStatic(method) ? 1 : 0;
    final int paramThread = param++;
    final int paramPreviousFrame = param++;

    int local = firstLocal(method);
    final int localThread = local++;
    final int localPreviousFrame = local++;
    final int localFrame = local++;

    InsnList restore = new InsnList();

    // previousFrame needs not to be stored in local, because the owner has already been stored in the previous frame

    // frame = previousFrame.next
    restore.add(new VarInsnNode(ALOAD, paramPreviousFrame));
    restore.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localFrame));

    // thread = currentThread;
    // TODO 2009-10-22 mh: rename locals to avoid this copy?
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
