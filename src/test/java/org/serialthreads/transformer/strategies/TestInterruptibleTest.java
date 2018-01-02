package org.serialthreads.transformer.strategies;

import org.junit.After;
import org.junit.Test;
import org.serialthreads.context.SerialThreadManager;

/**
 * Test for {@link TestInterruptible}.
 */
public class TestInterruptibleTest {
  @After
  public void tearDown() {
    // Re-enable debug mode, to detect byte code transformation failures.
    SerialThreadManager.DEBUG = true;
  }

  /**
   * Check if the test assertions are correct by executing them without a transformer.
   */
  @Test
  public void testNoTransform() {
    // Disable debug mode to not throw an exception in SerialThreadManager.interrupt().
    SerialThreadManager.DEBUG = false;

    TestInterruptible test = new TestInterruptible();
    test.run();
    test.assertExpectedResult();
  }
}
