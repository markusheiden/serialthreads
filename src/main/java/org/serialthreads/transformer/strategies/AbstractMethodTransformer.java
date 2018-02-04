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
import org.serialthreads.transformer.code.CompactingStackCode;
import org.serialthreads.transformer.code.LocalVariablesShifter;
import org.serialthreads.transformer.code.ThreadCode;
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
  protected static final String THREAD_FINISHED_EXCEPTION_NAME = Type.getType(ThreadFinishedException.class).getInternalName();

  protected static final String THREAD_IMPL_NAME = Type.getType(Stack.class).getInternalName();
  protected static final String THREAD_IMPL_DESC = Type.getType(Stack.class).getDescriptor();
  protected static final String FRAME_IMPL_NAME = Type.getType(StackFrame.class).getInternalName();
  protected static final String FRAME_IMPL_DESC = Type.getType(StackFrame.class).getDescriptor();

  protected final ClassNode clazz;
  protected final MethodNode method;
  protected final IClassInfoCache classInfoCache;

  protected final ThreadCode threadCode = new CompactingStackCode();

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
    LabelNode first = insertLabelBefore(method.instructions.getFirst());
    LabelNode last = insertLabelAfter(method.instructions.getLast());

    List<LocalVariableNode> locals = method.localVariables;
//    locals.add(new LocalVariableNode("thread", THREAD_DESC, null, first, last, localThread()));
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
  protected final int param(int p) {
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
  protected final int local(int l) {
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

    InsnList instructions = new InsnList();

    if (restores.size() == 1) {
      // just one method call -> nothing to dispatch -> execute directly
      instructions.add(new JumpInsnNode(GOTO, restores.get(0)));
      return instructions;
    }

    LabelNode defaultLabel = new LabelNode();
    restores.replaceAll(label -> label != null? label : defaultLabel);

    // switch(currentFrame.method) // branch to specific restore code
    instructions.add(getMethod);
    instructions.add(new TableSwitchInsnNode(startIndex, startIndex + restores.size() - 1, defaultLabel, restores.toArray(new LabelNode[restores.size()])));

    // default case -> may not happen -> throw IllegalThreadStateException
    instructions.add(defaultLabel);
    instructions.add(new TypeInsnNode(NEW, "java/lang/IllegalThreadStateException"));
    instructions.add(new InsnNode(DUP));
    instructions.add(new LdcInsnNode("Invalid method pointer"));
    instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/IllegalThreadStateException", "<init>", "(" + STRING_DESC + ")V", false));
    instructions.add(new InsnNode(ATHROW));

    return instructions;
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
      createCaptureAndRestoreCode(methodCall, metaInfo, methodCallIndex++, false, false);
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
      restores.add(createCaptureAndRestoreCode(methodCall, metaInfo, methodCallIndex++, suppressOwner, true));
    }

    return restores;
  }

  /**
   * Insert frame capturing and restore code after the given method call.
   *
   * @param methodCall Method call to generate capturing code for.
   * @param metaInfo Meta information about method call.
   * @param position Position of method call in method.
   * @param suppressOwner Suppress capturing of owner?.
   * @param restore Generate restore code too?.
   * @return Label to restore code, or null, if no restore code has been generated.
   */
  protected abstract LabelNode createCaptureAndRestoreCode(MethodInsnNode methodCall, MetaInfo metaInfo, int position, boolean suppressOwner, boolean restore);

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
    // throw new ThreadFinishedException(this.toString());
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
    if (isStatic(method)) {
      insertStaticMethodGetThreadStartCode(localThread, tryCode, restoreCode);
    } else {
      insertNonStaticMethodGetThreadStartCode(localThread, tryCode, restoreCode);
    }
  }

  /**
   * Create "get thread" code for static methods including exception handling.
   * Inserts generated code directly into method.
   * Required stack size: 3.
   *
   * @param localThread number of local containing the thread
   * @param tryCode restore code which can cause a NullPointerException during access of this.$$thread$$
   * @param restoreCode remaining restore code, executed directly after tryCode
   */
  protected void insertStaticMethodGetThreadStartCode(int localThread, InsnList tryCode, InsnList restoreCode) {
    InsnList instructions = new InsnList();

    // thread = SerialThreadManager.getThread();
    instructions.add(new MethodInsnNode(INVOKESTATIC, MANAGER_NAME, "getThread", "()" + THREAD_DESC, false));
    instructions.add(new TypeInsnNode(CHECKCAST, THREAD_IMPL_NAME));
    instructions.add(new VarInsnNode(ASTORE, localThread));
    // thread is always not null, so new exception handling needed here.
    instructions.add(tryCode);
    // Remaining restore code.
    instructions.add(restoreCode);

    method.instructions.insertBefore(method.instructions.getFirst(), instructions);
    return;
  }

  /**
   * Create "get thread" code for non-static methods including exception handling.
   * Inserts generated code directly into method.
   * Required stack size: 3.
   *
   * @param localThread number of local containing the thread
   * @param tryCode restore code which can cause a NullPointerException during access of this.$$thread$$
   * @param restoreCode remaining restore code, executed directly after tryCode
   */
  protected void insertNonStaticMethodGetThreadStartCode(int localThread, InsnList tryCode, InsnList restoreCode) {
    InsnList instructions = new InsnList();

    // thread = this.$$thread$$;
    instructions.add(threadCode.pushThread(clazz.name));

    LabelNode retry = new LabelNode();
    instructions.add(retry);
    // Store thread always in a new local variable.
    instructions.add(new VarInsnNode(ASTORE, localThread));

    // Labels (for try block) around restore code.
    LabelNode beginTry = new LabelNode();
    instructions.add(beginTry);
    // Code relying on thread to be not null.
    instructions.add(tryCode);
    LabelNode endTry = new LabelNode();
    instructions.add(endTry);
    // Remaining restore code.
    instructions.add(restoreCode);

    method.instructions.insertBefore(method.instructions.getFirst(), instructions);

    // try / catch (NullPointerException) for thread access
    InsnList handler = new InsnList();
    LabelNode catchNPE = new LabelNode();
    handler.add(catchNPE);
    // Pop NPE from stack
    handler.add(new InsnNode(POP));
    handler.add(new VarInsnNode(ALOAD, 0));
    handler.add(new MethodInsnNode(INVOKESTATIC, MANAGER_NAME, "getThread", "()" + THREAD_DESC, false));
    handler.add(new TypeInsnNode(CHECKCAST, THREAD_IMPL_NAME));
    handler.add(new InsnNode(DUP_X1));
    handler.add(threadCode.setThread(clazz.name));
    handler.add(new JumpInsnNode(GOTO, retry));
    method.instructions.add(handler);
    //noinspection unchecked
    method.tryCatchBlocks.add(new TryCatchBlockNode(beginTry, endTry, catchNPE, NPE_NAME));
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
  protected InsnList captureFrame(MethodInsnNode methodCall, MetaInfo metaInfo) {
    return threadCode.captureFrame(method, methodCall, metaInfo, localFrame());
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
  protected InsnList restoreFrame(MethodInsnNode methodCall, MetaInfo metaInfo) {
    return threadCode.restoreFrame(method, methodCall, metaInfo, localFrame());
  }

  /**
   * Push method onto frame.
   *
   * @param position
   *           position of method call.
   * @return generated capture code.
   */
  protected InsnList setMethod(int position) {
    if (interruptibleMethodCalls.size() <= 1) {
      return new InsnList();
    }

    return threadCode.setMethod(localFrame(), position);
  }

  /**
   * Restore method from frame.
   *
   * @return generated restore code.
   */
  protected InsnList pushMethod() {
    return threadCode.pushMethod(localFrame());
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
  protected InsnList setOwner(MethodInsnNode methodCall, MetaInfo metaInfo, boolean suppressOwner) {
    if (suppressOwner || isSelfCall(methodCall, metaInfo) || isStatic(method)) {
      return new InsnList();
    }

    return threadCode.setOwner(localPreviousFrame());
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
  protected InsnList pushOwner(MethodInsnNode methodCall, MetaInfo metaInfo) {
    InsnList instructions = new InsnList();

    if (isSelfCall(methodCall, metaInfo)) {
      // self call: owner == this
      instructions.add(new VarInsnNode(ALOAD, 0));
    } else if (isNotStatic(methodCall)) {
      // get owner
      instructions.add(threadCode.pushOwner(localFrame()));
      instructions.add(new TypeInsnNode(CHECKCAST, methodCall.owner));
    }

    return instructions;
  }

  //
  // Instructions helper.
  //

  /**
   * Replace instruction.
   *
   * @param instruction Instruction to replace.
   * @param replacement Replacement.
   */
  protected void replace(AbstractInsnNode instruction, InsnList replacement) {
    method.instructions.insert(instruction, replacement);
    method.instructions.remove(instruction);
  }

  /**
   * Insert label before instruction.
   *
   * @param instruction Instruction to insert label before.
   * @return Existing label, if there is already a label before the instruction, new label otherwise.
   */
  protected LabelNode insertLabelBefore(AbstractInsnNode instruction) {
    for (AbstractInsnNode p = instruction.getPrevious(); p != null && p.getOpcode() < 0; p = p.getPrevious()) {
      if (p instanceof LabelNode) {
        return (LabelNode) p;
      }
    }

    LabelNode label = new LabelNode();
    method.instructions.insertBefore(instruction, label);

    return label;
  }

  /**
   * Insert label after instruction.
   *
   * @param instruction Instruction to insert label after.
   * @return Existing label, if there is already a label after the instruction, new label otherwise.
   */
  protected LabelNode insertLabelAfter(AbstractInsnNode instruction) {
    for (AbstractInsnNode n = instruction.getNext(); n != null && n.getOpcode() < 0; n = n.getNext()) {
      if (n instanceof LabelNode) {
        return (LabelNode) n;
      }
    }

    LabelNode label = new LabelNode();
    method.instructions.insert(instruction, label);

    return label;
  }
}
