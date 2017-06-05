package org.serialthreads.performance;

import org.junit.Before;

/**
 * Test to analyze performance of threading with {@link Thread#yield()}.
 */
public class YieldVolatileTest extends AbstractPerformanceTest {
  private volatile int barrierCount;
  private volatile int round;

  @Before
  public void setUp() {
    barrierCount = COUNT - 1;
    round = 0;
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

    public YieldCounter(int number) {
      super(number);
      currentRound = 0;
    }

    @Override
    protected final void tick(long count) throws Exception {
      if (barrierCount != 0) {
        --barrierCount;
        do {
          Thread.yield();
        } while (currentRound == round);
      } else {
        barrierCount = COUNT - 1;
        currentRound = ++round;
      }
    }
  }
}
