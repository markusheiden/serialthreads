package org.serialthreads.performance;

import org.junit.jupiter.api.BeforeEach;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test to analyze performance of threading with {@link Thread#yield()}.
 */
public class YieldAtomicRoundTest extends AbstractPerformanceTest {
  private final AtomicInteger barrierCount = new AtomicInteger();
  private final AtomicInteger round = new AtomicInteger();

  @BeforeEach
  public void setUp() {
    barrierCount.set(COUNT);
    round.set(Integer.MIN_VALUE);
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new YieldConcurrentCounter(i);
    }
  }

  @Override
  protected void doStop() throws Exception {
    round.incrementAndGet();
  }

  private class YieldConcurrentCounter extends Counter {
    private int currentRound;

    public YieldConcurrentCounter(int number) {
      super(number);
      currentRound = round.get();
    }

    @Override
    protected final void tick(long count) throws Exception {
      if (barrierCount.decrementAndGet() > 0) {
        do {
          Thread.yield();
        } while (currentRound == (currentRound = round.get()));
      } else {
        barrierCount.set(COUNT);
        currentRound = round.incrementAndGet();
      }
    }
  }
}
