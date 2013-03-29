package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * Code for double values.
 */
public class DoubleValueCode extends AbstractValueCode {
  public DoubleValueCode() {
    super(Type.DOUBLE_TYPE, "Double", DLOAD, DSTORE, DALOAD, DASTORE, DCONST_0, DRETURN);
  }

  @Override
  public AbstractInsnNode push(Object value) {
    double d = (Double) value;

    if (d == 0D) {
      return new InsnNode(DCONST_0);
    } else if (d == 1D) {
      return new InsnNode(DCONST_1);
    }

    return new LdcInsnNode(d);
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
    return type != null && Type.DOUBLE == type.getSort();
  }
}
