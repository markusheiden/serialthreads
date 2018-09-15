package org.serialthreads.transformer.strategies;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;
import org.serialthreads.context.ThreadFinishedException;
import org.serialthreads.transformer.analyzer.ExtendedAnalyzer;
import org.serialthreads.transformer.analyzer.ExtendedFrame;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.CompactingStackCode;
import org.serialthreads.transformer.code.ThreadCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

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

  private static final String OBJECT_NAME = Type.getType(Object.class).getInternalName();
  private static final String STRING_DESC = Type.getType(String.class).getDescriptor();
  private static final String THREAD_FINISHED_EXCEPTION_NAME = Type.getType(ThreadFinishedException.class).getInternalName();

  protected static final String THREAD_IMPL_DESC = Type.getType(Stack.class).getDescriptor();
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
   * Parameter p used by this transformer.
   */
  protected final int param(int p) {
    return firstParam(method) + p;
  }

  /**
   * Local l used by this transformer.
   */
  protected final int local(int l) {
    return firstLocal(method) + l;
  }

  /**
   * Name local in the whole method.
   *
   * @param local Number of local.
   * @param desc Type descriptor.
   * @param variableName Name for local.
   */
  protected void nameLocal(int local, String desc, String variableName) {
    method.localVariables.add(
      new LocalVariableNode(variableName, desc, null, insertLabelAtStart(), insertLabelAtEnd(), local));
  }

  /**
   * Analyze a method to compute frames.
   * Extract all interruptible method calls.
   *
   * @throws AnalyzerException on invalid byte code.
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
   * @param instruction Instruction.
   * @param tag Tag.
   */
  private void tag(AbstractInsnNode instruction, String tag) {
    metaInfos.get(instruction).tags.add(tag);
  }

  /**
   * Create restore code dispatcher.
   *
   * @param localFrame Number of local containing the current frame.
   * @param restores Restore codes for each interruptible method call.
   * @param startIndex first method index, should be -1 for run(), 0 otherwise.
   * @return Generated restore code.
   */
  protected InsnList restoreCodeDispatcher(int localFrame, List<LabelNode> restores, int startIndex) {
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
    instructions.add(threadCode.pushMethod(localFrame));
    instructions.add(new TableSwitchInsnNode(startIndex, startIndex + restores.size() - 1, defaultLabel, restores.toArray(new LabelNode[0])));

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

  //
  // Stack frame code.
  //

  /**
   * Push method onto frame.
   *
   * @param localFrame
   *           Number of local containing the current frame .
   * @param position
   *           position of method call.
   * @return generated capture code.
   */
  protected InsnList setMethod(int localFrame, int position) {
    if (interruptibleMethodCalls.size() <= 1) {
      return new InsnList();
    }

    return threadCode.setMethod(localFrame, position);
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
   * @param localPreviousFrame
   *           Number of local containing the previous frame.
   * @return generated capture code.
   */
  protected InsnList setOwner(MethodInsnNode methodCall, MetaInfo metaInfo, boolean suppressOwner, int localPreviousFrame) {
    if (suppressOwner || isSelfCall(methodCall, metaInfo) || isStatic(methodCall)) {
      return new InsnList();
    }

    return threadCode.setOwner(localPreviousFrame);
  }

  /**
   * Restore owner.
   *
   * @param methodCall
   *           method call to process.
   * @param metaInfo
   *           Meta information about method call.
   * @param localFrame
   *           Number of local containing the current frame .
   * @return generated restore code.
   */
  protected InsnList pushOwner(MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame) {
    InsnList instructions = new InsnList();

    if (isSelfCall(methodCall, metaInfo)) {
      // self call: owner == this
      instructions.add(new VarInsnNode(ALOAD, 0));
    } else if (isNotStatic(methodCall)) {
      // get owner
      instructions.add(threadCode.pushOwner(localFrame));
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
   * Insert label before all instructions.
   *
   * @return Existing label, if there is already a label at the start of the method, new label otherwise.
   */
  protected LabelNode insertLabelAtStart() {
    AbstractInsnNode first = method.instructions.getFirst();
    LabelNode label = searchLabel(first, AbstractInsnNode::getNext);
    if (label == null) {
      label = new LabelNode();
      method.instructions.insertBefore(first, label);
    }

    return label;
  }

  /**
   * Insert label before instruction.
   *
   * @param instruction Instruction to insert label before.
   * @return Existing label, if there is already a label before the instruction, new label otherwise.
   */
  protected LabelNode insertLabelBefore(AbstractInsnNode instruction) {
    LabelNode label = searchLabel(instruction, AbstractInsnNode::getPrevious);
    if (label == null) {
      label = new LabelNode();
      method.instructions.insertBefore(instruction, label);
    }

    return label;
  }

  /**
   * Insert label after instruction.
   *
   * @param instruction Instruction to insert label after.
   * @return Existing label, if there is already a label after the instruction, new label otherwise.
   */
  protected LabelNode insertLabelAfter(AbstractInsnNode instruction) {
    LabelNode label = searchLabel(instruction, AbstractInsnNode::getNext);
    if (label == null) {
      label = new LabelNode();
      method.instructions.insert(instruction, label);
    }

    return label;
  }

  /**
   * Insert label after all instructions.
   *
   * @return Existing label, if there is already a label at the end of the method, new label otherwise.
   */
  protected LabelNode insertLabelAtEnd() {
    final AbstractInsnNode last = method.instructions.getLast();
    LabelNode label = searchLabel(last, AbstractInsnNode::getPrevious);
    if (label == null) {
      label = new LabelNode();
      method.instructions.insert(last, label);
    }

    return label;
  }

  /**
   * Search label node. Stops at first real instruction.
   *
   * @param start Instruction to start search at.
   * @param direction Direction of search.
   * @return Label, if there is a label before any real instruction, null otherwise.
   */
  private LabelNode searchLabel(AbstractInsnNode start, Function<AbstractInsnNode, AbstractInsnNode> direction) {
    for (AbstractInsnNode l = start; l != null && l.getOpcode() < 0; l = direction.apply(l)) {
      if (l instanceof LabelNode) {
        return (LabelNode) l;
      }
    }

    return null;
  }
}
