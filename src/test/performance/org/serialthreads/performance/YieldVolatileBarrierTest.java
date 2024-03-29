package org.serialthreads.performance;

import org.junit.jupiter.api.BeforeEach;

/**
 * Test to analyze performance of threading with {@link Thread#yield()}.
 */
class YieldVolatileBarrierTest extends AbstractPerformanceTest {
  private volatile int barrierCount;

  @BeforeEach
  void setUp() {
    barrierCount = 0;
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new YieldCounter(i);
    }
  }

  @Override
  protected void doStop() throws Exception {
    barrierCount = Integer.MAX_VALUE;
  }

  private class YieldCounter extends Counter {
    private int nextBarrier;

    YieldCounter(int number) {
      super(number);
      nextBarrier = barrierCount + COUNT;
    }

    @Override
    protected final void tick(long count) throws Exception {
      if (incrementAndGetBarrier() < nextBarrier) {
        do {
          Thread.yield();
        } while (barrierCount < nextBarrier);
      }
      nextBarrier += COUNT;
    }

    private int incrementAndGetBarrier() {
      // Synchronization needed, because the may be interrupted in middle of read-modify-write.
      // barrierCount needs still to be volatile, because it is read above.
      synchronized (lock) {
        return ++barrierCount;
      }
    }
  }
}
