package org.serialthreads.transformer.strategies;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.IntValueCode.push;
import static org.serialthreads.transformer.code.MethodCode.*;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_INTERRUPT;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_INTERRUPTIBLE;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_TAIL_CALL;

import java.util.*;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.context.*;
import org.serialthreads.context.Stack;
import org.serialthreads.transformer.analyzer.ExtendedAnalyzer;
import org.serialthreads.transformer.analyzer.ExtendedFrame;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.LocalVariablesShifter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all method transformers.
 */
@SuppressWarnings({"UnusedDeclaration", "UnusedAssignment", "UnnecessaryLocalVariable"})
public abstract class AbstractMethodTransformer {
  /**
   * Logger.
   */
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  protected static final String OBJECT_NAME = Type.getType(Object.class).getInternalName();
  protected static final String OBJECT_DESC = Type.getType(Object.class).getDescriptor();
  protected static final String STRING_DESC = Type.getType(String.class).getDescriptor();
  protected static final String NPE_NAME = Type.getType(NullPointerException.class).getInternalName();
  protected static final String MANAGER_NAME = Type.getType(SerialThreadManager.class).getInternalName();
  protected static final String THREAD_DESC = Type.getType(SerialThread.class).getDescriptor();
  protected static final String THREAD = "$$thread$$";
  protected static final String THREAD_FINISHED_EXCEPTION_NAME = Type.getType(ThreadFinishedException.class).getInternalName();

  protected static final String THREAD_IMPL_NAME = Type.getType(Stack.class).getInternalName();
  protected static final String THREAD_IMPL_DESC = Type.getType(Stack.class).getDescriptor();
  protected static final String FRAME_IMPL_NAME = Type.getType(StackFrame.class).getInternalName();
  protected static final String FRAME_IMPL_DESC = Type.getType(StackFrame.class).getDescriptor();

  protected final ClassNode clazz;
  protected final MethodNode method;
  protected final IClassInfoCache classInfoCache;

  /**
   * Meta information about instructions.
   */
  protected final Map<AbstractInsnNode, MetaInfo> metaInfos = new LinkedHashMap<>();

