package org.serialthreads.transformer.strategies;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.context.*;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.LoadUntransformedException;
import org.serialthreads.transformer.NotTransformableException;
import org.serialthreads.transformer.analyzer.ExtendedAnalyzer;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.IntValueCode;
import org.serialthreads.transformer.debug.Debugger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.MethodCode.*;

/**
 * Base implementation of a transformer.
 */
public abstract class AbstractTransformer implements ITransformer {
  /**
   * Logger.
   */
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  protected static final String OBJECT_NAME = Type.getType(Object.class).getInternalName();
  protected static final String OBJECT_DESC = Type.getType(Object.class).getDescriptor();
  protected static final String CLASS_NAME = Type.getType(Class.class).getInternalName();
  protected static final String CLASS_DESC = Type.getType(Class.class).getDescriptor();
  protected static final String STRING_DESC = Type.getType(String.class).getDescriptor();
  protected static final String IRUNNABLE_NAME = Type.getType(IRunnable.class).getInternalName();
  protected static final String ITRANSFORMED_RUNNABLE_NAME = Type.getType(ITransformedRunnable.class).getInternalName();
  protected static final String THREAD_DESC = Type.getType(SerialThread.class).getDescriptor();
  protected static final String THREAD = "$$thread$$";

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
  protected AbstractTransformer(IClassInfoCache classInfoCache, int defaultFrameSize) {
    assert classInfoCache != null : "Precondition: classInfoCache != null";
    assert defaultFrameSize > 0 : "Precondition: defaultFrameSize > 0";

    this.classInfoCache = classInfoCache;
    this.defaultFrameSize = defaultFrameSize;
  }

  @Override
  public void transform(ClassNode clazz) {
    logger.info("Transforming class {} with {}", clazz.name, toString());

    // separate constructors and methods
    List<MethodNode> constructors = new ArrayList<>(clazz.methods.size());
    List<MethodNode> methods = new ArrayList<>(clazz.methods.size());
    for (MethodNode method : clazz.methods) {
      if (method.name.equals("<init>")) {
        constructors.add(method);
      } else {
        methods.add(method);
      }
    }

    List<MethodNode> allTransformedMethods = new ArrayList<>(methods.size() * 2);
    for (MethodNode method : methods) {
      List<MethodNode> transformedMethods = transformMethod(clazz, method);
      if (transformedMethods == null) {
        // method not transformed? -> check that it contains no calls of interruptible methods
        check(clazz, method);
      } else {
        allTransformedMethods.addAll(transformedMethods);

        if (logger.isDebugEnabled()) {
          // analyze methods again to be sure that they are correct
          reanalyzeMethods(clazz, transformedMethods);
        }
      }
    }

    if (allTransformedMethods.isEmpty()) {
      // Class needs no transformation, but it should be loaded with this class loader
      logger.debug("  Class {} needs no transformation", clazz.name);
      throw new LoadUntransformedException(clazz.name);
    }

    afterTransformation(clazz, constructors);

    logger.debug("Finished transforming of class {}", clazz.name);
  }

  /**
   * Checks not interruptible method to contain no calls of interruptible methods.
   *
   * @param clazz Clazz to check
   * @param method Method to check
   */
  private void check(ClassNode clazz, MethodNode method) {
    AbstractInsnNode[] instructions = method.instructions.toArray();
    for (AbstractInsnNode instruction : instructions) {
      if (instruction.getType() == AbstractInsnNode.METHOD_INSN) {
        MethodInsnNode methodCall = (MethodInsnNode) instruction;

        if (!classInfoCache.isInterruptible(methodCall)) {
          // nothing to check on not interruptible methods
          continue;
        }

        if (!classInfoCache.isExecutor(clazz, method)) {
          throw new NotTransformableException(
            "Not interruptible method " + org.serialthreads.transformer.code.MethodCode.methodName(clazz, method) +
              " calls interruptible method " + org.serialthreads.transformer.code.MethodCode.methodName(methodCall));
        } else if (!isRun(methodCall, classInfoCache)) {
          throw new NotTransformableException(
            "Executor " + org.serialthreads.transformer.code.MethodCode.methodName(clazz, method) +
              " may only call run, but called " + org.serialthreads.transformer.code.MethodCode.methodName(methodCall));
        }
      }
    }
  }

