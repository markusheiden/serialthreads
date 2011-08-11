package org.serialthreads.transformer.code;

import org.ow2.asm.Type;
import org.ow2.asm.tree.AbstractInsnNode;
import org.ow2.asm.tree.InsnNode;
import org.ow2.asm.tree.LdcInsnNode;

import static org.ow2.asm.Opcodes.LALOAD;
import static org.ow2.asm.Opcodes.LASTORE;
import static org.ow2.asm.Opcodes.LCONST_0;
import static org.ow2.asm.Opcodes.LCONST_1;
import static org.ow2.asm.Opcodes.LLOAD;
import static org.ow2.asm.Opcodes.LRETURN;
import static org.ow2.asm.Opcodes.LSTORE;

/**
 * Code for long values.
 */
public class LongValueCode extends AbstractValueCode
{
  public LongValueCode()
  {
    super(Type.LONG_TYPE, "Long", LLOAD, LSTORE, LALOAD, LASTORE, LCONST_0, LRETURN);
  }

  public AbstractInsnNode push(Object value)
  {
    long l = (Long) value;

    if (l == 0L)
    {
      return new InsnNode(LCONST_0);
    }
    else if (l == 1L)
    {
      return new InsnNode(LCONST_1);
    }

    return new LdcInsnNode(l);
  }

  public boolean isResponsibleFor(Type type)
  {
    return type != null && Type.LONG == type.getSort();
  }
}
