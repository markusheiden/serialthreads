package org.serialthreads.transformer.analyzer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.HashSet;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.tree.analysis.BasicValue.UNINITIALIZED_VALUE;
import static org.serialthreads.transformer.analyzer.ExtendedValue.constantValue;
import static org.serialthreads.transformer.analyzer.ExtendedValue.value;
import static org.serialthreads.transformer.analyzer.ExtendedValue.valueInLocal;

/**
 * Frame for extended analyzer.
 * Supports detection of stack elements that have the same value as a local.
 */
public final class ExtendedFrame extends Frame<BasicValue> {
  /**
   * Need locals.
   */
  public final Set<Integer> neededLocals = new HashSet<>();

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
      case ACONST_NULL ->
              push(constantValue(interpreter.newOperation(insn).getType(), null));
      case ICONST_M1 ->
              push(constantValue(interpreter.newOperation(insn).getType(), -1));
      case ICONST_0 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 0));
      case ICONST_1 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 1));
      case ICONST_2 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 2));
      case ICONST_3 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 3));
      case ICONST_4 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 4));
      case ICONST_5 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 5));
      case LCONST_0 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 0L));
      case LCONST_1 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 1L));
      case FCONST_0 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 0F));
      case FCONST_1 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 1F));
      case FCONST_2 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 2F));
      case DCONST_0 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 0D));
      case DCONST_1 ->
              push(constantValue(interpreter.newOperation(insn).getType(), 1D));
      case BIPUSH, SIPUSH ->
              push(constantValue(interpreter.newOperation(insn).getType(), ((IntInsnNode) insn).operand));
      case LDC ->
              push(constantValue(interpreter.newOperation(insn).getType(), ((LdcInsnNode) insn).cst));
      case ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> {
        removeLocalFromStack(((VarInsnNode) insn).var);
        super.execute(insn, interpreter);
      }
      case IINC -> {
        removeLocalFromStack(((IincInsnNode) insn).var);
        super.execute(insn, interpreter);
      }
      default ->
              super.execute(insn, interpreter);
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

    var copy = new ExtendedFrame(this);
    for (int i = 0; i < copy.getLocals(); i++) {
      var local = copy.getLocal(i);
      if (local instanceof ExtendedValue l) {
        super.setLocal(i, l.removeLocal(modifiedLocal));
      }
    }
    clearStack();
    for (int i = 0; i < copy.getStackSize(); i++) {
      var stack = copy.getStack(i);
      super.push(((ExtendedValue) stack).removeLocal(modifiedLocal));
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Always uses extended values.
   */
  @Override
  public void setLocal(int i, BasicValue value) throws IndexOutOfBoundsException {
    if (value.equals(UNINITIALIZED_VALUE)) {
      // it is the uninitialized value -> no annotation needed, because there is no value
      super.setLocal(i, value);
    } else if (value instanceof ExtendedValue v) {
      // annotate the value, that it belongs to the local too
      super.setLocal(i, v.addLocal(i));
    } else {
      // annotate the value, that it belongs to the local -> new annotated value
      super.setLocal(i, valueInLocal(value.getType(), i));
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Always uses extended values.
   */
  @Override
  public void push(BasicValue value) throws IndexOutOfBoundsException {
    if (value.equals(UNINITIALIZED_VALUE)) {
      // no uninitialized values on the stack are allowed
      throw new IllegalArgumentException("Stack values have to be initialized");
    } else if (value instanceof ExtendedValue v) {
      // the value already has been converted to an extended value -> copy value
      super.push(v);
    } else {
      // convert the value to an extended value -> new value
      super.push(value(value.getType()));
    }
  }

  //
  // Backflow analysis related stuff
  //

  /**
   * Get the lowest needed local holding the given value.
   *
   * @param value Value.
   * @return local or -1.
   */
  public int getLowestNeededLocal(ExtendedValue value) {
    for (int lowerLocal : value.getLocals()) {
      if (neededLocals.contains(lowerLocal)) {
        return lowerLocal;
      }
    }

    return -1;
  }
}
