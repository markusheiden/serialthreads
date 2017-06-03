package org.serialthreads.performance;

import org.serialthreads.Interruptible;

/**
 * Simple runnable which increments an integer by 1 each run.
 */
public abstract class Counter implements ICounter {
  protected static final Object lock = new Object();
  private static volatile boolean run = false;
  protected final int number;
  protected volatile boolean ready = false;
  protected long count;

  protected Counter(int number) {
    this.number = number;
  }

  @Override
  @Interruptible
  public void run() {
    try {
      waitForStart();
      do {
        count++;
        tick(count);
      } while (run);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Interruptible
  protected abstract void tick(long count) throws Exception;

  protected void waitForStart() throws InterruptedException {
    synchronized (lock) {
      ready = true;
      lock.wait();
    }
  }

  public static void startAll() {
    run = true;
    synchronized (lock) {
      System.out.println("starting all");
      lock.notifyAll();
    }
  }

  public static void stopAll() {
    System.out.println("stopping all");
    run = false;
  }

  @Override
  public String toString() {
    return "counter " + number;
  }

  @Override
  public boolean isReady() {
    return ready;
  }

  @Override
  public synchronized long getCount() {
    return count;
  }
}
