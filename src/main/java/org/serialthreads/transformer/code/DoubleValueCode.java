package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.DOUBLE;
import static org.objectweb.asm.Type.DOUBLE_TYPE;

/**
 * Code for double values.
 */
public class DoubleValueCode extends AbstractValueCode {
  public DoubleValueCode() {
    super(DOUBLE_TYPE, "Double", DLOAD, DSTORE, DALOAD, DASTORE, DCONST_0, DRETURN);
  }

  @Override
  public boolean isResponsibleFor(Type type) {
    return type != null && type.getSort() == DOUBLE;
  }

  @Override
  public AbstractInsnNode push(Object value) {
    double d = (Double) value;

    if (d == 0D) {
      return new InsnNode(DCONST_0);
    } else if (d == 1D) {
      return new InsnNode(DCONST_1);
    }

    return new LdcInsnNode(d);
  }
}
