package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.DUP_X2;
import static org.objectweb.asm.Opcodes.LALOAD;
import static org.objectweb.asm.Opcodes.LASTORE;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.LCONST_1;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.LSTORE;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;

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
    return type != null && Type.LONG == type.getSort();
  }
}
