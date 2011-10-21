package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import static org.objectweb.asm.Opcodes.FALOAD;
import static org.objectweb.asm.Opcodes.FASTORE;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.FCONST_1;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.FSTORE;

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
