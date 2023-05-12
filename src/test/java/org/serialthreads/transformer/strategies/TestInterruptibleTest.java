package org.serialthreads.transformer.strategies;

import org.junit.jupiter.api.Test;

/**
 * Test for {@link TestInterruptible}.
 */
class TestInterruptibleTest {
  /**
   * Check if the test assertions are correct by executing them without a transformer.
   */
  @Test
  void testNoTransform() {
    var test = new TestInterruptible(false);
    test.run();
    test.assertExpectedResult();
  }
}
