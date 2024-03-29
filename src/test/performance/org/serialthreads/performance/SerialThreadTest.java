package org.serialthreads.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.agent.Transform;
import org.serialthreads.context.SimpleSerialThreadManager;
import org.serialthreads.transformer.strategies.frequent3.FrequentInterruptsTransformer3;

/**
 * Test to analyze performance of threading with serial threads.
 */
@Transform(transformer = FrequentInterruptsTransformer3.class, classPrefixes = "org.serialthreads.performance")
class SerialThreadTest extends AbstractPerformanceTest {
  private Thread managerThread;
  private volatile boolean ready;
  private final Object lock = new Object();

  @BeforeEach
  void setUp() {
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new SerialCounter(i);
    }
  }

  @Override
  protected void doStartThreads() {
    ready = false;
    managerThread = new Thread(() -> {
      try {
        SimpleSerialThreadManager manager = new SimpleSerialThreadManager(counters);
        synchronized (lock) {
          ready = true;
          lock.wait();
        }
        manager.execute();
        System.out.println("stopped scheduling");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }, "Serial thread manager");
    managerThread.start();

    do {
      Thread.yield();
    } while (!ready);
  }

  @Override
  protected void doUnlockThreads() {
    Counter.startAll();
    synchronized (lock) {
      lock.notifyAll();
    }
  }

  @Override
  protected void doStopCounters() {
    System.out.println("stopping all");
    Counter.stopAll();
  }

  @Override
  protected void doStop() {
    // not needed
  }

  @Override
  protected void doJoinThreads() throws Exception {
    managerThread.join();
  }

  static class SerialCounter extends Counter {
    SerialCounter(int number) {
      super(number);
    }

    @Override
    protected void waitForStart() {
      // not needed
    }

    @Override
    @Interruptible
    protected final void tick(long count) {
      interrupt();
    }

    @Interrupt
    private void interrupt() {
      throw new IllegalThreadStateException("Byte code transformation failed");
    }
  }
}
