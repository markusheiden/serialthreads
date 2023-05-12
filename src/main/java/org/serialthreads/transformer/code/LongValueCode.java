package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.LONG;
import static org.objectweb.asm.Type.LONG_TYPE;

/**
 * Code for long values.
 */
public class LongValueCode extends AbstractValueCode {
  public LongValueCode() {
    super(LONG_TYPE, "Long", LLOAD, LSTORE, LALOAD, LASTORE, LCONST_0, LRETURN);
  }

  @Override
  public boolean isResponsibleFor(Type type) {
    return type != null && type.getSort() == LONG;
  }

  @Override
  public AbstractInsnNode push(Object value) {
    long l = (Long) value;

    if (l == 0L) {
      return new InsnNode(LCONST_0);
    } else if (l == 1L) {
      return new InsnNode(LCONST_1);
    }

    return new LdcInsnNode(l);
  }
}
