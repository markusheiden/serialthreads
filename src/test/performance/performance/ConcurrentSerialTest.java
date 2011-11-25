package performance;

import org.junit.Before;

import java.util.concurrent.locks.LockSupport;

/**
 * Test to analyze performance of threading with java.lang.concurrent.
 */
public class ConcurrentSerialTest extends AbstractPerformanceTest
{
  @Before
  public void setUp()
  {
    for (int i = 0; i < counters.length; i++)
    {
      counters[i] = new ConcurrentSerialCounter(i);
    }
  }

  @Override
  protected void doUnlockThreads() throws Exception
  {
    super.doUnlockThreads();
    LockSupport.unpark(threads[0]);
  }

  protected void doStop()
  {
    for (Thread thread : threads)
    {
      LockSupport.unpark(thread);
    }
  }

  private class ConcurrentSerialCounter extends Counter
  {
    private Thread next;

    public ConcurrentSerialCounter(int number)
    {
      super(number);
    }

    @Override
    protected void waitForStart() throws InterruptedException
    {
      next = threads[(number + 1) % COUNT];
      synchronized (lock)
      {
        ready = true;
      }
      LockSupport.park();
    }

    protected final void tick(long count) throws Exception
    {
      LockSupport.unpark(next);
      LockSupport.park();
    }
  }
}
