package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;

import static org.objectweb.asm.Opcodes.*;

/**
 * Value specific code generation.
 */
public abstract class AbstractValueCode implements IValueCode {
  /**
   * Internal name of the thread class.
   */
  protected static final String THREAD_IMPL_NAME = Type.getType(Stack.class).getInternalName();

  /**
   * Internal name of the frame class.
   */
  protected static final String FRAME_IMPL_NAME = Type.getType(StackFrame.class).getInternalName();

  /**
   * Type representing the the value class.
   */
  protected final Type type;

  /**
   * Base type of the value class.
   * For all objects and arrays the base class is Object.
   */
  protected final Type baseType;

  /**
   * Base name of value specific load/store methods at stack.
   */
  protected final String methodName;

  /**
   * Value specific load instruction.
   */
  protected final int load;

  /**
   * Value specific store instruction.
   */
  protected final int store;

  /**
   * Value specific array load instruction.
   */
  protected final int aload;

  /**
   * Value specific array store instruction.
   */
  protected final int astore;

  /**
   * Value specific instruction to push a zero/null onto the stack.
   */
  protected final int pushNull;

  /**
   * Value specific return instruction.
   */
  protected final int returnValue;

  /**
   * Constructor.
   *
   * @param type Type representing the the value class
   * @param methodName Base name of value specific load/store methods at stack
   * @param load Value specific load instruction
   * @param store Value specific store instruction
   * @param aload Value specific array load instruction
   * @param astore Value specific array store instruction
   * @param pushNull Value specific instruction to push a zero/null onto the stack
   * @param returnValue Value specific return instruction
   */
  public AbstractValueCode(Type type, String methodName, int load, int store, int aload, int astore, int pushNull, int returnValue) {
    this.type = type;
    this.baseType = type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY ? Type.getType(Object.class) : type;
    this.methodName = methodName;
    this.load = load;
    this.store = store;
    this.aload = aload;
    this.astore = astore;
    this.pushNull = pushNull;
    this.returnValue = returnValue;
  }

