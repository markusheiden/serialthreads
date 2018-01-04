package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

/**
 * Test object for exception handling.
 */
public class TestException implements IRunnable {
  public int value = -1;

  @Interruptible
  public void run() {
    int v = Math.round(1.1F);
    try {
      throwException();
    } catch (Exception e) {
      // ignore
    }
    value = v;
  }

  @Interruptible
  private int throwException() {
    interrupt();
    throw new RuntimeException();
  }

  @Interrupt
  private void interrupt() {
    throw new UnsupportedOperationException("Method calls should be replaced by interrupt code");
  }
}
