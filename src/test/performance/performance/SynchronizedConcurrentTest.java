package performance;

import org.junit.Before;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test to analyze performance of threading with synchronization.
 */
public class SynchronizedConcurrentTest extends AbstractPerformanceTest {
  private static AtomicInteger barrierCount;

  @Before
  public void setUp() {
    barrierCount = new AtomicInteger(COUNT);
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new SynchronizedConcurrentCounter(i);
    }
  }

  @Override
  protected void doStop() {
    for (Thread thread : threads) {
      thread.interrupt();
    }
  }

  private static class SynchronizedConcurrentCounter extends Counter {
    private int next;

    public SynchronizedConcurrentCounter(int number) {
      super(number);
      next = COUNT;
    }

    @Override
    public void run() {
      try {
        waitForStart();
        for (;;) {
          count++;
          tick(count);
        }
      } catch (InterruptedException e) {
        // ignore
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    @Override
    protected final void tick(long count) throws Exception {
      if (barrierCount.incrementAndGet() < next) {
        synchronized (lock) {
          lock.wait();
        }
      } else {
        synchronized (lock) {
          lock.notifyAll();
        }
      }
      next += COUNT;
    }
  }
}
