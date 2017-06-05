package org.serialthreads.performance;

import org.junit.Before;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Test to analyze performance of threading with a {@link CyclicBarrier}.
 */
public class CyclicBarrierTest extends AbstractPerformanceTest {
  private final CyclicBarrier barrier = new CyclicBarrier(COUNT);

  @Before
  public void setUp() {
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new BarrierCounter(i);
    }
  }

  @Override
  protected void doStop() {
    barrier.reset();
  }

  private class BarrierCounter extends Counter {
    public BarrierCounter(int number) {
      super(number);
    }

    @Override
    protected final void tick(long count) throws Exception {
      try {
        barrier.await();
      } catch (BrokenBarrierException e) {
        // ignore exception thrown due to stopping
      }
    }
  }
}
