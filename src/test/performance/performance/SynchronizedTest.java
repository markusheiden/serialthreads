package performance;

import org.junit.Before;

/**
 * Test to analyze performance of threading with synchronization.
 */
public class SynchronizedTest extends AbstractPerformanceTest {
  private static int barrierCount;

  @Before
  public void setUp() {
    barrierCount = COUNT - 1;
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new SynchronizedCounter(i);
    }
  }

  @Override
  protected void doStop() {
    for (int i = 0; i < threads.length; i++) {
      threads[i].interrupt();
    }
  }

  private static class SynchronizedCounter extends Counter {
    public SynchronizedCounter(int number) {
      super(number);
    }

    @Override
    public void run() {
      try {
        waitForStart();
        do {
          count++;
          tick(count);
        } while (true);
      } catch (InterruptedException e) {
        // ignore
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    @Override
    protected final void tick(long count) throws Exception {
      synchronized (lock) {
        if (barrierCount != 0) {
          --barrierCount;
          lock.wait();
        } else {
          barrierCount = COUNT - 1;
          lock.notifyAll();
        }
      }
    }
  }
}
