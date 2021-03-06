package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

/**
 * Test object for tail call handling.
 */
public class TestTailCall implements IRunnable {
  public int value = -1;

  @Interruptible
  public void run() {
    value = method1();
    interrupt();
  }

  @Interruptible
  private int method1() {
    return method2();
  }

  @Interruptible
  private int method2() {
    interrupt();
    return 1;
  }

  @Interrupt
  private void interrupt() {
    throw new UnsupportedOperationException("Method calls should be replaced by interrupt code");
  }
}
