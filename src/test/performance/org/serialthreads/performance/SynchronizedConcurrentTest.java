package org.serialthreads.performance;

import org.junit.Before;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test to analyze performance of threading with synchronization.
 */
public class SynchronizedConcurrentTest extends AbstractPerformanceTest {
  private static final Object lock = new Object();
  private static AtomicInteger barrierCount;

  @Before
  public void setUp() {
    barrierCount = new AtomicInteger(COUNT);
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new SynchronizedConcurrentCounter(i);
    }
  }

  protected void doStop() {
    for (int i = 0; i < threads.length; i++) {
      threads[i].interrupt();
    }
  }

  private static class SynchronizedConcurrentCounter extends Counter {
    private int next;

    public SynchronizedConcurrentCounter(int number) {
      super(number);
      next = COUNT;
    }

    public void run() {
      try {
        waitForStart();
        do {
          count++;
          tick(count);
        } while(true);
      } catch (InterruptedException e) {
        // ignore
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

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