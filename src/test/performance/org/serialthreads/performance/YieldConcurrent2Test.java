package org.serialthreads.performance;

import org.junit.Before;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test to analyze performance of threading with java.lang.concurrent.
 */
public class YieldConcurrent2Test extends AbstractPerformanceTest {
  private final AtomicInteger barrierCount = new AtomicInteger();

  @Before
  public void setUp() {
    barrierCount.set(0);
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new YieldConcurrentCounter(i);
    }
  }

  @Override
  protected void doStop() throws Exception {
    barrierCount.set(Integer.MAX_VALUE);
  }

  private class YieldConcurrentCounter extends Counter {
    private int nextBarrier;

    public YieldConcurrentCounter(int number) {
      super(number);
      nextBarrier = barrierCount.get() + COUNT;
    }

    @Override
    protected final void tick(long count) throws Exception {
      if (barrierCount.incrementAndGet() != nextBarrier) {
        do {
          Thread.yield();
        } while (barrierCount.get() < nextBarrier);
      }
      nextBarrier += COUNT;
    }
  }
}
