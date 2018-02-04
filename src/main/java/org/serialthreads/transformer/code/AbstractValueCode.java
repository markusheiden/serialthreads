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

  //
  // Stack.
  //

  /**
   * Generate code to get the array for stack elements of the frame.
   *
   * @param localFrame
   *           local with frame.
   */
  protected InsnList getStacks(int localFrame) {
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
      if (clear) {
        instructions.add(new VarInsnNode(ALOAD, localFrame));
        instructions.add(pushNull());
        instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "stack" + methodName + index, type.getDescriptor()));
      }
    } else {
      // for too deep stack use "slow" storage in array
      instructions.add(getStacks(localFrame));
      if (clear) {
        instructions.add(new InsnNode(DUP));
      }
      instructions.add(IntValueCode.push(index - StackFrame.FAST_FRAME_SIZE));
      instructions.add(new InsnNode(aload));
      instructions.add(cast());
      if (clear) {
        instructions.add(new InsnNode(SWAP));
        instructions.add(IntValueCode.push(index - StackFrame.FAST_FRAME_SIZE));
        instructions.add(pushNull());
        instructions.add(new InsnNode(astore));
      }
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
  protected InsnList getLocals(int localFrame) {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "local" + methodName + "s", "[" + type.getDescriptor()));
    return instructions;
  }

  @Override
  public InsnList pushLocal(int local, int index, boolean more, int localFrame) {
    InsnList instructions = new InsnList();
    if (index < StackFrame.FAST_FRAME_SIZE) {
      // for first locals use fast stack
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(new VarInsnNode(load, local));
      instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "local" + methodName + index, type.getDescriptor()));
    } else {
      // for too high locals use "slow" storage in (dynamic) array
      if (index == StackFrame.FAST_FRAME_SIZE) {
        instructions.add(getLocals(localFrame));
      }
      if (more) {
        instructions.add(new InsnNode(DUP));
      }
      instructions.add(IntValueCode.push(index- StackFrame.FAST_FRAME_SIZE));
      instructions.add(new VarInsnNode(load, local));
      instructions.add(new InsnNode(astore));
    }
    return instructions;
  }

  @Override
  public InsnList popLocal(int local, int index, boolean more, int localFrame) {
    InsnList instructions = new InsnList();
    if (index < StackFrame.FAST_FRAME_SIZE) {
      // for first locals use fast stack
      // TODO 2010-03-18 mh: clear reference in stack frame?
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "local" + methodName + index, type.getDescriptor()));
      instructions.add(cast());
      instructions.add(new VarInsnNode(store, local));
      if (clear) {
        instructions.add(new VarInsnNode(ALOAD, localFrame));
        instructions.add(pushNull());
        instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "local" + methodName + index, type.getDescriptor()));
      }
    } else {
      // for too high locals use "slow" storage in (dynamic) array
      if (index == StackFrame.FAST_FRAME_SIZE) {
        instructions.add(getLocals(localFrame));
      }
      if (more) {
        instructions.add(new InsnNode(DUP));
      }
      if (clear) {
        instructions.add(new InsnNode(DUP));
      }
      instructions.add(IntValueCode.push(index - StackFrame.FAST_FRAME_SIZE));
      instructions.add(new InsnNode(aload));
      instructions.add(cast());
      if (clear) {
        instructions.add(new InsnNode(SWAP));
        instructions.add(IntValueCode.push(index - StackFrame.FAST_FRAME_SIZE));
        instructions.add(pushNull());
        instructions.add(new InsnNode(astore));
      }
      instructions.add(new VarInsnNode(store, local));
    }
    return instructions;
  }

  //
  // Return values.
  //

  @Override
  public InsnList popReturnValue(int localFrame) {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "return" + methodName, baseType.getDescriptor()));
    instructions.add(cast());
    if (clear) {
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(pushNull());
      instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "return" + methodName, type.getDescriptor()));
    }
    return instructions;
  }

  @Deprecated // TODO 2018-02-04 markus: Remove ASAP, if storing of return values in frames has been fixed.
  @Override
  public InsnList popReturnValueStack(int localThread) {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "return" + methodName, baseType.getDescriptor()));
    instructions.add(cast());
    if (clear) {
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(pushNull());
      instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "return" + methodName, type.getDescriptor()));
    }
    return instructions;
  }

  @Override
  public InsnList pushReturnValue(int localPreviousFrame) {
    InsnList instructions = new InsnList();
    if (size == 1) {
      // Put previousFrame before return value onto stack.
      instructions.add(new VarInsnNode(ALOAD, localPreviousFrame));
      instructions.add(new InsnNode(SWAP));
    } else {
      // Put previousFrame before return value (2 words) onto stack.
      instructions.add(new VarInsnNode(ALOAD, localPreviousFrame));
      instructions.add(new InsnNode(DUP_X2));
      // Remove duplicated previousFrame from top of stack.
      instructions.add(new InsnNode(POP));
    }
    instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "return" + methodName, baseType.getDescriptor()));
    return instructions;
  }

  @Deprecated // TODO 2018-02-04 markus: Remove ASAP, if storing of return values in frames has been fixed.
  @Override
  public InsnList pushReturnValueStack(int localThread) {
    InsnList instructions = new InsnList();
    if (size == 1) {
      // Put previousFrame before return value onto stack.
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(new InsnNode(SWAP));
    } else {
      // Put previousFrame before return value (2 words) onto stack.
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(new InsnNode(DUP_X2));
      // Remove duplicated previousFrame from top of stack.
      instructions.add(new InsnNode(POP));
    }
    instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "return" + methodName, baseType.getDescriptor()));
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
