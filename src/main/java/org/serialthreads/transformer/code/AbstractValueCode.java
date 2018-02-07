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
   * Number of words of an element.
   */
  protected final int size;

  /**
   * Clear restore elements from stack frame?
   */
  protected final boolean clear;

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
    if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
      this.baseType = Type.getType(Object.class);
      this.size = 1;
      this.clear = true;
    } else {
      this.baseType = type;
      this.size = type.getSize();
      this.clear = false;
    }
    this.methodName = methodName;
    this.load = load;
    this.store = store;
    this.aload = aload;
    this.astore = astore;
    this.pushNull = pushNull;
    this.returnValue = returnValue;
  }

  @Override
  public boolean isCompatibleWith(Type type) {
    assert type != null : "Precondition: type != null";

    return isResponsibleFor(type);
  }

  //
  // Stack.
  //

  /**
   * Generate code to get the array for stack elements of the frame.
   *
   * @param localFrame
   *           local with frame.
   */
  private InsnList getStacks(int localFrame) {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "stack" + methodName + "s", "[" + type.getDescriptor()));
    return instructions;
  }

  @Override
  public InsnList pushStack(int index, int localFrame) {
    if (index < StackFrame.FAST_FRAME_SIZE) {
      // For first stack elements use fast stack.
      return pushStackFast(index, localFrame);
    } else {
      // For too deep stack use "slow" storage in a dynamic array.
      return pushStackSlow(index - StackFrame.FAST_FRAME_SIZE, localFrame);
    }
  }

  private InsnList pushStackFast(int index, int localFrame) {
    InsnList instructions = new InsnList();
    // frame.stackXXX0 = stack;
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new InsnNode(SWAP));
    instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "stack" + methodName + index, type.getDescriptor()));
    return instructions;
  }

  private InsnList pushStackSlow(int index, int localFrame) {
    InsnList instructions = new InsnList();
    // frame.stackXXXs[index] = stack;
    instructions.add(getStacks(localFrame));
    instructions.add(new InsnNode(SWAP));
    instructions.add(IntValueCode.push(index));
    instructions.add(new InsnNode(SWAP));
    instructions.add(new InsnNode(astore));
    return instructions;
  }

  @Override
  public InsnList popStack(int index, int localFrame) {
    if (index < StackFrame.FAST_FRAME_SIZE) {
      // For first stack elements use fast stack.
      return popStackFast(index, localFrame);
    } else {
      // For too deep stack use "slow" storage in a dynamic array.
      return popStackSlow(index - StackFrame.FAST_FRAME_SIZE, localFrame);
    }
  }

  private InsnList popStackFast(int index, int localFrame) {
    InsnList instructions = new InsnList();
    // stack = frame.stackXXX0;
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "stack" + methodName + index, type.getDescriptor()));
    instructions.add(cast());
    if (clear) {
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(pushNull());
      instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "stack" + methodName + index, type.getDescriptor()));
    }
    return instructions;
  }

  private InsnList popStackSlow(int index, int localFrame) {
    InsnList instructions = new InsnList();
    // stack = frame.stackXXXs[index];
    instructions.add(getStacks(localFrame));
    if (clear) {
      instructions.add(new InsnNode(DUP));
    }
    instructions.add(IntValueCode.push(index));
    instructions.add(new InsnNode(aload));
    instructions.add(cast());
    if (clear) {
      instructions.add(new InsnNode(SWAP));
      instructions.add(IntValueCode.push(index));
      instructions.add(pushNull());
      instructions.add(new InsnNode(astore));
    }
    return instructions;
  }

  //
  // Locals.
  //

  /**
   * Generate code to get the array for locals of the frame.
   *
   * @param localFrame
   *           local with frame.
   */
  private InsnList getLocals(int localFrame) {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "local" + methodName + "s", "[" + type.getDescriptor()));
    return instructions;
  }

  @Override
  public InsnList pushLocal(int local, int index, boolean more, int localFrame) {
    if (index < StackFrame.FAST_FRAME_SIZE) {
      // For first locals use fast stack.
      return pushLocalFast(local, index, localFrame);
    } else {
      // For too high locals use "slow" storage in a dynamic array.
      return pushLocalSlow(local, index - StackFrame.FAST_FRAME_SIZE, more, localFrame);
    }
  }

  private InsnList pushLocalFast(int local, int index, int localFrame) {
    InsnList instructions = new InsnList();
    // frame.localXXX0 = local0;
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new VarInsnNode(load, local));
    instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "local" + methodName + index, type.getDescriptor()));
    return instructions;
  }

  private InsnList pushLocalSlow(int local, int index, boolean more, int localFrame) {
    InsnList instructions = new InsnList();
    // frame.localXXXs[index] = local0;
    if (index == 0) {
      instructions.add(getLocals(localFrame));
    }
    if (more) {
      instructions.add(new InsnNode(DUP));
    }
    instructions.add(IntValueCode.push(index));
    instructions.add(new VarInsnNode(load, local));
    instructions.add(new InsnNode(astore));
    return instructions;
  }

  @Override
  public InsnList popLocal(int local, int index, boolean more, int localFrame) {
    if (index < StackFrame.FAST_FRAME_SIZE) {
      // For first locals use fast stack.
      return popLocalFast(local, index, localFrame);
    } else {
      // For too high locals use "slow" storage in a dynamic array.
      return popLocalSlow(local, index - StackFrame.FAST_FRAME_SIZE, more, localFrame);
    }
  }

  private InsnList popLocalFast(int local, int index, int localFrame) {
    InsnList instructions = new InsnList();
    // local0 = frame.localXXX0;
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "local" + methodName + index, type.getDescriptor()));
    instructions.add(cast());
    instructions.add(new VarInsnNode(store, local));
    if (clear) {
      // frame.localXXX0 = null;
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(pushNull());
      instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "local" + methodName + index, type.getDescriptor()));
    }
    return instructions;
  }

  private InsnList popLocalSlow(int local, int index, boolean more, int localFrame) {
    InsnList instructions = new InsnList();
    // local0 = frame.localXXXs[index];
    if (index == 0) {
      instructions.add(getLocals(localFrame));
    }
    if (more) {
      instructions.add(new InsnNode(DUP));
    }
    if (clear) {
      instructions.add(new InsnNode(DUP));
    }
    instructions.add(IntValueCode.push(index));
    instructions.add(new InsnNode(aload));
    instructions.add(cast());
    instructions.add(new VarInsnNode(store, local));
    if (clear) {
      // frame.localXXXs[index] = null;
      instructions.add(IntValueCode.push(index));
      instructions.add(pushNull());
      instructions.add(new InsnNode(astore));
    }
    return instructions;
  }

  //
  // Return values.
  //

  @Override
  public InsnList pushReturnValue(int localThread) {
    InsnList instructions = new InsnList();
    // thread.returnXXX = stack;
    if (size == 1) {
      // Put thread before return value onto stack.
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(new InsnNode(SWAP));
    } else {
      // Put thread before return value (2 words) onto stack.
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(new InsnNode(DUP_X2));
      // Remove duplicated previousFrame from top of stack.
      instructions.add(new InsnNode(POP));
    }
    instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "return" + methodName, baseType.getDescriptor()));
    return instructions;
  }

  @Override
  public InsnList popReturnValue(int localThread) {
    InsnList instructions = new InsnList();
    // stack = thread.returnXXX;
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "return" + methodName, baseType.getDescriptor()));
    instructions.add(cast());
    if (clear) {
      // thread.returnXXX = null;
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(pushNull());
      instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "return" + methodName, type.getDescriptor()));
    }
    return instructions;
  }

  //
  // Restore support.
  //

  /**
   * Generate code to cast the topmost element on the stack to this type.
   *
   * @return Instructions
   */
  protected InsnList cast() {
    // overwrite, if needed
    return new InsnList();
  }

  //
  // Instructions.
  //

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
    return new InsnNode(size == 1 ? POP : POP2);
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
}
