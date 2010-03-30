package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.serialthreads.context.StackFrame;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.SWAP;

/**
 * Value specific code generation.
 */
public abstract class AbstractValueCode implements IValueCode
{
  protected static final String FRAME_IMPL_NAME = Type.getType(StackFrame.class).getInternalName();

  protected final Type type;
  protected final String methodName;
  protected final int load;
  protected final int store;
  protected final int aload;
  protected final int astore;
  protected final int pushNull;
  protected final int returnValue;

  public AbstractValueCode(Type type, String methodName, int load, int store, int aload, int astore, int pushNull, int returnValue)
  {
    this.type = type;
    this.methodName = methodName;
    this.load = load;
    this.store = store;
    this.aload = aload;
    this.astore = astore;
    this.pushNull = pushNull;
    this.returnValue = returnValue;
  }

  @Override
  public InsnList getLocals(int localFrame)
  {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "local" + methodName + "s", "[" + type.getDescriptor()));
    return instructions;
  }

  @Override
  public InsnList pushLocalVariableFast(int local, int index, int localFrame)
  {
    assert index < StackFrame.FAST_FRAME_SIZE : "Precondition: index < StackFrame.FAST_FRAME_SIZE";

    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new VarInsnNode(load, local));
    instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "local" + methodName + index, type.getDescriptor()));
    return instructions;
  }

  @Override
  public InsnList pushLocalVariable(int local, int index)
  {
    InsnList instructions = new InsnList();
    instructions.add(IntValueCode.push(index));
    instructions.add(new VarInsnNode(load, local));
    instructions.add(new InsnNode(astore));
    return instructions;
  }

  @Override
  public InsnList popLocalVariableFast(int local, int index, int localFrame)
  {
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
  public InsnList popLocalVariable(int local, int index)
  {
    InsnList instructions = new InsnList();
    instructions.add(beforePop());
    instructions.add(IntValueCode.push(index));
    instructions.add(new InsnNode(aload));
    instructions.add(cast());
    instructions.add(afterPop(local));
    instructions.add(new VarInsnNode(store, local));
    return instructions;
  }

  public InsnList getStacks(int localFrame)
  {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "stack" + methodName + "s", "[" + type.getDescriptor()));
    return instructions;
  }

  @Override
  public InsnList pushStack(int index, int localFrame)
  {
    InsnList instructions = new InsnList();
    if (index < StackFrame.FAST_FRAME_SIZE)
    {
      // for first stack elements use fast stack
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(new InsnNode(SWAP));
      instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "stack" + methodName + index, type.getDescriptor()));
    }
    else
    {
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
  public InsnList popStack(int index, int localFrame)
  {
    InsnList instructions = new InsnList();
    if (index < StackFrame.FAST_FRAME_SIZE)
    {
      // for first stack elements use fast stack
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "stack" + methodName + index, type.getDescriptor()));
      instructions.add(cast());
      instructions.add(clear("stack" + methodName + index, localFrame));
    }
    else
    {
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

  protected InsnList cast()
  {
    // overwrite, if needed
    return new InsnList();
  }

  protected InsnList clear(String name, int localFrame)
  {
    // overwrite, if needed
    return new InsnList();
  }

  protected InsnList beforePop()
  {
    // overwrite, if needed
    return new InsnList();
  }

  protected InsnList afterPop(int i)
  {
    // overwrite, if needed
    return new InsnList();
  }

  @Override
  public VarInsnNode load(int i)
  {
    return new VarInsnNode(load, i);
  }

  @Override
  public VarInsnNode store(int i)
  {
    return new VarInsnNode(store, i);
  }

  @Override
  public InsnNode pop()
  {
    return new InsnNode(type.getSize() == 1 ? POP : POP2);
  }

  @Override
  public InsnNode pushNull()
  {
    return new InsnNode(pushNull);
  }

  @Override
  public InsnList returnNull()
  {
    InsnList instructions = new InsnList();
    instructions.add(pushNull());
    instructions.add(new InsnNode(returnValue));
    return instructions;
  }

  @Override
  public boolean isCompatibleWith(Type type)
  {
    assert type != null : "Precondition: type != null";

    return isResponsibleFor(type);
  }
}
