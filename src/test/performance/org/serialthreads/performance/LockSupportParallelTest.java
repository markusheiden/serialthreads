package org.serialthreads.performance;

import org.junit.jupiter.api.BeforeEach;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Test to analyze performance of threading with {@link LockSupport}.
 */
class LockSupportParallelTest extends AbstractPerformanceTest {
  private final AtomicInteger barrierCount = new AtomicInteger();

  @BeforeEach
  void setUp() {
    barrierCount.set(COUNT);
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new ConcurrentCounter(i);
    }
  }

  @Override
  protected void doStop() {
    for (Thread thread : threads) {
      LockSupport.unpark(thread);
    }
  }

  private class ConcurrentCounter extends Counter {
    ConcurrentCounter(int number) {
      super(number);
    }

    @Override
    protected final void tick(long count) throws Exception {
      if (barrierCount.decrementAndGet() != 0) {
        LockSupport.park();
      } else {
        barrierCount.set(COUNT);
        for (int i = 0; i < COUNT; i++) {
          if (counters[i] != this) {
            LockSupport.unpark(threads[i]);
          }
        }
      }
    }
  }
}
