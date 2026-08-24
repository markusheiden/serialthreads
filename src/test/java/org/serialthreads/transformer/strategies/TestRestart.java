package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

/**
 * Test object for restarting a finished serial thread.
 */
public class TestRestart implements IRunnable {
  public int runs = 0;
  public int value = 0;

  @Interruptible
  public void run() {
    runs++;
    value = 1;

    interrupt();

    value = 2;
  }

  @Interrupt
  void interrupt() {
    // Method call will be redirected to interrupt code.
  }
}