  @Override
  public InsnList getLocals(int localFrame) {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "local" + methodName + "s", "[" + type.getDescriptor()));
    return instructions;
  }

  @Override
  public InsnList pushLocalVariableFast(int local, int index, int localFrame) {
    assert index < StackFrame.FAST_FRAME_SIZE : "Precondition: index < StackFrame.FAST_FRAME_SIZE";

    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new VarInsnNode(load, local));
    instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "local" + methodName + index, type.getDescriptor()));
    return instructions;
  }

  @Override
  public InsnList pushLocalVariable(int local, int index) {
    InsnList instructions = new InsnList();
    instructions.add(IntValueCode.push(index));
    instructions.add(new VarInsnNode(load, local));
    instructions.add(new InsnNode(astore));
    return instructions;
  }

  @Override
  public InsnList pushReturnValue(int localThread) {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new InsnNode(SWAP));
    doPushReturnValueImpl(instructions);
    return instructions;
  }

  /**
   * Generate code to capture the return value into the thread.
   * Required objects on the stack: Thread, Value.
   *
   * @param instructions Instructions
   */
  protected final void doPushReturnValueImpl(InsnList instructions) {
    instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "return" + methodName, baseType.getDescriptor()));
    instructions.add(new InsnNode(RETURN));
  }

  @Override
  public InsnList popLocalVariableFast(int local, int index, int localFrame) {
    assert index < StackFrame.FAST_FRAME_SIZE : "Precondition: index < StackFrame.FAST_FRAME_SIZE";

    // TODO 2010-03-18 mh: clear reference in stack frame?
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "local" + methodName + index, type.getDescriptor()));
    instructions.add(cast());
    instructions.add(new VarInsnNode(store, local));
    instructions.add(clear("local" + methodName + index, localFrame));
    return instructions;
  }

  @Override
  public InsnList popLocalVariable(int local, int index) {
    InsnList instructions = new InsnList();
    instructions.add(beforePop());
    instructions.add(IntValueCode.push(index));
    instructions.add(new InsnNode(aload));
    instructions.add(cast());
    instructions.add(afterPop(local));
    instructions.add(new VarInsnNode(store, local));
    return instructions;
  }

  @Override
  public InsnList popReturnValue(int localThread) {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "return" + methodName, baseType.getDescriptor()));
    instructions.add(cast());
    return instructions;
  }

  @Override
  public InsnList getStacks(int localFrame) {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "stack" + methodName + "s", "[" + type.getDescriptor()));
    return instructions;
  }

  @Override
  public InsnList pushStack(int index, int localFrame) {
    InsnList instructions = new InsnList();
    if (index < StackFrame.FAST_FRAME_SIZE) {
      // for first stack elements use fast stack
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(new InsnNode(SWAP));
      instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "stack" + methodName + index, type.getDescriptor()));
    } else {
      // for too deep stack use "slow" storage in array
      instructions.add(getStacks(localFrame));
      instructions.add(new InsnNode(SWAP));
      instructions.add(IntValueCode.push(index - StackFrame.FAST_FRAME_SIZE));
      instructions.add(new InsnNode(SWAP));
      instructions.add(new InsnNode(astore));
    }

    return instructions;
  }

  @Override
  public InsnList popStack(int index, int localFrame) {
    InsnList instructions = new InsnList();
    if (index < StackFrame.FAST_FRAME_SIZE) {
      // for first stack elements use fast stack
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "stack" + methodName + index, type.getDescriptor()));
      instructions.add(cast());
      instructions.add(clear("stack" + methodName + index, localFrame));
    } else {
      // for too deep stack use "slow" storage in array
      instructions.add(getStacks(localFrame));
      instructions.add(beforePop());
      instructions.add(IntValueCode.push(index - StackFrame.FAST_FRAME_SIZE));
      instructions.add(new InsnNode(aload));
      instructions.add(cast());
      instructions.add(afterPop(index - StackFrame.FAST_FRAME_SIZE));
    }

    return instructions;
  }

  /**
   * Generate code to cast the topmost element on the stack to this type.
   *
   * @return Instructions
   */
  protected InsnList cast() {
    // overwrite, if needed
    return new InsnList();
  }

  /**
   * Generate code to clear a saved value from the stack frame to avoid memory leaks.
   *
   * @param name Name of stack frame field to clear
   * @param localFrame Local containing the frame
   * @return Instructions
   */
  protected InsnList clear(String name, int localFrame) {
    // overwrite, if needed
    return new InsnList();
  }

  /**
   * Add code directly before restoring a local or a stack element from a frame.
   *
   * @return Instructions
   */
  protected InsnList beforePop() {
    // overwrite, if needed
    return new InsnList();
  }

  /**
   * Add code directly after restoring a local or a stack element from a frame.
   *
   * @param i Index
   * @return Instructions
   */
  protected InsnList afterPop(int i) {
    // overwrite, if needed
    return new InsnList();
  }

  @Override
  public VarInsnNode load(int i) {
    return new VarInsnNode(load, i);
  }

  @Override
  public VarInsnNode store(int i) {
    return new VarInsnNode(store, i);
  }

  @Override
  public InsnList move(int from, int to) {
    InsnList instructions = new InsnList();
    if (from != to) {
      instructions.add(load(from));
      instructions.add(store(to));
    }
    return instructions;
  }

  @Override
  public InsnNode pop() {
    return new InsnNode(type.getSize() == 1 ? POP : POP2);
  }

  @Override
  public InsnNode pushNull() {
    return new InsnNode(pushNull);
  }

  @Override
  public InsnList returnNull() {
    InsnList instructions = new InsnList();
    instructions.add(pushNull());
    instructions.add(new InsnNode(returnValue));
    return instructions;
  }

  @Override
  public boolean isCompatibleWith(Type type) {
    assert type != null : "Precondition: type != null";

    return isResponsibleFor(type);
  }
}
