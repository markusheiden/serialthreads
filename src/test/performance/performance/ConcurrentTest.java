package performance;

import org.junit.Before;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Test to analyze performance of threading with java.lang.concurrent.
 */
public class ConcurrentTest extends AbstractPerformanceTest
{
  private static final AtomicInteger barrierCount = new AtomicInteger(COUNT);

  @Before
  public void setUp()
  {
    for (int i = 0; i < counters.length; i++)
    {
      counters[i] = new ConcurrentCounter(i);
    }
  }

  protected void doStop()
  {
    for (Thread thread : threads)
    {
      LockSupport.unpark(thread);
    }
  }

  private class ConcurrentCounter extends Counter
  {
    public ConcurrentCounter(int number)
    {
      super(number);
    }

    protected final void tick(long count) throws Exception
    {
      if (barrierCount.decrementAndGet() != 0)
      {
        LockSupport.park();
      }
      else
      {
        barrierCount.set(COUNT);
        for (int i = 0; i < COUNT; i++)
        {
          if (counters[i] != this)
          {
            LockSupport.unpark(threads[i]);
          }
        }
      }
    }
  }
}
