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

  private final StackFrameCode stackFrameCode = new CompactingStackFrameCode();

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
    LocalVariablesShifter.shift(firstLocal(method), 3, method);
  }

  /**
   * Add names for added locals.
   */
  protected void nameAddedLocals() {
    InsnList instructions = method.instructions;
    LabelNode first = new LabelNode();
    instructions.insertBefore(instructions.getFirst(), first);
    LabelNode last = new LabelNode();
    instructions.insert(instructions.getLast(), last);

    List<LocalVariableNode> locals = method.localVariables;
    locals.add(new LocalVariableNode("thread", THREAD_DESC, null, first, last, localThread()));
    locals.add(new LocalVariableNode("previousFrame", FRAME_IMPL_DESC, null, first, last, localPreviousFrame()));
    locals.add(new LocalVariableNode("frame", FRAME_IMPL_DESC, null, first, last, localFrame()));
  }

  /**
   * Parameter holding the thread in copied methods.
   */
  protected int paramThread() {
    return param(0);
  }

  /**
   * Parameter holding the previous frame in copied methods.
   */
  protected int paramPreviousFrame() {
    return param(1);
  }

  /**
   * Parameter p used by this transformer.
   */
  private int param(int p) {
    return firstParam(method) + p;
  }

  /**
   * Local holding the thread.
   * This is the parameter holding the thread in original methods.
   */
  protected int localThread() {
    return local(0);
  }

  /**
   * Local holding the previous frame.
   * This is the parameter holding the previous frame in original methods.
   */
  protected int localPreviousFrame() {
    return local(1);
  }

  /**
   * Newly introduced local holding the current frame.
   */
  protected int localFrame() {
    return local(2);
  }

  /**
   * Local l used by this transformer.
   */
  private int local(int l) {
    return firstLocal(method) + l;
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
          } else if (isReturn(nextInstruction(methodCall))) {
            // Tag (tail) method call.
            tag(instruction, TAG_TAIL_CALL);
          }
        }
      }
    }
  }

  /**
   * Next (real) instruction. Skips label nodes.
   */
  protected AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
    AbstractInsnNode next = instruction;
    do {
      next = next.getNext();
      if (next == null) {
        return null;
      }
    } while (next instanceof LabelNode);

    return next;
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
   * @param getMethod instruction to get method index onto the top of the stack.
   * @param startIndex first method index, should be -1 for run(), 0 otherwise.
   * @return Generated restore code.
   */
  protected InsnList restoreCodeDispatcher(InsnList getMethod, List<LabelNode> restores, int startIndex) {
    assert !restores.isEmpty() : "Precondition: !restores.isEmpty()";

    InsnList result = new InsnList();

    if (restores.size() == 1) {
      // just one method call -> nothing to dispatch -> execute directly
      result.add(new JumpInsnNode(GOTO, restores.get(0)));
      return result;
    }

    LabelNode defaultLabel = new LabelNode();
    restores.replaceAll(label -> label != null? label : defaultLabel);

    // switch(currentFrame.method) // branch to specific restore code
    result.add(getMethod);
    result.add(new TableSwitchInsnNode(startIndex, startIndex + restores.size() - 1, defaultLabel, restores.toArray(new LabelNode[restores.size()])));

    // default case -> may not happen -> throw IllegalThreadStateException
    result.add(defaultLabel);
    result.add(new TypeInsnNode(NEW, "java/lang/IllegalThreadStateException"));
    result.add(new InsnNode(DUP));
    result.add(new LdcInsnNode("Invalid method pointer"));
    result.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/IllegalThreadStateException", "<init>", "(" + STRING_DESC + ")V", false));
    result.add(new InsnNode(ATHROW));

    return result;
  }

  //
  // capture code insertion
  //

  /**
   * Inserts capture code after method calls.
   * Mainly used for concrete methods.
   */
  protected void insertCaptureCode() {
    int methodCallIndex = 0;
    for (MethodInsnNode methodCall : interruptibleMethodCalls) {
      MetaInfo metaInfo = metaInfos.get(methodCall);

      // No restore code needed.
      InsnList restoreCode = new InsnList();

      createCaptureCode(methodCall, metaInfo, methodCallIndex++, false, restoreCode);
    }
  }

  /**
   * Inserts capture and restore code after method calls.
   * Mainly used for copied concrete methods.
   *
   * @param suppressOwner suppress capturing of owner?
   * @return Labels pointing to the generated restore codes for method calls.
   */
  protected List<LabelNode> insertCaptureAndRestoreCode(boolean suppressOwner) {
    List<LabelNode> restores = new ArrayList<>(interruptibleMethodCalls.size());
    int methodCallIndex = 0;
    for (MethodInsnNode methodCall : interruptibleMethodCalls) {
      MetaInfo metaInfo = metaInfos.get(methodCall);

      LabelNode restore = new LabelNode();
      restores.add(restore);
      InsnList restoreCode = createRestoreCode(methodCall, metaInfo);
      restoreCode.insertBefore(restoreCode.getFirst(), restore);

      createCaptureCode(methodCall, metaInfo, methodCallIndex++, suppressOwner, restoreCode);
    }

    return restores;
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

    InsnList restoreCode = new InsnList();

    LabelNode normal = new LabelNode();
    method.instructions.insert(methodCall, normal);

    // Stop deserializing.
    restoreCode.add(stopDeserializing());
    // Restore frame.
    restoreCode.add(popFromFrame(methodCall, metaInfo));

    // resume
    restoreCode.add(new JumpInsnNode(GOTO, normal));

    return restoreCode;
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
   * Insert frame capturing and restore code after a given method call.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @param position position of method call in method
   * @param suppressOwner suppress capturing of owner?
   * @param restoreCode Restore code. Null if none required.
   */
  protected void createCaptureCode(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, InsnList restoreCode) {
    if (metaInfo.tags.contains(TAG_INTERRUPT)) {
      createCaptureCodeForInterrupt(methodCall, metaInfo, position, suppressOwner, restoreCode);
    } else {
      createCaptureCodeForMethod(methodCall, metaInfo, position, suppressOwner, restoreCode);
    }
  }

  /**
   * Insert frame capturing code when starting an interrupt.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @param position position of method call in method
   * @param suppressOwner suppress capturing of owner?
   * @param restoreCode Restore code. Null if none required.
   */
  protected void createCaptureCodeForInterrupt(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, InsnList restoreCode) {
    logger.debug("      Creating capture code for interrupt");

    InsnList capture = new InsnList();

    // Capture frame.
    capture.add(pushToFrame(methodCall, metaInfo));
    capture.add(pushMethodToFrame(position));
    // TODO 2018-01-17 markus: Remove (at least for frequent 3+) because method owner is already stored in frame.
    capture.add(pushOwnerToFrame(methodCall, metaInfo, suppressOwner));
    // Start serializing and return early.
    capture.add(startSerializing());

    capture.add(restoreCode);

    // Replace dummy call of interrupt method by capture code.
    method.instructions.insert(methodCall, capture);
    method.instructions.remove(methodCall);
  }

  /**
   * Insert frame capturing code after returning from a method call.
   *
   * @param methodCall method call to generate capturing code for
   * @param metaInfo Meta information about method call
   * @param position position of method call in method
   * @param suppressOwner suppress capturing of owner?
   * @param restoreCode Restore code. Null if none required.
   */
  protected abstract void createCaptureCodeForMethod(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, InsnList restoreCode);

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

    // Get thread.
    if (isMethodNotStatic) {
      instructions.add(new VarInsnNode(ALOAD, 0));
      instructions.add(new FieldInsnNode(GETFIELD, clazz.name, THREAD, THREAD_IMPL_DESC));
    } else {
      instructions.add(new MethodInsnNode(INVOKESTATIC, MANAGER_NAME, "getThread", "()" + THREAD_DESC, false));
      instructions.add(new TypeInsnNode(CHECKCAST, THREAD_IMPL_NAME));
    }
    LabelNode retry = new LabelNode();
    instructions.add(retry);
    // Store thread always in a new local variable.
    instructions.add(new VarInsnNode(ASTORE, localThread));

    // Labels (for try block) around restore code.
    LabelNode beginTry = new LabelNode();
    instructions.add(beginTry);
    instructions.add(tryCode);
    LabelNode endTry = new LabelNode();
    instructions.add(endTry);
    instructions.add(restoreCode);

    method.instructions.insertBefore(method.instructions.getFirst(), instructions);

    // try / catch (NullPointerException e).
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
  // Stack frame code.
  //

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
    return stackFrameCode.pushToFrame(method, methodCall, metaInfo, localFrame());
  }

  /**
   * Push method onto frame.
   *
   * @param position
   *           position of method call.
   * @return generated capture code.
   */
  protected InsnList pushMethodToFrame(int position) {
    if (interruptibleMethodCalls.size() <= 1) {
      return new InsnList();
    }

    return stackFrameCode.pushMethodToFrame(position, localFrame());
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
  protected InsnList pushOwnerToFrame(MethodInsnNode methodCall, MetaInfo metaInfo, boolean suppressOwner) {
    if (suppressOwner) {
      return new InsnList();
    }

    return stackFrameCode.pushOwnerToFrame(method, methodCall, metaInfo, localPreviousFrame());
  }

  /**
   * Start serializing at interrupt.
   */
  protected InsnList startSerializing() {
    InsnList result = new InsnList();
    result.add(stackFrameCode.startSerializing(localThread()));
    result.add(dummyReturnStatement(method));
    return result;
  }

  /**
   * Stop de-serializing when interrupt location has been reached.
   */
  protected InsnList stopDeserializing() {
    return stackFrameCode.stopDeserializing(localThread());
  }

  /**
   * Restore owner.
   *
   * @param methodCall
   *           method call to process.
   * @param metaInfo
   *           Meta information about method call.
   * @return generated restore code.
   */
  protected InsnList popOwnerFromFrame(MethodInsnNode methodCall, MetaInfo metaInfo) {
    return stackFrameCode.popOwnerFromFrame(methodCall, metaInfo, localFrame());
  }

  /**
   * Restore method from frame.
   *
   * @return generated restore code.
   */
  protected InsnList popMethodFromFrame() {
    return stackFrameCode.popMethodFromFrame(localFrame());
  }

  /**
   * Restore current frame before resuming the method call
   *
   * @param methodCall
   *           method call to process.
   * @param metaInfo
   *           Meta information about method call.
   * @return generated restore code.
   */
  protected InsnList popFromFrame(MethodInsnNode methodCall, MetaInfo metaInfo) {
    return stackFrameCode.popFromFrame(method, methodCall, metaInfo, localFrame());
  }
}
