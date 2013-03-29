package performance;

import org.junit.Before;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test to analyze performance of threading with java.lang.concurrent.
 */
public class YieldConcurrentTest extends AbstractPerformanceTest {
  private static volatile AtomicInteger barrierCount;
  private static volatile AtomicInteger round;

  @Before
  public void setUp() {
    barrierCount = new AtomicInteger(COUNT);
    round = new AtomicInteger(Integer.MIN_VALUE);
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
      if (barrierCount.decrementAndGet() != 0) {
        do {
          Thread.yield();
        } while (currentRound == round.get());
      } else {
        barrierCount.set(COUNT);
        currentRound = round.incrementAndGet();
      }
    }
  }
}
