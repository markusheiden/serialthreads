package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Type.ARRAY;
import static org.objectweb.asm.Type.OBJECT;

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
  public boolean isCompatibleWith(Type type) {
    assert type != null : "Precondition: type != null.";

    return super.isCompatibleWith(type) && type.getInternalName().equals(type.getInternalName());
  }

  @Override
  public boolean isResponsibleFor(Type type) {
    if (type == null) {
      return false;
    }
    return switch (type.getSort()) {
      case OBJECT, ARRAY -> true;
      default -> false;
    };
  }

  @Override
  protected InsnList cast() {
    var instructions = new InsnList();
    instructions.add(new TypeInsnNode(CHECKCAST, concreteType.getInternalName()));

    return instructions;
  }

  @Override
  public AbstractInsnNode push(Object value) {
    if (value == null) {
      return new InsnNode(ACONST_NULL);
    }

    return new LdcInsnNode(value);
  }
}
