package org.serialthreads.transformer.code;

import org.ow2.asm.Type;
import org.ow2.asm.tree.AbstractInsnNode;
import org.ow2.asm.tree.FieldInsnNode;
import org.ow2.asm.tree.InsnList;
import org.ow2.asm.tree.InsnNode;
import org.ow2.asm.tree.LdcInsnNode;
import org.ow2.asm.tree.TypeInsnNode;
import org.ow2.asm.tree.VarInsnNode;

import static org.ow2.asm.Opcodes.AALOAD;
import static org.ow2.asm.Opcodes.AASTORE;
import static org.ow2.asm.Opcodes.ACONST_NULL;
import static org.ow2.asm.Opcodes.ALOAD;
import static org.ow2.asm.Opcodes.ARETURN;
import static org.ow2.asm.Opcodes.ASTORE;
import static org.ow2.asm.Opcodes.CHECKCAST;
import static org.ow2.asm.Opcodes.DUP;
import static org.ow2.asm.Opcodes.PUTFIELD;
import static org.ow2.asm.Opcodes.SWAP;

/**
 * Code for object values.
 */
public class ReferenceValueCode extends AbstractValueCode
{
  private final Type concreteType;

  public ReferenceValueCode(Type type)
  {
    super(Type.getType(Object.class), "Object", ALOAD, ASTORE, AALOAD, AASTORE, ACONST_NULL, ARETURN);

    this.concreteType = type;
  }

  @Override
  protected InsnList cast()
  {
    InsnList instructions = new InsnList();
    instructions.add(new TypeInsnNode(CHECKCAST, concreteType.getInternalName()));

    return instructions;
  }

  // TODO 2009-10-11 mh: remove deletion of reference for performance reasons?

  @Override
  protected InsnList clear(String name, int localFrame)
  {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(pushNull());
    instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, name, type.getDescriptor()));

    return instructions;
  }

  @Override
  protected InsnList beforePop()
  {
    InsnList instructions = new InsnList();
    instructions.add(new InsnNode(DUP));

    return instructions;
  }

  @Override
  protected InsnList afterPop(int i)
  {
    InsnList instructions = new InsnList();
    instructions.add(new InsnNode(SWAP));
    instructions.add(IntValueCode.push(i));
    instructions.add(pushNull());
    instructions.add(new InsnNode(astore));

    return instructions;
  }

  public AbstractInsnNode push(Object value)
  {
    if (value == null)
    {
      return new InsnNode(ACONST_NULL);
    }

    return new LdcInsnNode(value);
  }

  public boolean isCompatibleWith(Type type)
  {
    assert type != null : "Precondition: type != null2";

    return super.isCompatibleWith(type) && type.getInternalName().equals(type.getInternalName());
  }

  @Override
  public boolean isResponsibleFor(Type type)
  {
    return type != null && (Type.OBJECT == type.getSort() || Type.ARRAY == type.getSort());
  }
}
