package org.serialthreads.performance;

import org.junit.Before;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test to analyze performance of threading with synchronization.
 */
public class SynchronizedConcurrentTest extends AbstractPerformanceTest {
  private final AtomicInteger barrierCount = new AtomicInteger();
  private final AtomicInteger waiting = new AtomicInteger();

  @Before
  public void setUp() {
    barrierCount.set(0);
    waiting.set(1);
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

  private class SynchronizedConcurrentCounter extends Counter {
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
          waiting.incrementAndGet();
          lock.wait();
        }
      } else {
        while (waiting.get() < COUNT) {
          Thread.yield();
        }
        waiting.set(1);
        synchronized (lock) {
          lock.notifyAll();
        }
      }
      next += COUNT;
    }
  }
}
