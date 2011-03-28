package performance;

import org.junit.Test;
import org.serialthreads.context.IRunnable;

import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertTrue;

/**
 * Base performance test.
 */
public abstract class AbstractPerformanceTest
{
  protected static final int TIME = 10 * 1000;
  protected static final int COUNT = 10;
  protected final ICounter[] counters = new ICounter[COUNT];
  protected final Thread[] threads = new Thread[COUNT];
  private long startTime;

  @Test
  public synchronized void testPerformance() throws Exception
  {
    start();
    wait(TIME);
    stop();

    for (int i = 0; i < counters.length; i++)
    {
      System.out.println("Counter " + i + ": " + counters[i].getCount());
    }

    long count = counters[0].getCount();
    for (int i = 1; i < counters.length; i++)
    {
      // check that all counters differ only by max 1, due to different stop time
      assertTrue("Counters are consistent: " + count + " / " + counters[i].getCount(), count - 1 <= counters[i].getCount());
      assertTrue("Counters are consistent: " + count + " / " + counters[i].getCount(), counters[i].getCount() <= count + 1);
      // just to be sure check that not all counters are the same
      assertNotSame(counters[0], counters[i]);
    }
  }

  /**
   * Start test run.
   */
  protected final void start() throws Exception
  {
    doStartThreads();
    startTime = System.currentTimeMillis();
    doUnlockThreads();
  }

  /**
   * Create and start counter threads.
   * Threads should be in a waiting state.
   */
  protected void doStartThreads() throws Exception
  {
    for (int i = 0; i < counters.length; i++)
    {
      final IRunnable counter = counters[i];
      threads[i] = new Thread(new Runnable()
      {
        @Override
        public void run()
        {
          counter.run();
        }
      }, "Counter " + i);
      threads[i].start();
    }

    boolean ready;
    do
    {
      Thread.yield();
      ready = true;
      for (int i = 0; i < counters.length; i++)
      {
        ready &= counters[i].isReady();
      }
    } while (!ready);
  }

  /**
   * Unlock all waiting threads.
   */
  protected void doUnlockThreads() throws Exception
  {
    Counter.startAll();
  }

  /**
   * Stop test run.
   */
  protected final void stop() throws Exception
  {
    doStopCounters();
    long endTime = System.currentTimeMillis();
    doStop();
    doJoinThreads();

    long duration = endTime - startTime;
    long countsPerSecond = counters[0].getCount() * 1000 / duration;
    System.out.println("Performance: " + countsPerSecond + " counts/s for " + COUNT + " threads");
  }

  /**
   * Stop all counters from counting any further.
   */
  protected void doStopCounters() throws Exception
  {
    Counter.stopAll();
  }

  /**
   * Unlock all threads that are still waiting to be executed again.
   */
  protected abstract void doStop() throws Exception;

  /**
   * Join stopped thread to ensure that they have really ended.
   */
  protected void doJoinThreads() throws Exception
  {
    for (int i = 0; i < threads.length; i++)
    {
      threads[i].join();
    }
  }
}
