package org.serialthreads.transformer.analyzer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import static org.serialthreads.transformer.analyzer.ExtendedValue.*;

/**
 * Frame for extended analyzer.
 * Supports detection of stack elements that have the same value as a local.
 */
public class ExtendedFrame extends Frame<BasicValue> {
  /**
   * Constructs a new frame with the given size.
   *
   * @param nLocals the maximum number of local variables of the frame.
   * @param nStack the maximum stack size of the frame.
   */
  public ExtendedFrame(int nLocals, int nStack) {
    super(nLocals, nStack);
  }

  /**
   * Constructs a new frame that is identical to the given frame.
   *
   * @param src a frame.
   */
  public ExtendedFrame(Frame<? extends BasicValue> src) {
    super(src);
  }

  @Override
  public void execute(AbstractInsnNode insn, Interpreter<BasicValue> interpreter) throws AnalyzerException {
    // remove references to local, if required
    switch (insn.getOpcode()) {
      case Opcodes.ACONST_NULL:
        push(constantValue(interpreter.newOperation(insn).getType(), null));
        break;
      case Opcodes.ICONST_M1:
        push(constantValue(interpreter.newOperation(insn).getType(), -1));
        break;
      case Opcodes.ICONST_0:
        push(constantValue(interpreter.newOperation(insn).getType(), 0));
        break;
      case Opcodes.ICONST_1:
        push(constantValue(interpreter.newOperation(insn).getType(), 1));
        break;
      case Opcodes.ICONST_2:
        push(constantValue(interpreter.newOperation(insn).getType(), 2));
        break;
      case Opcodes.ICONST_3:
        push(constantValue(interpreter.newOperation(insn).getType(), 3));
        break;
      case Opcodes.ICONST_4:
        push(constantValue(interpreter.newOperation(insn).getType(), 4));
        break;
      case Opcodes.ICONST_5:
        push(constantValue(interpreter.newOperation(insn).getType(), 5));
        break;
      case Opcodes.LCONST_0:
        push(constantValue(interpreter.newOperation(insn).getType(), 0L));
        break;
      case Opcodes.LCONST_1:
        push(constantValue(interpreter.newOperation(insn).getType(), 1L));
        break;
      case Opcodes.FCONST_0:
        push(constantValue(interpreter.newOperation(insn).getType(), 0F));
        break;
      case Opcodes.FCONST_1:
        push(constantValue(interpreter.newOperation(insn).getType(), 1F));
        break;
      case Opcodes.FCONST_2:
        push(constantValue(interpreter.newOperation(insn).getType(), 2F));
        break;
      case Opcodes.DCONST_0:
        push(constantValue(interpreter.newOperation(insn).getType(), 0D));
        return;
      case Opcodes.DCONST_1:
        push(constantValue(interpreter.newOperation(insn).getType(), 1D));
        return;
      case Opcodes.BIPUSH:
      case Opcodes.SIPUSH:
        push(constantValue(interpreter.newOperation(insn).getType(), ((IntInsnNode) insn).operand));
        return;
      case Opcodes.LDC:
        push(constantValue(interpreter.newOperation(insn).getType(), ((LdcInsnNode) insn).cst));
        return;
      case Opcodes.ISTORE:
      case Opcodes.LSTORE:
      case Opcodes.FSTORE:
      case Opcodes.DSTORE:
      case Opcodes.ASTORE:
        removeLocalFromStack(((VarInsnNode) insn).var);
        super.execute(insn, interpreter);
        break;
      case Opcodes.IINC:
        removeLocalFromStack(((IincInsnNode) insn).var);
        super.execute(insn, interpreter);
        break;
      default:
        super.execute(insn, interpreter);
        break;
    }
  }

  /**
   * Removes reference to a modified local from all locals and stack elements.
   * Because the value of the local changed, the elements "loaded" from the old value of that local
   * no longer have the same value as the local.
   *
   * @param modifiedLocal modified local
   */
  private void removeLocalFromStack(int modifiedLocal) {
    assert modifiedLocal >= 0 && modifiedLocal < getLocals() : "Precondition: modifiedLocal >= 0 && modifiedLocal < getLocals()";

    ExtendedFrame copy = new ExtendedFrame(this);
    for (int i = 0; i < copy.getLocals(); i++) {
      Value local = copy.getLocal(i);
      if (local instanceof ExtendedValue) {
        super.setLocal(i, ((ExtendedValue) local).removeLocal(modifiedLocal));
      }
    }
    clearStack();
    for (int i = 0; i < copy.getStackSize(); i++) {
      Value stack = copy.getStack(i);
      super.push(((ExtendedValue) stack).removeLocal(modifiedLocal));
    }
  }

  /**
   * {@inheritDoc}
   * <p/>
   * Always uses extended values.
   */
  @Override
  public void setLocal(int i, BasicValue value) throws IndexOutOfBoundsException {
    if (value.equals(BasicValue.UNINITIALIZED_VALUE)) {
      // it is the uninitialized value -> no annotation needed, because there is no value
      super.setLocal(i, value);
    } else if (value instanceof ExtendedValue) {
      // annotate the value, that it belongs to the local too
      super.setLocal(i, ((ExtendedValue) value).addLocal(i));
    } else {
      // annotate the value, that it belongs to the local -> new annotated value
      super.setLocal(i, valueInLocal(value.getType(), i));
    }
  }

  /**
   * {@inheritDoc}
   * <p/>
   * Always uses extended values.
   */
  @Override
  public void push(BasicValue value) throws IndexOutOfBoundsException {
    if (value.equals(BasicValue.UNINITIALIZED_VALUE)) {
      // no uninitialized values on the stack are allowed
      throw new IllegalArgumentException("Stack values have to be initialized");
    } else if (value instanceof ExtendedValue) {
      // the value already has been converted to an extended value -> copy value
      super.push(value);
    } else {
      // convert the value to an extended value -> new value
      super.push(value(value.getType()));
    }
  }
}
