package org.serialthreads.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link ChainedRunnable}.
 */
class ChainedRunnableTest {
  @Test
  void testChain_empty() {
    assertThatThrownBy(ChainedRunnable::chain)
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(SimpleSerialThreadManager::new)
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(SerialThreadExecutor::new)
      .isInstanceOf(IllegalArgumentException.class);
  }
}
