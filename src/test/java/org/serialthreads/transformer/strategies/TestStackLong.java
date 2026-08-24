package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

/**
 * Test object for capture and restore of operand stack values of type {@link long}.
 * The field reads put longs onto the operand stack which are neither constants nor mirrored in locals,
 * so they have to be captured from the stack when the called method interrupts.
 */
public class TestStackLong implements IRunnable {
  // Deliberately not final: a final field with a constant initializer is a constant variable,
  // which javac inlines, so the value would not be captured from the operand stack.
  private long base = 10;
  public long value = -1;
  public long valueDeep = -1;

  @Interruptible
  public void run() {
    // One long on the operand stack across the interrupt: fast stack storage.
    value = base + interruptibleValue();
    // Nine longs on the operand stack across the interrupt: exceeds the fast stack storage.
    valueDeep = base + (base + (base + (base + (base + (base + (base + (base + (base + interruptibleValue()))))))));
  }

  @Interruptible
  private long interruptibleValue() {
    interrupt();
    return 32;
  }

  @Interrupt
  private void interrupt() {
    // method call will be redirected to interrupt code
  }
}