  /**
   * Interruptible method calls.
   */
  protected final Set<MethodInsnNode> interruptibleMethodCalls = new LinkedHashSet<>();

  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected AbstractMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
    this.clazz = clazz;
    this.method = method;
    this.classInfoCache = classInfoCache;
  }

  /**
   * Shift index of the locals to get place for the three needed new locals.
   * Local 0: thread, local 1: previous frame, local 2: current frame.
   */
  protected void shiftLocals() {
    int firstLocal = firstLocal(method);

    LocalVariablesShifter.shift(firstLocal, 3, method);

    InsnList instructions = method.instructions;
    LabelNode first = new LabelNode();
    instructions.insertBefore(instructions.getFirst(), first);
    LabelNode last = new LabelNode();
    instructions.insert(instructions.getLast(), last);

    List<LocalVariableNode> locals = method.localVariables;
    locals.add(new LocalVariableNode("thread", THREAD_DESC, null, first, last, firstLocal + 0));
    locals.add(new LocalVariableNode("previousFrame", FRAME_IMPL_DESC, null, first, last, firstLocal + 1));
    locals.add(new LocalVariableNode("frame", FRAME_IMPL_DESC, null, first, last, firstLocal + 2));
  }

  /**
   * Local of parameter holding the thread.
   * This method is used for method copies. These have the thread as the first parameter.
   */
  protected int paramThread() {
    return firstParam(method) + 0;
  }

  /**
   * Local of parameter holding the previous frame.
   * This method is used for method copies. These have the previous frame as the second parameter.
   */
  protected int paramPreviousFrame() {
    return firstParam(method) + 1;
  }

  /**
   * Local holding the thread.
   */
  protected int localThread() {
    return firstLocal(method) + 0;
  }

  /**
   * Local holding the previous frame.
   */
  protected int localPreviousFrame() {
    return firstLocal(method) + 1;
  }

  /**
   * Local holding the current frame.
   */
  protected int localFrame() {
    return firstLocal(method) + 2;
  }

  /**
   * Analyze a method to compute frames.
   * Extract all interruptible method calls.
   *
   * @throws AnalyzerException
   */
  protected void analyze() throws AnalyzerException {
    // Init meta information
    Frame[] frames = ExtendedAnalyzer.analyze(clazz, method, classInfoCache);
    InsnList instructions = method.instructions;
    for (int i = 0, e = instructions.size(), last = e - 1; i < e; i++) {
      metaInfos.put(instructions.get(i), new MetaInfo((ExtendedFrame) frames[i], i < last ? (ExtendedFrame) frames[i + 1] : null));
    }

    // Tag special instructions
    for (int i = 0, e = instructions.size(); i < e; i++) {
      AbstractInsnNode instruction = instructions.get(i);
      if (instruction instanceof MethodInsnNode) {
        MethodInsnNode methodCall = (MethodInsnNode) instruction;
        if (classInfoCache.isInterruptible(methodCall)) {
          interruptibleMethodCalls.add(methodCall);
          tag(instruction, TAG_INTERRUPTIBLE);
          if (classInfoCache.isInterrupt(methodCall)) {
            tag(instruction, TAG_INTERRUPT);
          } else if (isReturn(methodCall.getNext())) {
            // Tag (tail) method call.
            tag(instruction, TAG_TAIL_CALL);
            // Tag return too.
            tag(instruction.getNext(), TAG_TAIL_CALL);
          }
        }
      }
    }
  }

  /**
   * Tag an instruction.
   *
   * @param instruction Instruction
   * @param tag Tag
   */
  private void tag(AbstractInsnNode instruction, String tag) {
    metaInfos.get(instruction).tags.add(tag);
  }

  /**
   * Create restore code dispatcher.
   *
   * @param getMethod instruction to get method index onto the top of the stack
   * @param restoreCodes restore codes
   * @param startIndex first method index, should be -1 for run(), 0 otherwise
   * @return generated restore code
   */
  protected InsnList restoreCodeDispatcher(InsnList getMethod, List<InsnList> restoreCodes, int startIndex) {
    assert !restoreCodes.isEmpty() : "Precondition: !restoreCodes.isEmpty()";

    if (restoreCodes.size() == 1) {
      // just one method call -> nothing to dispatch -> execute directly
      return restoreCodes.get(0);
    }

    InsnList result = new InsnList();

    // Label to the specific restore code for every method call
    LabelNode[] labels = new LabelNode[restoreCodes.size()];
    for (int i = 0; i < labels.length; i++) {
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
    result.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/IllegalThreadStateException", "<init>", "(" + STRING_DESC + ")V", false));
    result.add(new InsnNode(ATHROW));

    // reverse iteration to put first restore code at the end
    for (int i = labels.length - 1; i >= 0; i--) {
      result.add(labels[i]);
      result.add(restoreCodes.get(i));
    }

    return result;
  }

  //
  // capture code insertion
  //

  /**
   * Inserts capture code after method calls.
   *
   * @param suppressOwner suppress capturing of owner?
   * @return generated restore codes for method calls
   */
  protected List<InsnList> insertCaptureCode(boolean suppressOwner) {
    boolean moreThanOne = interruptibleMethodCalls.size() > 1;

    List<InsnList> restoreCodes = new ArrayList<>(interruptibleMethodCalls.size());
    int methodCallIndex = 0;
    for (MethodInsnNode methodCall : interruptibleMethodCalls) {
      MetaInfo metaInfo = metaInfos.get(methodCall);

      restoreCodes.add(createRestoreCode(methodCall, metaInfo));
      createCaptureCode(methodCall, metaInfo, methodCallIndex++, moreThanOne, suppressOwner);
    }

    return restoreCodes;
  }

  /**
   * Create method specific frame restore code.
   *
   * @param methodCall method call to generate restore code for
   * @param metaInfo Meta information about method call
   * @return restore code
   */
  protected InsnList createRestoreCode(MethodInsnNode methodCall, MetaInfo metaInfo) {
    return metaInfo.tags.contains(TAG_INTERRUPT) ?
      createRestoreCodeForInterrupt(methodCall, metaInfo) :
      createRestoreCodeForMethod(methodCall, metaInfo);
  }

  /**
   * Create restore code for ending an interrupt.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @return restore code
   */
  protected InsnList createRestoreCodeForInterrupt(MethodInsnNode methodCall, MetaInfo metaInfo) {
    logger.debug("      Creating restore code for interrupt");

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    InsnList restore = new InsnList();

    LabelNode normal = new LabelNode();
    method.instructions.insert(methodCall, normal);

    // stop deserializing
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new InsnNode(ICONST_0));
    restore.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "serializing", "Z"));

    // restore frame
    // TODO 2009-10-17 mh: avoid restore, if method returns directly after interrupt?
    restore.add(StackFrameCapture.popFromFrame(method, methodCall, metaInfo, localFrame));

    // resume
    restore.add(new JumpInsnNode(GOTO, normal));

    return restore;
  }

  /**
   * Create method specific frame restore code.
   *
   * @param methodCall method call to generate restore code for
   * @param metaInfo Meta information about method call
   * @return restore code
   */
  protected abstract InsnList createRestoreCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo);

  /**
   * Insert frame capturing code after a given method call.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @param position position of method call in method
   * @param containsMoreThanOneMethodCall does the method contain more than one method call at all?
   * @param suppressOwner suppress capturing of owner?
   */
  protected void createCaptureCode(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner) {
    if (metaInfo.tags.contains(TAG_INTERRUPT)) {
      createCaptureCodeForInterrupt(methodCall, metaInfo, position, containsMoreThanOneMethodCall, suppressOwner);
    } else {
      createCaptureCodeForMethod(methodCall, metaInfo, position, containsMoreThanOneMethodCall, suppressOwner);
    }
  }

  /**
   * Insert frame capturing code when starting an interrupt.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @param position position of method call in method
   * @param containsMoreThanOneMethodCall does the method contain more than one method call at all?
   * @param suppressOwner suppress capturing of owner?
   */
  protected void createCaptureCodeForInterrupt(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner) {
    logger.debug("      Creating capture code for interrupt");

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    InsnList capture = new InsnList();

    // capture frame
    // TODO 2009-10-17 mh: avoid capture, if method returns directly after interrupt?
    capture.add(StackFrameCapture.pushToFrame(method, methodCall, metaInfo, localFrame));
    capture.add(pushMethodToFrame(position, containsMoreThanOneMethodCall, suppressOwner || isSelfCall(methodCall, metaInfo), localPreviousFrame, localFrame));

    // "start" serializing
    capture.add(new VarInsnNode(ALOAD, localThread));
    capture.add(new InsnNode(ICONST_1));
    capture.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "serializing", "Z"));

    // return early
    capture.add(dummyReturn());

    // replace dummy call of interrupt method by capture code
    method.instructions.insert(methodCall, capture);
    method.instructions.remove(methodCall);
  }

  /**
   * Dummy return for the given method.
   */
  protected InsnList dummyReturn() {
    InsnList result = new InsnList();
    result.add(dummyReturnStatement(method));
    return result;
  }

  /**
   * Insert frame capturing code after returning from a method call.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @param position position of method call in method
   * @param containsMoreThanOneMethodCall does the method contain more than one method call at all?
   * @param suppressOwner suppress capturing of owner?
   */
  protected abstract void createCaptureCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner);

  /**
   * Replace all return instructions by ThreadFinishedException.
   * Needed for transformation of IRunnable.run().
   */
  protected void replaceReturns() {
    // TODO 2013-11-24 mh: implement as method -> call method?

    LabelNode exception = new LabelNode();
    for (AbstractInsnNode returnInstruction : returnInstructions(method)) {
      method.instructions.set(returnInstruction, new JumpInsnNode(GOTO, exception));
    }

    InsnList instructions = new InsnList();
    instructions.add(exception);
    instructions.add(new TypeInsnNode(NEW, THREAD_FINISHED_EXCEPTION_NAME));
    instructions.add(new InsnNode(DUP));
    // TODO 2010-02-02 mh: use thread name instead of runnable.toString()
    instructions.add(new VarInsnNode(ALOAD, 0));
    instructions.add(new MethodInsnNode(INVOKEVIRTUAL, OBJECT_NAME, "toString", "()" + STRING_DESC, false));
    instructions.add(new MethodInsnNode(INVOKESPECIAL, THREAD_FINISHED_EXCEPTION_NAME, "<init>", "(" + STRING_DESC + ")V", false));
    instructions.add(new InsnNode(ATHROW));
    method.instructions.insert(method.instructions.getLast(), instructions);
  }

  /**
   * Create "get thread" code including exception handling.
   * Inserts generated code directly into method.
   * Required stack size: 3.
   *
   * @param localThread number of local containing the thread
   * @param tryCode restore code which can cause a NullPointerException during access of this.$$thread$$
   * @param restoreCode remaining restore code, executed directly after tryCode
   */
  protected void insertMethodGetThreadStartCode(int localThread, InsnList tryCode, InsnList restoreCode) {
    final boolean isMethodNotStatic = isNotStatic(method);

    InsnList instructions = new InsnList();

    // store thread always in a new local variable
    if (isMethodNotStatic) {
      instructions.add(new VarInsnNode(ALOAD, 0));
      instructions.add(new FieldInsnNode(GETFIELD, clazz.name, THREAD, THREAD_IMPL_DESC));
    } else {
      instructions.add(new MethodInsnNode(INVOKESTATIC, MANAGER_NAME, "getThread", "()" + THREAD_DESC, false));
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

    if (isMethodNotStatic) {
      InsnList handler = new InsnList();

      // try / catch (NullPointerException) for thread access
      LabelNode catchNPE = new LabelNode();
      handler.add(catchNPE);
      // Pop NPE from stack
      handler.add(new InsnNode(POP));
      handler.add(new VarInsnNode(ALOAD, 0));
      handler.add(new MethodInsnNode(INVOKESTATIC, MANAGER_NAME, "getThread", "()" + THREAD_DESC, false));
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
  // frame related code
  //

  /**
   * Push method and owner onto frame.
   *
   * @param position position of method call
   * @param containsMoreThanOneMethodCall contains the method more than one method call?
   * @param suppressOwner suppress saving the owner?
   * @param localPreviousFrame number of local containing the previous frame or -1 for retrieving it via current frame
   * @param localFrame number of local containing the current frame
   * @return generated capture code
   */
  protected InsnList pushMethodToFrame(int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner, int localPreviousFrame, int localFrame) {
    InsnList result = new InsnList();

    // save method index of this method
    if (containsMoreThanOneMethodCall) {
      // frame.method = position;
      result.add(new VarInsnNode(ALOAD, localFrame));
      result.add(push(position));
      result.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
    }

    // save owner of method call one level above
    if (isNotStatic(method) && !suppressOwner) {
      // previousFrame.owner = this;
      if (localPreviousFrame < 0) {
        result.add(new VarInsnNode(ALOAD, localFrame));
        result.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "previous", FRAME_IMPL_DESC));
      } else {
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
   * @see Stack#leaveMethod(Object, int) etc.
   *
   * @param position position of method call
   * @param containsMoreThanOneMethodCall contains the method more than one method call?
   * @param methodName name of method to store owner and method
   * @param localThread number of local containing the thread
   * @return generated capture code
   */
  protected InsnList pushMethodToFrame(int position, boolean containsMoreThanOneMethodCall, String methodName, int localThread) {
    InsnList result = new InsnList();

    final boolean isMethodNotStatic = isNotStatic(method);

    // save method index of this method and owner of method call one level above
    boolean pushOwner = isMethodNotStatic && !isRun(clazz, method, classInfoCache);
    boolean pushMethod = containsMoreThanOneMethodCall;
    if (pushOwner && pushMethod) {
      // save owner of this method for calling method and index of interrupted method
      result.add(new VarInsnNode(ALOAD, localThread));
      result.add(new VarInsnNode(ALOAD, 0));
      result.add(push(position));
      result.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, methodName, "(" + OBJECT_DESC + "I)V", false));
    } else if (pushOwner) {
      // save owner of this method for calling method
      result.add(new VarInsnNode(ALOAD, localThread));
      result.add(new VarInsnNode(ALOAD, 0));
      result.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, methodName, "(" + OBJECT_DESC + ")V", false));
    } else if (pushMethod) {
      // save index of interrupted method
      result.add(new VarInsnNode(ALOAD, localThread));
      result.add(push(position));
      result.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, methodName, "(I)V", false));
    }

    return result;
  }
}
