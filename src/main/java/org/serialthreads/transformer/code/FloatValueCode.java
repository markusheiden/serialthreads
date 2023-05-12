package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.FLOAT;
import static org.objectweb.asm.Type.FLOAT_TYPE;

/**
 * Code for float values.
 */
public class FloatValueCode extends AbstractValueCode {
  public FloatValueCode() {
    super(FLOAT_TYPE, "Float", FLOAD, FSTORE, FALOAD, FASTORE, FCONST_0, FRETURN);
  }

  @Override
  public boolean isResponsibleFor(Type type) {
    return type != null && type.getSort() == FLOAT;
  }

  @Override
  public AbstractInsnNode push(Object value) {
    float f = (Float) value;

    if (f == 0F) {
      return new InsnNode(FCONST_0);
    } else if (f == 1F) {
      return new InsnNode(FCONST_1);
    } else if (f == 2F) {
      return new InsnNode(FCONST_2);
    }

    return new LdcInsnNode(f);
  }
}
