package org.serialthreads.performance;

import org.junit.Before;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Test to analyze performance of threading with a CyclicBarrier.
 */
public class BarrierTest extends AbstractPerformanceTest
{
  private CyclicBarrier barrier;

  @Before
  public void setUp()
  {
    barrier = new CyclicBarrier(COUNT);
    for (int i = 0; i < counters.length; i++)
    {
      counters[i] = new BarrierCounter(i);
    }
  }

  protected void doStop()
  {
    barrier.reset();
  }

  private class BarrierCounter extends Counter
  {
    public BarrierCounter(int number)
    {
      super(number);
    }

    protected final void tick(long count) throws Exception
    {
      try
      {
        barrier.await();
      }
      catch (BrokenBarrierException e)
      {
        // ignore exception thrown due to stopping
      }
    }
  }
}
