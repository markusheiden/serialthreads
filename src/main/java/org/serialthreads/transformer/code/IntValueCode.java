package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.BOOLEAN;
import static org.objectweb.asm.Type.BYTE;
import static org.objectweb.asm.Type.CHAR;
import static org.objectweb.asm.Type.INT;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.SHORT;

/**
 * Code for int values (and boolean, byte, char and short).
 */
public class IntValueCode extends AbstractValueCode {
  public IntValueCode() {
    super(INT_TYPE, "Int", ILOAD, ISTORE, IALOAD, IASTORE, ICONST_0, IRETURN);
  }

  @Override
  public boolean isResponsibleFor(Type type) {
    return
      type != null &&
        (type.getSort() == BOOLEAN ||
         type.getSort() == CHAR ||
         type.getSort() == BYTE ||
         type.getSort() == SHORT ||
         type.getSort() == INT);
  }

  @Override
  public AbstractInsnNode push(Object value) {
    int i = (Integer) value;

    return push(i);
  }

  public static AbstractInsnNode push(int i) {
    return switch (i) {
      case -1 ->
              new InsnNode(ICONST_M1);
      case 0 ->
              new InsnNode(ICONST_0);
      case 1 ->
              new InsnNode(ICONST_1);
      case 2 ->
              new InsnNode(ICONST_2);
      case 3 ->
              new InsnNode(ICONST_3);
      case 4 ->
              new InsnNode(ICONST_4);
      case 5 ->
              new InsnNode(ICONST_5);
      default -> {
        if (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
          yield new IntInsnNode(BIPUSH, i);
        } else if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
          yield new IntInsnNode(SIPUSH, i);
        } else {
          yield new LdcInsnNode(i);
        }
      }
    };
  }
}
