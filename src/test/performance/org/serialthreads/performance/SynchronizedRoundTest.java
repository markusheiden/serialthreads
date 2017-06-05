package org.serialthreads.performance;

import org.junit.Before;

/**
 * Test to analyze performance of threading with synchronization.
 */
public class SynchronizedRoundTest extends AbstractPerformanceTest {
  private int barrierCount;
  private int round;

  @Before
  public void setUp() {
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

    public YieldCounter(int number) {
      super(number);
      currentRound = round;
    }

    @Override
    protected final void tick(long count) throws Exception {
      if (decrementAndGetBarrier() > 0) {
        do {
          Thread.yield();
        } while (currentRound == (currentRound = getRound()));
      } else {
        setBarrier(COUNT);
        currentRound = incrementAndGetRound();
      }
    }

    private int decrementAndGetBarrier() {
      synchronized (lock) {
        return --barrierCount;
      }
    }

    private void setBarrier(int barrier) {
      synchronized (lock) {
        barrierCount = barrier;
      }
    }

    private int incrementAndGetRound() {
      synchronized (lock) {
        return ++round;
      }
    }

    private int getRound() {
      synchronized (lock) {
        return round;
      }
    }
  }
}
