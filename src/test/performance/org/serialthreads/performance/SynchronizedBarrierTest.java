package org.serialthreads.performance;

import org.junit.Before;

/**
 * Test to analyze performance of threading with synchronization.
 */
public class SynchronizedBarrierTest extends AbstractPerformanceTest {
  private int barrierCount;

  @Before
  public void setUp() {
    barrierCount = 0;
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new SynchronizedCounter(i);
    }
  }

  @Override
  protected void doStop() throws Exception {
    barrierCount = Integer.MAX_VALUE;
  }

  private class SynchronizedCounter extends Counter {
    private int nextBarrier;

    public SynchronizedCounter(int number) {
      super(number);
      nextBarrier = barrierCount + COUNT;
    }

    @Override
    protected final void tick(long count) throws Exception {
      if (incrementAndGetBarrier() < nextBarrier) {
        do {
          Thread.yield();
        } while (getBarrier() < nextBarrier);
      }
      nextBarrier += COUNT;
    }

    private int incrementAndGetBarrier() {
      // Synchronization needed, because the may be interrupted in middle of read-modify-write.
      synchronized (lock) {
        return ++barrierCount;
      }
    }

    private int getBarrier() {
      // Synchronization needed, because the may be interrupted in middle of read-modify-write.
      synchronized (lock) {
        return barrierCount;
      }
    }
  }
}
