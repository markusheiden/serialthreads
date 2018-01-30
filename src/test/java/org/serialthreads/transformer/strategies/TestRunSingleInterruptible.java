package org.serialthreads.transformer.strategies;

import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

import static org.junit.Assert.assertEquals;

/**
 * Test runnable for transformer integration tests.
 */
public class TestRunSingleInterruptible implements IRunnable {
  private int i;

  /**
   * Execute runnable.
   */
  @Override
  @Interruptible
  public void run() {
    int i = 2;
    i = interruptible(i);
    i = i * 5;
    this.i = i;
  }

  /**
   * Test interrupt of method and capture and restore of integer locals.
   */
  @Interruptible
  public int interruptible(int i) {
    return i * 3;
  }


  //
  // Check results of execution ("self test")
  //

  /**
   * Check expected results of calling {@link TestInterruptible#run()} once.
   */
  public void assertExpectedResult() {
    // 2 * 3 * 5.
    assertEquals(30, i);
  }
}
