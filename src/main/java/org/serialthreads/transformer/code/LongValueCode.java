package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * Code for long values.
 */
public class LongValueCode extends AbstractValueCode {
  public LongValueCode() {
    super(Type.LONG_TYPE, "Long", LLOAD, LSTORE, LALOAD, LASTORE, LCONST_0, LRETURN);
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

  @Override
  public InsnList pushReturnValue(int localFrame) {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new InsnNode(DUP_X2));
    instructions.add(new InsnNode(POP));
    instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "return" + methodName, baseType.getDescriptor()));

    instructions.add(new InsnNode(RETURN));
    return instructions;
  }

  @Override
  public boolean isResponsibleFor(Type type) {
    return type != null && Type.LONG == type.getSort();
  }
}
