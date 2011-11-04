package org.serialthreads.transformer;

import org.apache.log4j.Logger;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.ITransformedRunnable;
import org.serialthreads.context.SerialThread;
import org.serialthreads.context.SerialThreadManager;
import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;
import org.serialthreads.context.ThreadFinishedException;
import org.serialthreads.transformer.analyzer.ExtendedAnalyzer;
import org.serialthreads.transformer.analyzer.ExtendedValue;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.IValueCode;
import org.serialthreads.transformer.code.IntValueCode;
import org.serialthreads.transformer.code.ValueCodeFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.IntValueCode.push;
import static org.serialthreads.transformer.code.MethodCode.dummyReturnStatement;
import static org.serialthreads.transformer.code.MethodCode.firstLocal;
import static org.serialthreads.transformer.code.MethodCode.isInterrupt;
import static org.serialthreads.transformer.code.MethodCode.isNotStatic;
import static org.serialthreads.transformer.code.MethodCode.isNotVoid;
import static org.serialthreads.transformer.code.MethodCode.isRun;
import static org.serialthreads.transformer.code.MethodCode.isSelfCall;
import static org.serialthreads.transformer.code.MethodCode.methodName;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;

/**
 * Base implementation of a transformer.
 */
@SuppressWarnings({"UnusedDeclaration", "UnusedAssignment", "UnnecessaryLocalVariable"})
public abstract class AbstractTransformer implements ITransformer
{
  protected final Logger log = Logger.getLogger(getClass());

  protected static final String OBJECT_NAME = Type.getType(Object.class).getInternalName();
  protected static final String OBJECT_DESC = Type.getType(Object.class).getDescriptor();
  protected static final String CLASS_NAME = Type.getType(Class.class).getInternalName();
  protected static final String CLASS_DESC = Type.getType(Class.class).getDescriptor();
  protected static final String STRING_DESC = Type.getType(String.class).getDescriptor();
  protected static final String NPE_NAME = Type.getType(NullPointerException.class).getInternalName();
  protected static final String MANAGER_NAME = Type.getType(SerialThreadManager.class).getInternalName();
  protected static final String IRUNNABLE_NAME = Type.getType(IRunnable.class).getInternalName();
  protected static final String ITRANSFORMED_RUNNABLE_NAME = Type.getType(ITransformedRunnable.class).getInternalName();
  protected static final String THREAD_NAME = Type.getType(SerialThread.class).getInternalName();
  protected static final String THREAD_DESC = Type.getType(SerialThread.class).getDescriptor();
  protected static final String THREAD = "$$thread$$";
  protected static final String THREAD_FINISHED_EXCEPTION_NAME = Type.getType(ThreadFinishedException.class).getInternalName();

  protected final String THREAD_IMPL_NAME = Type.getType(Stack.class).getInternalName();
  protected final String THREAD_IMPL_DESC = Type.getType(Stack.class).getDescriptor();
  protected final String FRAME_IMPL_NAME = Type.getType(StackFrame.class).getInternalName();
  protected final String FRAME_IMPL_DESC = Type.getType(StackFrame.class).getDescriptor();

  protected final int defaultFrameSize;
  protected final IClassInfoCache classInfoCache;

  /**
   * Constructor.
   *
   * @param classInfoCache class cache to use
   * @param defaultFrameSize default size of frames
   */
  protected AbstractTransformer(IClassInfoCache classInfoCache, int defaultFrameSize)
  {
    assert classInfoCache != null : "Precondition: classInfoCache != null";
    assert defaultFrameSize > 0 : "Precondition: defaultFrameSize > 0";

    this.classInfoCache = classInfoCache;
    this.defaultFrameSize = defaultFrameSize;
  }

  @Override
  public void transform(ClassNode clazz)
  {
    if (log.isDebugEnabled())
    {
      log.info("Transforming class " + clazz.name + " with " + toString());
    }

    // separate constructors and methods
    List<MethodNode> constructors = new ArrayList<>(clazz.methods.size());
    List<MethodNode> methods = new ArrayList<>(clazz.methods.size());
    for (MethodNode method : clazz.methods)
    {
      if (method.name.equals("<init>"))
      {
        constructors.add(method);
      }
      else
      {
        methods.add(method);
      }
    }

    List<MethodNode> allTransformedMethods = new ArrayList<>(methods.size() * 2);
    for (MethodNode method : methods)
    {
      List<MethodNode> transformedMethods = transformMethod(clazz, method);
      allTransformedMethods.addAll(transformedMethods);
      if (transformedMethods.isEmpty())
      {
        // method not transformed? -> check that it contains no calls of interruptible methods
        check(clazz, method);
      }

      if (log.isDebugEnabled())
      {
        // analyze methods again to be sure that they are correct
        reanalyzeMethods(clazz, transformedMethods);
      }
    }

    if (allTransformedMethods.isEmpty())
    {
      // Class needs no transformation, but it should be loaded with this class loader
      if (log.isDebugEnabled())
      {
        log.debug("  Class " + clazz.name + " needs no transformation");
      }
      throw new LoadUntransformedException(clazz.name);
    }

    afterTransformation(clazz, constructors);

    if (log.isDebugEnabled())
    {
      log.debug("Finished transforming of class " + clazz.name);
    }
  }

