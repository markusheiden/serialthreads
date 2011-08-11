package org.serialthreads.transformer.code;

import org.ow2.asm.Type;
import org.ow2.asm.tree.AbstractInsnNode;
import org.ow2.asm.tree.InsnNode;
import org.ow2.asm.tree.IntInsnNode;
import org.ow2.asm.tree.LdcInsnNode;

import static org.ow2.asm.Opcodes.BIPUSH;
import static org.ow2.asm.Opcodes.IALOAD;
import static org.ow2.asm.Opcodes.IASTORE;
import static org.ow2.asm.Opcodes.ICONST_0;
import static org.ow2.asm.Opcodes.ICONST_1;
import static org.ow2.asm.Opcodes.ICONST_2;
import static org.ow2.asm.Opcodes.ICONST_3;
import static org.ow2.asm.Opcodes.ICONST_4;
import static org.ow2.asm.Opcodes.ICONST_5;
import static org.ow2.asm.Opcodes.ICONST_M1;
import static org.ow2.asm.Opcodes.ILOAD;
import static org.ow2.asm.Opcodes.IRETURN;
import static org.ow2.asm.Opcodes.ISTORE;
import static org.ow2.asm.Opcodes.SIPUSH;

/**
 * Code for int values (and boolean, byte, char and short).
 */
public class IntValueCode extends AbstractValueCode
{
  public IntValueCode()
  {
    super(Type.INT_TYPE, "Int", ILOAD, ISTORE, IALOAD, IASTORE, ICONST_0, IRETURN);
  }

  public AbstractInsnNode push(Object value)
  {
    int i = (Integer) value;

    return push(i);
  }

  public static AbstractInsnNode push(int i)
  {
    switch (i)
    {
      case -1:
        return new InsnNode(ICONST_M1);
      case 0:
        return new InsnNode(ICONST_0);
      case 1:
        return new InsnNode(ICONST_1);
      case 2:
        return new InsnNode(ICONST_2);
      case 3:
        return new InsnNode(ICONST_3);
      case 4:
        return new InsnNode(ICONST_4);
      case 5:
        return new InsnNode(ICONST_5);
      default:
      {
        if (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE)
        {
          return new IntInsnNode(BIPUSH, i);
        }
        else if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE)
        {
          return new IntInsnNode(SIPUSH, i);
        }
        else
        {
          return new LdcInsnNode(i);
        }
      }
    }
  }

  public boolean isResponsibleFor(Type type)
  {
    return
      type != null &&
        (Type.BOOLEAN == type.getSort() ||
          Type.CHAR == type.getSort() ||
          Type.BYTE == type.getSort() ||
          Type.SHORT == type.getSort() ||
          Type.INT == type.getSort());
  }
}
