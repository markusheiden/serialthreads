package org.serialthreads.transformer.strategies;

import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

import static org.junit.Assert.assertEquals;

/**
 * Test runnable for transformer integration tests.
 */
public class TestRunMultiInterruptible implements IRunnable {
  private int i;

  /**
   * Execute runnable.
   */
  @Override
  @Interruptible
  public void run() {
    int i = 2;
    i = interruptible1(i);
    i = i * 7;
    i = interruptible2(i);
    i = i * 11;
    this.i = i;
  }

  /**
   * Test interrupt of method and capture and restore of integer locals.
   */
  @Interruptible
  public int interruptible1(int i) {
    return i * 3;
  }

  /**
   * Test interrupt of method and capture and restore of integer locals.
   */
  @Interruptible
  public int interruptible2(int i) {
    return i * 5;
  }

  //
  // Check results of execution ("self test")
  //

  /**
   * Check expected results of calling {@link TestInterruptible#run()} once.
   */
  public void assertExpectedResult() {
    // 2 * 3 * 5 * 7 * 11.
    assertEquals(2310, i);
  }
}
