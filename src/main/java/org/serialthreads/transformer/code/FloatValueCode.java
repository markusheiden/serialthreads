package org.serialthreads.transformer.code;

import org.ow2.asm.Type;
import org.ow2.asm.tree.AbstractInsnNode;
import org.ow2.asm.tree.InsnNode;
import org.ow2.asm.tree.LdcInsnNode;

import static org.ow2.asm.Opcodes.FALOAD;
import static org.ow2.asm.Opcodes.FASTORE;
import static org.ow2.asm.Opcodes.FCONST_0;
import static org.ow2.asm.Opcodes.FCONST_1;
import static org.ow2.asm.Opcodes.FLOAD;
import static org.ow2.asm.Opcodes.FRETURN;
import static org.ow2.asm.Opcodes.FSTORE;

/**
 * Code for float values.
 */
public class FloatValueCode extends AbstractValueCode
{
  public FloatValueCode()
  {
    super(Type.FLOAT_TYPE, "Float", FLOAD, FSTORE, FALOAD, FASTORE, FCONST_0, FRETURN);
  }

  public AbstractInsnNode push(Object value)
  {
    float f = (Float) value;

    if (f == 0F)
    {
      return new InsnNode(FCONST_0);
    }
    else if (f == 1F)
    {
      return new InsnNode(FCONST_1);
    }

    return new LdcInsnNode(f);
  }

  public boolean isResponsibleFor(Type type)
  {
    return type != null && Type.FLOAT == type.getSort();
  }
}
