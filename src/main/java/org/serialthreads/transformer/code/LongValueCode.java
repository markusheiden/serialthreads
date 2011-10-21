package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import static org.objectweb.asm.Opcodes.LALOAD;
import static org.objectweb.asm.Opcodes.LASTORE;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.LCONST_1;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.LSTORE;

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
