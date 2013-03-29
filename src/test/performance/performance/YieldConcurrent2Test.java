package performance;

import org.junit.Before;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test to analyze performance of threading with java.lang.concurrent.
 */
public class YieldConcurrent2Test extends AbstractPerformanceTest {
  private static volatile AtomicInteger barrierCount;

  @Before
  public void setUp() {
    barrierCount = new AtomicInteger(0);
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new YieldConcurrentCounter(i);
    }
  }

  @Override
  protected void doStop() throws Exception {
    barrierCount.addAndGet(COUNT);
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
