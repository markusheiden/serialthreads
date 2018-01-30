package org.serialthreads.transformer.strategies;

import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

import static org.junit.Assert.assertEquals;

/**
 * Test runnable for transformer integration tests.
 */
public class TestRunNoInterruptible implements IRunnable {
  private int i;

  /**
   * Execute runnable.
   */
  @Override
  @Interruptible
  public void run() {
    i = 1;
    i = interruptible(i);
    i = i + 1;
  }

  /**
   * Test interrupt of method and capture and restore of integer locals.
   */
  public int interruptible(int i) {
    return i + 1;
  }


  //
  // Check results of execution ("self test")
  //

  /**
   * Check expected results of calling {@link TestInterruptible#run()} once.
   */
  public void assertExpectedResult() {
    // 1 + 1 + 1.
    assertEquals(3, i);
  }
}
