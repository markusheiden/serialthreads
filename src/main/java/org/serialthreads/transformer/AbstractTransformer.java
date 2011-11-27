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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
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
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.IntValueCode;
import org.serialthreads.transformer.debug.Debugger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.MethodCode.isNotStatic;
import static org.serialthreads.transformer.code.MethodCode.methodName;
import static org.serialthreads.transformer.code.MethodCode.returnInstructions;

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
        new NotInterruptableMethodChecker(clazz, method, classInfoCache).check();
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

    if (classInfoCache.isExecutor(clazz, method))
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

    for (AbstractInsnNode returnInstruction : returnInstructions(constructor))
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
