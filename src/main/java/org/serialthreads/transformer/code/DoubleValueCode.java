package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.DALOAD;
import static org.objectweb.asm.Opcodes.DASTORE;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.DCONST_1;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Opcodes.DUP_X2;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;

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

  @Override
  public InsnList pushReturnValue(int localFrame)
  {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new InsnNode(DUP_X2));
    instructions.add(new InsnNode(POP));
    instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "return" + methodName, baseType.getDescriptor()));

    instructions.add(new InsnNode(RETURN));
    return instructions;
  }

  public boolean isResponsibleFor(Type type)
  {
    return type != null && Type.DOUBLE == type.getSort();
  }
}
