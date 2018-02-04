package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * Code for object values.
 */
public class ReferenceValueCode extends AbstractValueCode {
  private final Type concreteType;

  public ReferenceValueCode(Type type) {
    super(Type.getType(Object.class), "Object", ALOAD, ASTORE, AALOAD, AASTORE, ACONST_NULL, ARETURN);

    this.concreteType = type;
  }

  @Override
  protected InsnList cast() {
    InsnList instructions = new InsnList();
    instructions.add(new TypeInsnNode(CHECKCAST, concreteType.getInternalName()));

    return instructions;
  }

  @Override
  protected InsnList beforePop() {
    InsnList instructions = new InsnList();
    instructions.add(new InsnNode(DUP));

    return instructions;
  }

  @Override
  protected InsnList afterPop(int i) {
    InsnList instructions = new InsnList();
    instructions.add(new InsnNode(SWAP));
    instructions.add(IntValueCode.push(i));
    instructions.add(pushNull());
    instructions.add(new InsnNode(astore));

    return instructions;
  }

  @Override
  public AbstractInsnNode push(Object value) {
    if (value == null) {
      return new InsnNode(ACONST_NULL);
    }

    return new LdcInsnNode(value);
  }

  @Override
  public boolean isCompatibleWith(Type type) {
    assert type != null : "Precondition: type != null.";

    return super.isCompatibleWith(type) && type.getInternalName().equals(type.getInternalName());
  }

  @Override
  public boolean isResponsibleFor(Type type) {
    return type != null && (Type.OBJECT == type.getSort() || Type.ARRAY == type.getSort());
  }
}
