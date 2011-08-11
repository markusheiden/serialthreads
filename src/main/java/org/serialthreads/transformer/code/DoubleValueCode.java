package org.serialthreads.transformer.code;

import org.ow2.asm.Type;
import org.ow2.asm.tree.AbstractInsnNode;
import org.ow2.asm.tree.InsnNode;
import org.ow2.asm.tree.LdcInsnNode;

import static org.ow2.asm.Opcodes.DALOAD;
import static org.ow2.asm.Opcodes.DASTORE;
import static org.ow2.asm.Opcodes.DCONST_0;
import static org.ow2.asm.Opcodes.DCONST_1;
import static org.ow2.asm.Opcodes.DLOAD;
import static org.ow2.asm.Opcodes.DRETURN;
import static org.ow2.asm.Opcodes.DSTORE;

/**
 * Code for double values.
 */
public class DoubleValueCode extends AbstractValueCode
{
  public DoubleValueCode()
  {
    super(Type.DOUBLE_TYPE, "Double", DLOAD, DSTORE, DALOAD, DASTORE, DCONST_0, DRETURN);
  }

  public AbstractInsnNode push(Object value)
  {
    double d = (Double) value;

    if (d == 0D)
    {
      return new InsnNode(DCONST_0);
    }
    else if (d == 1D)
    {
      return new InsnNode(DCONST_1);
    }

    return new LdcInsnNode(d);
  }

  public boolean isResponsibleFor(Type type)
  {
    return type != null && Type.DOUBLE == type.getSort();
  }
}