  /**
   * Reanalyze transformed methods to be sure, that they are correct.
   * Logs disassembled transformed byte code on errors.
   *
   * @param clazz owning class
   * @param methods transformed methods
   */
  private void reanalyzeMethods(ClassNode clazz, List<MethodNode> methods) throws NotTransformableException
  {
    for (MethodNode method : methods)
    {
      try
      {
        analyze(clazz, method);
      }
      catch (Exception e)
      {
        // disassemble erroneous method
        log.debug("Unable to analyze transformed method " + methodName(clazz, method) + ": " + e.getMessage());
        log.debug("Byte code:\n" + Debugger.debug(clazz.name, method));
        throw new NotTransformableException("Unable to analyze transformed method " + methodName(clazz, method) + ": " + e.getMessage(), e);
      }
    }
  }

  /**
   * Execute byte code transformation on interruptible method.
   *
   * @param clazz class to transform
   * @param method method node to transform
   * @return transformed methods
   */
  protected List<MethodNode> transformMethod(ClassNode clazz, MethodNode method)
  {
    if (log.isDebugEnabled())
    {
      log.debug("  Transforming method " + methodName(clazz, method));
    }

    if (isExecutor(clazz, method))
    {
      // TODO 2009-12-11 mh: check executor
      // bypass check()
      return Arrays.asList(method);
    }

    if (!classInfoCache.isInterruptible(clazz, method))
    {
      if (log.isDebugEnabled())
      {
        log.debug("    Not interruptible -> abort transformation of method");
      }
      return Collections.emptyList();
    }

    try
    {
      if (log.isDebugEnabled())
      {
        logDebug(method);
      }

      return doTransformMethod(clazz, method);
    }
    catch (AnalyzerException e)
    {
      // abort in case of analyzer errors
      throw new NotTransformableException("Unable to analyze transformed method " + methodName(clazz, method) + ": " + e.getMessage(), e);
    }
  }


  /**
   * Execute byte code transformation on interruptible method.
   *
   * @param clazz class to transform
   * @param method method node to transform
   * @return transformed methods
   */
  protected abstract List<MethodNode> doTransformMethod(ClassNode clazz, MethodNode method) throws AnalyzerException;

  /**
   * Analyze a method to compute frames.
   *
   * @param clazz owner of method
   * @param method method
   * @return frames
   * @exception AnalyzerException
   */
  protected Frame[] analyze(ClassNode clazz, MethodNode method) throws AnalyzerException
  {
    Type classType = Type.getObjectType(clazz.name);
    Type superClassType = Type.getObjectType(clazz.superName);
    List<Type> interfaceTypes = new ArrayList<>(clazz.interfaces.size());
    for (String interfaceName : clazz.interfaces)
    {
      interfaceTypes.add(Type.getObjectType(interfaceName));
    }
    boolean isInterface = (clazz.access & ACC_INTERFACE) != 0;

    return new ExtendedAnalyzer(classInfoCache, classType, superClassType, interfaceTypes, isInterface).analyze(clazz.name, method);
  }

  /**
   * Execute some final modifications after transformation.
   * This method will be only executed, if some methods of the clazz had been transformed.
   *
   * @param clazz class to alter
   * @param constructors all constructors of class
   */
  protected void afterTransformation(ClassNode clazz, List<MethodNode> constructors)
  {
    implementTransformedRunnable(clazz, constructors);
  }

  /**
   * Make IRunnables implement ITransformedRunnable.
   *
   * @param clazz class to transform
   * @param constructors constructors
   * @return whether ITransformedRunnable has been implemented
   */
  protected boolean implementTransformedRunnable(ClassNode clazz, List<MethodNode> constructors)
  {
    if (!classInfoCache.hasSuperClass(clazz.name, IRUNNABLE_NAME))
    {
      return false;
    }

    if (log.isDebugEnabled())
    {
      log.debug("  Implement ITransformedRunnable");
    }

    // make class implement ITransformedRunnable
    if (clazz.interfaces.contains(ITRANSFORMED_RUNNABLE_NAME))
    {
      throw new NotTransformableException("Custom classes may not not implement ITransformedRunnable. Implement IRunnable instead.");
    }

    clazz.interfaces.add(ITRANSFORMED_RUNNABLE_NAME);

    // add $$thread$$ field
    clazz.fields.add(new FieldNode(ACC_PRIVATE + ACC_FINAL + ACC_SYNTHETIC, THREAD, THREAD_IMPL_DESC, null, null));

    // init $$thread$$ fields in constructors
    for (MethodNode constructor : constructors)
    {
      transformConstructor(clazz, constructor);
    }

    // implement ITransformedRunnable.getThread()
    createGetThread(clazz);

    return true;
  }

