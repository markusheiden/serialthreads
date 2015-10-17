package org.serialthreads.transformer.strategies;

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

import java.util.*;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.MethodCode.*;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_INTERRUPT;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_INTERRUPTIBLE;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_TAIL_CALL;

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
    List<InsnList> restoreCodes = new ArrayList<>(interruptibleMethodCalls.size());
    int methodCallIndex = 0;
    for (MethodInsnNode methodCall : interruptibleMethodCalls) {
      MetaInfo metaInfo = metaInfos.get(methodCall);

      restoreCodes.add(createRestoreCode(methodCall, metaInfo));
      createCaptureCode(methodCall, metaInfo, methodCallIndex++, suppressOwner);
    }

    return restoreCodes;
  }

  /**
   * Has the method more than one interruptible method call?.
   */
  protected boolean hasMoreThanOneMethodCall() {
    return interruptibleMethodCalls.size() > 1;
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
    restore.add(stopDeserializing());

    // restore frame
    // TODO 2009-10-17 mh: avoid restore, if method returns directly after interrupt?
    restore.add(StackFrameCapture.popFromFrame(method, methodCall, metaInfo, localFrame));

    // resume
    restore.add(new JumpInsnNode(GOTO, normal));

    return restore;
  }

  /**
   * Stop de-serializing when interrupt location has been reached.
   */
  protected InsnList stopDeserializing() {
    final int localThread = localThread();

    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new InsnNode(ICONST_0));
    instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    return instructions;
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
   * @param suppressOwner suppress capturing of owner?
   */
  protected void createCaptureCode(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner) {
    if (metaInfo.tags.contains(TAG_INTERRUPT)) {
      createCaptureCodeForInterrupt(methodCall, metaInfo, position, suppressOwner);
    } else {
      createCaptureCodeForMethod(methodCall, metaInfo, position, suppressOwner);
    }
  }

  /**
   * Insert frame capturing code when starting an interrupt.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @param position position of method call in method
   * @param suppressOwner suppress capturing of owner?
   */
  protected void createCaptureCodeForInterrupt(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner) {
    logger.debug("      Creating capture code for interrupt");

    InsnList capture = new InsnList();

    // Capture frame.
    capture.add(pushToFrame(methodCall, metaInfo));
    capture.add(pushMethodToFrame(position));
    capture.add(pushOwnerToFrame(methodCall, metaInfo, suppressOwner));

    // Start serializing and return early.
    capture.add(startSerializing());

    // Replace dummy call of interrupt method by capture code.
    method.instructions.insert(methodCall, capture);
    method.instructions.remove(methodCall);
  }

  /**
   * Save current "frameAfter" after returning from a method call.
   *
   * @param methodCall
   *           method call to process.
   * @param metaInfo
   *           Meta information about method call.
   * @return generated capture code.
   */
  protected InsnList pushToFrame(MethodInsnNode methodCall, MetaInfo metaInfo) {
    return StackFrameCapture.pushToFrame(method, methodCall, metaInfo, localFrame());
  }

  /**
   * Push method onto frame.
   *
   * @param position
   *           position of method call.
   * @return generated capture code.
   */
  protected InsnList pushMethodToFrame(int position) {
      return StackFrameCapture.pushMethodToFrame(position, hasMoreThanOneMethodCall(), localFrame());
  }

  /**
   * Push owner onto frame.
   *
   * @param methodCall
   *           Method call to generate capturing code for
   * @param metaInfo
   *           Meta information about method call
   * @param suppressOwner
   *           Suppress saving the owner?.
   * @return generated capture code.
   */
  public InsnList pushOwnerToFrame(MethodInsnNode methodCall, MetaInfo metaInfo, boolean suppressOwner) {
    return StackFrameCapture.pushOwnerToFrame(method, suppressOwner || isSelfCall(methodCall, metaInfo), localPreviousFrame(), localFrame());
  }

  /**
   * Start serializing at interrupt.
   */
  protected InsnList startSerializing() {
    final int localThread = localThread();

    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new InsnNode(ICONST_1));
    instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    instructions.add(dummyReturnStatement(method));
    return instructions;
  }

  /**
   * Insert frame capturing code after returning from a method call.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @param position position of method call in method
   * @param suppressOwner suppress capturing of owner?
   */
  protected abstract void createCaptureCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner);

  /**
   * Replace all return instructions by ThreadFinishedException.
   * Needed for transformation of IRunnable.run().
   */
  protected void replaceRunReturns() {
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
}