  /**
   * Reanalyze transformed methods to be sure, that they are correct.
   * Logs disassembled transformed byte code on errors.
   *
   * @param clazz owning class
   * @param methods transformed methods
   */
  private void reanalyzeMethods(ClassNode clazz, List<MethodNode> methods) throws NotTransformableException {
    for (MethodNode method : methods) {
      try {
        ExtendedAnalyzer.analyze(clazz, method, classInfoCache);
      } catch (Exception e) {
        // disassemble erroneous method
        logger.debug("Unable to analyze transformed method " + methodName(clazz, method) + ": " + e.getMessage());
        logger.debug("Byte code:\n" + Debugger.debug(clazz.name, method));
        throw new NotTransformableException("Unable to analyze transformed method " + methodName(clazz, method) + ": " + e.getMessage(), e);
      }
    }
  }

  /**
   * Execute byte code transformation on interruptible method.
   *
   * @param clazz class to transform
   * @param method method node to transform
   * @return transformed methods or null, if the method needs to transformation
   */
  protected List<MethodNode> transformMethod(ClassNode clazz, MethodNode method) {
    logger.debug("  Transforming method {}", methodName(clazz, method));

    if (classInfoCache.isExecutor(clazz, method)) {
      // TODO 2009-12-11 mh: check executor
      // bypass check()
      return Arrays.asList(method);
    }

    if (!classInfoCache.isInterruptible(clazz, method)) {
      logger.debug("    Not interruptible -> abort transformation of method");

      return null;
    }

    try {
      if (logger.isDebugEnabled()) {
        logDebug(method);
      }

      return doTransformMethod(clazz, method);
    } catch (AnalyzerException e) {
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
   * Are there no interruptible method calls?.
   *
   * @param method method node to transform
   */
  protected boolean hasNoInterruptibleMethodCalls(MethodNode method) {
    for (int i = 0; i < method.instructions.size(); i++) {
      AbstractInsnNode instruction = method.instructions.get(i);
      if (instruction instanceof MethodInsnNode) {
        if (classInfoCache.isInterruptible((MethodInsnNode) instruction)) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Execute some final modifications after transformation.
   * This method will be only executed, if some methods of the clazz had been transformed.
   *
   * @param clazz class to alter
   * @param constructors all constructors of class
   */
  protected void afterTransformation(ClassNode clazz, List<MethodNode> constructors) {
    implementTransformedRunnable(clazz, constructors);
  }

  /**
   * Make IRunnables implement ITransformedRunnable.
   *
   * @param clazz class to transform
   * @param constructors constructors
   * @return whether ITransformedRunnable has been implemented
   */
  protected boolean implementTransformedRunnable(ClassNode clazz, List<MethodNode> constructors) {
    if (!classInfoCache.hasSuperClass(clazz.name, IRUNNABLE_NAME)) {
      return false;
    }

    logger.debug("  Implement ITransformedRunnable");

    // make class implement ITransformedRunnable
    if (clazz.interfaces.contains(ITRANSFORMED_RUNNABLE_NAME)) {
      throw new NotTransformableException("Custom classes may not not implement ITransformedRunnable. Implement IRunnable instead.");
    }

    clazz.interfaces.add(ITRANSFORMED_RUNNABLE_NAME);

    // add $$thread$$ field
    clazz.fields.add(new FieldNode(ACC_PRIVATE + ACC_FINAL + ACC_SYNTHETIC, THREAD, THREAD_IMPL_DESC, null, null));

    // init $$thread$$ fields in constructors
    for (MethodNode constructor : constructors) {
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
  protected void transformConstructor(ClassNode clazz, MethodNode constructor) {
    assert constructor.name.equals("<init>") : "Precondition: constructor.name.equals(\"<init>\")";

    logger.debug("    Transforming constructor {}", methodName(clazz, constructor));

    constructor.maxStack = Math.max(5, constructor.maxStack);

    for (AbstractInsnNode returnInstruction : returnInstructions(constructor)) {
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
  private void createGetThread(ClassNode clazz) {
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
  // Log / debug code
  //

  /**
   * Log details of method for debugging purposes.
   *
   * @param method method
   */
  protected void logDebug(MethodNode method) {
    logger.debug("    Max stack : " + method.maxStack);
    logger.debug("    Max locals: " + method.maxLocals);
  }

  /**
   * Log contents of a frame for debugging purposes.
   *
   * @param frame frame
   */
  @SuppressWarnings({"UnusedDeclaration"})
  protected void logDebug(Frame frame) {
    for (int i = 0; i < frame.getLocals(); i++) {
      BasicValue local = (BasicValue) frame.getLocal(i);
      logger.debug("        Local " + i + ": " + (local.isReference() ? local.getType().getDescriptor() : local));
    }
    for (int i = 0; i < frame.getStackSize(); i++) {
      BasicValue stack = (BasicValue) frame.getStack(i);
      logger.debug("        Stack " + i + ": " + (stack.isReference() ? stack.getType().getDescriptor() : stack));
    }
  }
}