  /**
   * Transform constructor of runnables.
   * Initializes $$thread$$ field.
   *
   * @param clazz class to alter
   * @param constructor method to transform
   */
  protected void transformConstructor(ClassNode clazz, MethodNode constructor)
  {
    assert constructor.name.equals("<init>") : "Precondition: constructor.name.equals(\"<init>\")";

    if (log.isDebugEnabled())
    {
      log.debug("    Transforming constructor " + methodName(clazz, constructor));
    }

    constructor.maxStack = Math.max(5, constructor.maxStack);

    for (AbstractInsnNode returnInstruction : returnInstructions(constructor.instructions))
    {
      // constructors may not return a value
      assert returnInstruction.getOpcode() == RETURN : "Check: returnInstruction.getOpcode() == RETURN";

      // init $$thread$$ field before returning from constructor
      InsnList instructions = new InsnList();
      instructions.add(new VarInsnNode(ALOAD, 0));
      instructions.add(new TypeInsnNode(NEW, THREAD_IMPL_NAME));
      instructions.add(new InsnNode(DUP));
      instructions.add(new VarInsnNode(ALOAD, 0));
      instructions.add(new MethodInsnNode(INVOKEVIRTUAL, OBJECT_NAME, "getClass", "()" + CLASS_DESC));
      instructions.add(new MethodInsnNode(INVOKEVIRTUAL, CLASS_NAME, "getSimpleName", "()" + STRING_DESC));
      // TODO 2009-12-01 mh: configurable default size
      instructions.add(IntValueCode.push(defaultFrameSize));
      instructions.add(new MethodInsnNode(INVOKESPECIAL, THREAD_IMPL_NAME, "<init>", "(" + STRING_DESC + "I)V"));
      instructions.add(new FieldInsnNode(PUTFIELD, clazz.name, THREAD, THREAD_IMPL_DESC));

      constructor.instructions.insertBefore(returnInstruction, instructions);
    }
  }

  /**
   * Implement ITransformedRunnable.getThread().
   *
   * @param clazz clazz to alter
   */
  private void createGetThread(ClassNode clazz)
  {
    MethodNode getThread = new MethodNode(ACC_PUBLIC, "getThread", "()" + THREAD_DESC, null, new String[0]);

    getThread.maxLocals = 1;
    getThread.maxStack = 1;

    InsnList instructions = getThread.instructions;
    instructions.add(new VarInsnNode(ALOAD, 0));
    instructions.add(new FieldInsnNode(GETFIELD, clazz.name, THREAD, THREAD_IMPL_DESC));
    instructions.add(new InsnNode(ARETURN));

    clazz.methods.add(getThread);
  }

  //
  //
  //

  /**
   * Extract all interruptible method calls.
   *
   * @param instructions instructions
   * @return map with method call -> Index of method call in instructions
   */
  protected Map<MethodInsnNode, Integer> interruptibleMethodCalls(InsnList instructions)
  {
    Map<MethodInsnNode, Integer> result = new LinkedHashMap<>();
    for (int i = 0; i < instructions.size(); i++)
    {
      AbstractInsnNode instruction = instructions.get(i);
      if (instruction instanceof MethodInsnNode)
      {
        MethodInsnNode methodCall = (MethodInsnNode) instruction;
        if (isInterruptible(methodCall))
        {
          result.put(methodCall, i);
        }
      }
    }

    return result;
  }

  /**
   * Extract return instructions.
   * <p/>
   * TODO 2009-11-20 mh: extract
   *
   * @param instructions instructions
   */
  protected List<AbstractInsnNode> returnInstructions(InsnList instructions)
  {
    List<AbstractInsnNode> result = new ArrayList<>();
    for (Iterator<AbstractInsnNode> iter = instructions.iterator(); iter.hasNext(); )
    {
      AbstractInsnNode instruction = iter.next();
      int opcode = instruction.getOpcode();
      if (opcode >= IRETURN && opcode <= RETURN)
      {
        result.add(instruction);
      }
    }

    return result;
  }

  //
  // capture code insertion
  //

  /**
   * Inserts capture code after method calls.
   *
   * @param clazz class containing method
   * @param method method
   * @param frames frames
   * @param methodCalls method calls inside method
   * @param suppressOwner suppress capturing of owner?
   * @return generated restore codes for method calls
   */
  protected List<InsnList> insertCaptureCode(ClassNode clazz, MethodNode method, Frame[] frames, Map<MethodInsnNode, Integer> methodCalls, boolean suppressOwner)
  {
    boolean moreThanOne = methodCalls.size() > 1;

    List<InsnList> restoreCodes = new ArrayList<>(methodCalls.size());
    int methodCallIndex = 0;
    for (Entry<MethodInsnNode, Integer> entry : methodCalls.entrySet())
    {
      Frame frameBefore = frames[entry.getValue()];
      MethodInsnNode methodCall = entry.getKey();
      Frame frameAfter = frames[entry.getValue() + 1];

      restoreCodes.add(createRestoreCode(method, frameBefore, methodCall, frameAfter));
      createCaptureCode(clazz, method, frameBefore, methodCall, frameAfter, methodCallIndex++, moreThanOne, suppressOwner);
    }

    return restoreCodes;
  }

