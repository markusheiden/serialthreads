package org.serialthreads.performance;

import org.junit.jupiter.api.BeforeEach;

/**
 * Test to analyze performance of threading with {@link Thread#yield()}.
 */
class YieldVolatileRoundTest extends AbstractPerformanceTest {
  private volatile int barrierCount;
  private volatile int round;

  @BeforeEach
  void setUp() {
    barrierCount = COUNT;
    round = Integer.MIN_VALUE;
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new YieldCounter(i);
    }
  }

  @Override
  protected void doStop() throws Exception {
    round++;
  }

  private class YieldCounter extends Counter {
    private int currentRound;

    YieldCounter(int number) {
      super(number);
      currentRound = round;
    }

    @Override
    protected final void tick(long count) throws Exception {
      if (decrementAndGetBarrier() > 0) {
        do {
          Thread.yield();
        } while (currentRound == (currentRound = round));
      } else {
        barrierCount = COUNT;
        currentRound = ++round;
      }
    }

    private int decrementAndGetBarrier() {
      // Synchronization needed, because the may be interrupted in middle of read-modify-write.
      // barrierCount needs still to be volatile, because it is reset above.
      synchronized (lock) {
        return --barrierCount;
      }
    }
  }
}
