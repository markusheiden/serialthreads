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
  public InsnList pushReturnValue(int localPreviousFrame) {
    InsnList instructions = new InsnList();
    // Put previousFrame before return value (2 words) onto stack.
    instructions.add(new VarInsnNode(ALOAD, localPreviousFrame));
    instructions.add(new InsnNode(DUP_X2));
    // Remove duplicated previousFrame from top of stack.
    instructions.add(new InsnNode(POP));
    doPushReturnValueImpl(instructions);
    return instructions;
  }

  @Deprecated // TODO 2018-02-04 markus: Remove ASAP, if storing of return values in frames has been fixed.
  @Override
  public InsnList pushReturnValueStack(int localThread) {
    InsnList instructions = new InsnList();
    // Put previousFrame before return value (2 words) onto stack.
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new InsnNode(DUP_X2));
    // Remove duplicated previousFrame from top of stack.
    instructions.add(new InsnNode(POP));
    doPushReturnValueImpl(instructions);
    return instructions;
  }

  @Override
  public boolean isResponsibleFor(Type type) {
    return type != null && Type.DOUBLE == type.getSort();
  }
}