  /**
   * Create method specific frame restore code.
   *
   * @param method method containing the call
   * @param frameBefore frame before method call
   * @param methodCall method call to generate restore code for
   * @param frameAfter frame after method call
   * @return restore code
   */
  protected InsnList createRestoreCode(MethodNode method, Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter)
  {
    return isInterrupt(methodCall, classInfoCache)?
      createRestoreCodeForInterrupt(method, methodCall, frameAfter) :
      createRestoreCodeForMethod(method, frameBefore, methodCall, frameAfter);
  }

  /**
   * Create restore code for ending an interrupt.
   *
   * @param method method containing the call
   * @param methodCall method call to generate capturing code for
   * @param frame frame after method call
   * @return restore code
   */
  protected InsnList createRestoreCodeForInterrupt(MethodNode method, MethodInsnNode methodCall, Frame frame)
  {
    if (log.isDebugEnabled())
    {
      log.debug("      Creating restore code for interrupt");
    }

    int local = firstLocal(method);
    final int localThread = local++;
    final int localPreviousFrame = local++;
    final int localFrame = local++;

    InsnList restore = new InsnList();

    LabelNode normal = new LabelNode();
    method.instructions.insert(methodCall, normal);

    // stop deserializing
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new InsnNode(ICONST_0));
    restore.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "serializing", "Z"));

    // restore frame
    // TODO 2009-10-17 mh: avoid restore, if method returns directly after interrupt?
    restore.add(popFromFrame(method, methodCall, frame, localFrame));

    // resume
    restore.add(new JumpInsnNode(GOTO, normal));

    return restore;
  }

  /**
   * Create method specific frame restore code.
   *
   * @param method method containing the call
   * @param frameBefore frame before method call
   * @param methodCall method call to generate restore code for
   * @param frameAfter frame after method call
   * @return restore code
   */
  protected abstract InsnList createRestoreCodeForMethod(MethodNode method, Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter);

  /**
   * Insert frame capturing code after a given method call.
   *
   * @param clazz containing method
   * @param method method containing the call
   * @param frameBefore frame before method call
   * @param methodCall method call to generate capturing code for
   * @param frameAfter frame after method call
   * @param position position of method call in method
   * @param containsMoreThanOneMethodCall does the method contain more than one method call at all?
   * @param suppressOwner suppress capturing of owner?
   */
  protected void createCaptureCode(ClassNode clazz, MethodNode method, Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner)
  {
    if (isInterrupt(methodCall, classInfoCache))
    {
      createCaptureCodeForInterrupt(clazz, method, frameBefore, methodCall, frameAfter, position, containsMoreThanOneMethodCall, suppressOwner);
    }
    else
    {
      createCaptureCodeForMethod(clazz, method, frameBefore, methodCall, frameAfter, position, containsMoreThanOneMethodCall, suppressOwner);
    }
  }

  /**
   * Insert frame capturing code when starting an interrupt.
   *
   * @param clazz class containing method
   * @param method method containing the call
   * @param frameBefore frame before method call
   * @param methodCall method call to generate capturing code for
   * @param frameAfter frame after method call
   * @param position position of method call in method
   * @param containsMoreThanOneMethodCall does the method contain more than one method call at all?
   * @param suppressOwner suppress capturing of owner?
   */
  protected void createCaptureCodeForInterrupt(ClassNode clazz, MethodNode method, Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner)
  {
    if (log.isDebugEnabled())
    {
      log.debug("      Creating capture code for interrupt");
    }

    int local = firstLocal(method);
    final int localThread = local++;
    final int localPreviousFrame = local++;
    final int localFrame = local++;

    InsnList capture = new InsnList();

    // capture frame
    // TODO 2009-10-17 mh: avoid capture, if method returns directly after interrupt?
    capture.add(pushToFrame(method, methodCall, frameAfter, localFrame));
    capture.add(pushMethodToFrame(method, position, containsMoreThanOneMethodCall, suppressOwner || isSelfCall(methodCall, frameBefore), localPreviousFrame, localFrame));

    // "start" serializing
    capture.add(new VarInsnNode(ALOAD, localThread));
    capture.add(new InsnNode(ICONST_1));
    capture.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "serializing", "Z"));

    // return early
    capture.add(dummyReturn(method));

    // replace dummy call of interrupt method by capture code
    method.instructions.insert(methodCall, capture);
    method.instructions.remove(methodCall);
  }

  /**
   * Dummy return for the given method.
   *
   * @param method method containing the call
   */
  protected InsnList dummyReturn(MethodNode method)
  {
    InsnList result = new InsnList();
    result.add(dummyReturnStatement(method));
    return result;
  }

  /**
   * Insert frame capturing code after returning from a method call.
   *
   * @param clazz class containing method
   * @param method method containing the call
   * @param frameBefore frame before method call
   * @param methodCall method call to generate capturing code for
   * @param frameAfter frame after method call
   * @param position position of method call in method
   * @param containsMoreThanOneMethodCall does the method contain more than one method call at all?
   * @param suppressOwner suppress capturing of owner?
   */
  protected abstract void createCaptureCodeForMethod(ClassNode clazz, MethodNode method, Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner);

  //
  // frame related code
  //

  /**
   * Save current frame after returning from a method call.
   *
   * @param method method to transform
   * @param methodCall method call to process
   * @param frame frame after method call
   * @param localFrame number of local containing the frame
   * @return generated capture code
   */
  protected InsnList pushToFrame(MethodNode method, MethodInsnNode methodCall, Frame frame, int localFrame)
  {
    InsnList push = new InsnList();

    final boolean isMethodNotStatic = isNotStatic(method);
    final boolean isCallNotVoid = isNotVoid(methodCall);

    // get rid of dummy return value of called method first
    if (isCallNotVoid)
    {
      push.add(popReturnValue(methodCall));
    }

    // save stack
    // the topmost element is a dummy return value, if the called method returns one
    int[] stackIndexes = stackIndexes(frame);
    for (int stack = isCallNotVoid? frame.getStackSize() - 2 : frame.getStackSize() - 1; stack >= 0; stack--)
    {
      ExtendedValue value = (ExtendedValue) frame.getStack(stack);
      if (value.isConstant() || value.isHoldInLocal())
      {
        // just pop the value from stack, because the stack value is constant or stored in a local too.
        push.add(code(value).pop());
      }
      else
      {
        push.add(code(value).pushStack(stackIndexes[stack], localFrame));
      }
    }

    // save locals separated by type
    for (IValueCode code : ValueCodeFactory.CODES)
    {
      List<Integer> pushLocals = new ArrayList<>(frame.getLocals());

      // do not store local 0 for non static methods, because it always contains "this"
      for (int local = isMethodNotStatic? 1 : 0, end = frame.getLocals() - 1; local <= end; local++)
      {
        BasicValue value = (BasicValue) frame.getLocal(local);
        if (code.isResponsibleFor(value.getType()))
        {
          ExtendedValue extendedValue = (ExtendedValue) value;
          if (!extendedValue.isHoldInLowerLocal(local))
          {
            pushLocals.add(local);
          }
        }
      }

      Iterator<Integer> iter = pushLocals.iterator();

      // for first locals use fast stack
      for (int i = 0; iter.hasNext() && i < StackFrame.FAST_FRAME_SIZE; i++)
      {
        int local = iter.next();
        IValueCode localCode = code((BasicValue) frame.getLocal(local));
        push.add(localCode.pushLocalVariableFast(local, i, localFrame));
      }

      // for too high locals use "slow" storage in (dynamic) array
      if (iter.hasNext())
      {
        push.add(code.getLocals(localFrame));
        for (int i = 0; iter.hasNext(); i++)
        {
          int local = iter.next();
          IValueCode localCode = code((BasicValue) frame.getLocal(local));
          if (iter.hasNext())
          {
            push.add(new InsnNode(DUP));
          }
          push.add(localCode.pushLocalVariable(local, i));
        }
      }
    }

    return push;
  }

  /**
   * Pop return value from stack before saving a frame.
   *
   * @param methodCall method call to process
   */
  protected InsnList popReturnValue(MethodInsnNode methodCall)
  {
    InsnList result = new InsnList();
    result.add(code(Type.getReturnType(methodCall.desc)).pop());
    return result;
  }

  /**
   * Push method and owner onto frame.
   *
   * @param method method to transform
   * @param position position of method call
   * @param containsMoreThanOneMethodCall contains the method more than one method call?
   * @param suppressOwner suppress saving the owner?
   * @param localPreviousFrame number of local containing the previous frame or -1 for retrieving it via current frame
   * @param localFrame number of local containing the current frame
   * @return generated capture code
   */
  protected InsnList pushMethodToFrame(MethodNode method, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner, int localPreviousFrame, int localFrame)
  {
    InsnList result = new InsnList();

    // save method index of this method
    if (containsMoreThanOneMethodCall)
    {
      // frame.method = position;
      result.add(new VarInsnNode(ALOAD, localFrame));
      result.add(push(position));
      result.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
    }

    // save owner of method call one level above
    if (isNotStatic(method) && !suppressOwner)
    {
      // previousFrame.owner = this;
      if (localPreviousFrame < 0)
      {
        result.add(new VarInsnNode(ALOAD, localFrame));
        result.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "previous", FRAME_IMPL_DESC));
      }
      else
      {
        result.add(new VarInsnNode(ALOAD, localPreviousFrame));
      }
      result.add(new VarInsnNode(ALOAD, 0));
      result.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
    }

    return result;
  }

  /**
   * Push method and owner onto frame with a given method.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param position position of method call
   * @param containsMoreThanOneMethodCall contains the method more than one method call?
   * @param methodName name of method to store owner and method
   * @param localThread number of local containing the thread
   * @return generated capture code
   */
  protected InsnList pushMethodToFrame(ClassNode clazz, MethodNode method, int position, boolean containsMoreThanOneMethodCall, String methodName, int localThread)
  {
    InsnList result = new InsnList();

    final boolean isMethodNotStatic = isNotStatic(method);

    // save method index of this method and owner of method call one level above
    boolean pushOwner = isMethodNotStatic && !isRun(clazz, method, classInfoCache);
    boolean pushMethod = containsMoreThanOneMethodCall;
    if (pushOwner && pushMethod)
    {
      // save owner of this method for calling method and index of interrupted method
      result.add(new VarInsnNode(ALOAD, localThread));
      result.add(new VarInsnNode(ALOAD, 0));
      result.add(push(position));
      result.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, methodName, "(" + OBJECT_DESC + "I)V"));
    }
    else if (pushOwner)
    {
      // save owner of this method for calling method
      result.add(new VarInsnNode(ALOAD, localThread));
      result.add(new VarInsnNode(ALOAD, 0));
      result.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, methodName, "(" + OBJECT_DESC + ")V"));
    }
    else if (pushMethod)
    {
      // save index of interrupted method
      result.add(new VarInsnNode(ALOAD, localThread));
      result.add(push(position));
      result.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, methodName, "(I)V"));
    }

    return result;
  }

  /**
   * Create restore code dispatcher.
   *
   * @param getMethod instruction to get method index onto the top of the stack
   * @param restoreCodes restore codes
   * @param startIndex first method index, should be -1 for run(), 0 otherwise
   * @return generated restore code
   */
  protected InsnList restoreCodeDispatcher(InsnList getMethod, List<InsnList> restoreCodes, int startIndex)
  {
    assert !restoreCodes.isEmpty() : "Precondition: !restoreCodes.isEmpty()";

    if (restoreCodes.size() == 1)
    {
      // just one method call -> nothing to dispatch -> execute directly
      return restoreCodes.get(0);
    }

    InsnList result = new InsnList();

    // Label to the specific restore code for every method call
    LabelNode[] labels = new LabelNode[restoreCodes.size()];
    for (int i = 0; i < labels.length; i++)
    {
      labels[i] = new LabelNode();
    }
    LabelNode defaultLabel = new LabelNode();

    // switch(currentFrame.method) // branch to specific restore code
    result.add(getMethod);
    result.add(new TableSwitchInsnNode(startIndex, startIndex + restoreCodes.size() - 1, defaultLabel, labels));

    // default case -> may not happen -> throw IllegalThreadStateException
    result.add(defaultLabel);
    result.add(new TypeInsnNode(NEW, "java/lang/IllegalThreadStateException"));
    result.add(new InsnNode(DUP));
    result.add(new LdcInsnNode("Invalid method pointer"));
    result.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/IllegalThreadStateException", "<init>", "(" + STRING_DESC + ")V"));
    result.add(new InsnNode(ATHROW));

    // reverse iteration to put first restore code at the end
    for (int i = labels.length - 1; i >= 0; i--)
    {
      result.add(labels[i]);
      result.add(restoreCodes.get(i));
    }

    return result;
  }

  /**
   * Restore current frame before resuming the method call
   *
   * @param method method to transform
   * @param methodCall method call to process
   * @param frame frame after method call
   * @param localFrame number of local containing the frame
   * @return generated restore code
   */
  protected InsnList popFromFrame(MethodNode method, MethodInsnNode methodCall, Frame frame, int localFrame)
  {
    InsnList result = new InsnList();

    final boolean isMethodNotStatic = isNotStatic(method);
    final boolean isCallNotVoid = isNotVoid(methodCall);

    // restore locals by type
    for (IValueCode code : ValueCodeFactory.CODES)
    {
      List<Integer> popLocals = new ArrayList<>();
      InsnList copyLocals = new InsnList();

      // do not restore local 0 for non static methods, because it always contains "this"
      for (int local = isMethodNotStatic? 1 : 0, end = frame.getLocals() - 1; local <= end; local++)
      {
        BasicValue value = (BasicValue) frame.getLocal(local);
        if (code.isResponsibleFor(value.getType()))
        {
          ExtendedValue extendedValue = (ExtendedValue) value;
          if (extendedValue.isHoldInLowerLocal(local))
          {
            // the value of the local is hold in a lower local too -> copy
            if (log.isDebugEnabled())
            {
              log.debug("        Detected codes with the same value: " + extendedValue.getLowestLocal() + "/" + local);
            }
            copyLocals.add(code(extendedValue).load(extendedValue.getLowestLocal()));
            copyLocals.add(code(extendedValue).store(local));
          }
          else
          {
            // normal case -> pop local from frame
            popLocals.add(local);
          }
        }
      }

      // first restore not duplicated locals, if any
      Iterator<Integer> iter = popLocals.iterator();

      // for first locals use fast stack
      for (int i = 0; iter.hasNext() && i < StackFrame.FAST_FRAME_SIZE; i++)
      {
        int local = iter.next();
        IValueCode localCode = code((BasicValue) frame.getLocal(local));
        result.add(localCode.popLocalVariableFast(local, i, localFrame));
      }

      // for too high locals use "slow" storage in (dynamic) array
      if (iter.hasNext())
      {
        result.add(code.getLocals(localFrame));
        for (int i = 0; iter.hasNext(); i++)
        {
          int local = iter.next();
          IValueCode localCode = code((BasicValue) frame.getLocal(local));
          if (iter.hasNext())
          {
            result.add(new InsnNode(DUP));
          }
          result.add(localCode.popLocalVariable(local, i));
        }
      }

      // then restore duplicated locals
      result.add(copyLocals);
    }

    // restore stack
    // the topmost element is a dummy return value, if the called method is not a void method
    int[] stackIndexes = stackIndexes(frame);
    for (int stack = 0, end = isCallNotVoid? frame.getStackSize() - 1 : frame.getStackSize(); stack < end; stack++)
    {
      ExtendedValue value = (ExtendedValue) frame.getStack(stack);
      if (value.isConstant())
      {
        // the stack value is constant -> push constant
        if (log.isDebugEnabled())
        {
          log.debug("        Detected constant value on stack: " + value.toString() + " / value " + value.getConstant());
        }
        result.add(code(value).push(value.getConstant()));
      }
      else if (value.isHoldInLocal())
      {
        // the stack value was already stored in local variable -> load local
        if (log.isDebugEnabled())
        {
          log.debug("        Detected value of local on stack: " + value.toString() + " / local " + value.getLowestLocal());
        }
        result.add(code(value).load(value.getLowestLocal()));
      }
      else
      {
        // normal case -> pop stack from frame
        result.add(code(value).popStack(stackIndexes[stack], localFrame));
      }
    }

    return result;
  }

  /**
   * Compute index of all stack elements in typed stack arrays.
   *
   * @param frame Frame
   * @return array stack element -> stack element index
   */
  private int[] stackIndexes(Frame frame)
  {
    int[] result = new int[frame.getStackSize()];
    Arrays.fill(result, -1);
    for (IValueCode code : ValueCodeFactory.CODES)
    {
      for (int stack = 0, end = frame.getStackSize(), i = 0; stack < end; stack++)
      {
        BasicValue value = (BasicValue) frame.getStack(stack);
        if (code.isResponsibleFor(value.getType()))
        {
          result[stack] = i++;
        }
      }
    }

    return result;
  }

  /**
   * Create "get thread" code including exception handling.
   * Inserts generated code directly into method.
   * Required stack size: 3.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param localThread number of local containing the thread
   * @param tryCode restore code which can cause a NullPointerException during access of this.$$thread$$
   * @param restoreCode remaining restore code, executed directly after tryCode
   */
  protected void insertMethodGetThreadStartCode(ClassNode clazz, MethodNode method, int localThread, InsnList tryCode, InsnList restoreCode)
  {
    final boolean isMethodNotStatic = isNotStatic(method);

    InsnList instructions = new InsnList();

    // store thread always in a new local variable
    if (isMethodNotStatic)
    {
      instructions.add(new VarInsnNode(ALOAD, 0));
      instructions.add(new FieldInsnNode(GETFIELD, clazz.name, THREAD, THREAD_IMPL_DESC));
    }
    else
    {
      instructions.add(new MethodInsnNode(INVOKESTATIC, MANAGER_NAME, "getThread", "()" + THREAD_DESC));
      instructions.add(new TypeInsnNode(CHECKCAST, THREAD_IMPL_NAME));
    }
    LabelNode retry = new LabelNode();
    instructions.add(retry);
    // store frame always in a new local variable
    instructions.add(new VarInsnNode(ASTORE, localThread));

    LabelNode beginTry = new LabelNode();
    instructions.add(beginTry);
    instructions.add(tryCode);
    LabelNode endTry = new LabelNode();
    instructions.add(endTry);
    instructions.add(restoreCode);

    method.instructions.insertBefore(method.instructions.getFirst(), instructions);

    if (isMethodNotStatic)
    {
      InsnList handler = new InsnList();

      // try / catch (NullPointerException) for thread access
      LabelNode catchNPE = new LabelNode();
      handler.add(catchNPE);
      // Pop NPE from stack
      handler.add(new InsnNode(POP));
      handler.add(new VarInsnNode(ALOAD, 0));
      handler.add(new MethodInsnNode(INVOKESTATIC, MANAGER_NAME, "getThread", "()" + THREAD_DESC));
      handler.add(new TypeInsnNode(CHECKCAST, THREAD_IMPL_NAME));
      handler.add(new InsnNode(DUP_X1));
      handler.add(new FieldInsnNode(PUTFIELD, clazz.name, THREAD, THREAD_IMPL_DESC));
      handler.add(new JumpInsnNode(GOTO, retry));
      //noinspection unchecked
      method.tryCatchBlocks.add(new TryCatchBlockNode(beginTry, endTry, catchNPE, NPE_NAME));

      method.instructions.add(handler);
    }
  }

  /**
   * Replace all return instructions by ThreadFinishedException.
   * Needed for transformation of IRunnable.run().
   *
   * @param clazz clazz to alter
   * @param run run method to alter
   */
  protected void replaceReturns(ClassNode clazz, MethodNode run)
  {
    for (AbstractInsnNode returnInstruction : returnInstructions(run.instructions))
    {
      InsnList instructions = new InsnList();
      // TODO 2009-11-21 mh: implement once, jump to implementation
      instructions.add(new TypeInsnNode(NEW, THREAD_FINISHED_EXCEPTION_NAME));
      instructions.add(new InsnNode(DUP));
      // TODO 2010-02-02 mh: use thread name instead of runnable.toString()
      instructions.add(new VarInsnNode(ALOAD, 0));
      instructions.add(new MethodInsnNode(INVOKEVIRTUAL, OBJECT_NAME, "toString", "()" + STRING_DESC));
      instructions.add(new MethodInsnNode(INVOKESPECIAL, THREAD_FINISHED_EXCEPTION_NAME, "<init>", "(" + STRING_DESC + ")V"));
      instructions.add(new InsnNode(ATHROW));

      run.instructions.insertBefore(returnInstruction, instructions);
      run.instructions.remove(returnInstruction);
    }
  }

  //
  // helper
  //

  /**
   * Checks not interruptible method to contain no calls of interruptible methods.
   *
   * @param clazz class of method to check
   * @param method not interruptible method to check
   */
  protected void check(ClassNode clazz, MethodNode method)
  {
    AbstractInsnNode[] instructions = method.instructions.toArray();
    for (AbstractInsnNode instruction : instructions)
    {
      if (instruction.getType() == AbstractInsnNode.METHOD_INSN)
      {
        MethodInsnNode methodCall = (MethodInsnNode) instruction;

        if (!isInterruptible(methodCall))
        {
          // nothing to check on not interruptible methods
          continue;
        }

        if (!isExecutor(clazz, method))
        {
          throw new NotTransformableException(
            "Not interruptible method " + org.serialthreads.transformer.code.MethodCode.methodName(clazz, method) +
              " calls interruptible method " + org.serialthreads.transformer.code.MethodCode.methodName(methodCall));
        }
        else if (!isRun(methodCall, classInfoCache))
        {
          throw new NotTransformableException(
            "Executor " + org.serialthreads.transformer.code.MethodCode.methodName(clazz, method) +
              " may only call run, but called " + org.serialthreads.transformer.code.MethodCode.methodName(methodCall));
        }
      }
    }
  }

  /**
   * Check, if method is an executor.
   *
   * @param clazz owner of method
   * @param method method
   */
  protected boolean isExecutor(ClassNode clazz, MethodNode method)
  {
    return classInfoCache.isExecutor(clazz, method);
  }

  /**
   * Check if method call is interruptible.
   *
   * @param methodCall method call
   */
  protected boolean isInterruptible(MethodInsnNode methodCall)
  {
    return isInterrupt(methodCall, classInfoCache) || classInfoCache.isInterruptible(methodCall);
  }

  //
  // Log / debug code
  //

  /**
   * Log details of method for debugging purposes.
   *
   * @param method method
   */
  protected void logDebug(MethodNode method)
  {
    log.debug("    Max stack : " + method.maxStack);
    log.debug("    Max locals: " + method.maxLocals);
  }

  /**
   * Log contents of a frame for debugging purposes.
   *
   * @param frame frame
   */
  protected void logDebug(Frame frame)
  {
    for (int i = 0; i < frame.getLocals(); i++)
    {
      BasicValue local = (BasicValue) frame.getLocal(i);
      log.debug("        Local " + i + ": " + (local.isReference()? local.getType().getDescriptor() : local));
    }
    for (int i = 0; i < frame.getStackSize(); i++)
    {
      BasicValue stack = (BasicValue) frame.getStack(i);
      log.debug("        Stack " + i + ": " + (stack.isReference()? stack.getType().getDescriptor() : stack));
    }
  }
}
