package org.serialthreads.performance;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.serialthreads.Interruptible;
import org.serialthreads.agent.Transform;
import org.serialthreads.agent.TransformingRunner;
import org.serialthreads.context.SerialThreadManager;
import org.serialthreads.context.SimpleSerialThreadManager;
import org.serialthreads.transformer.strategies.frequent3.FrequentInterruptsTransformer3;

/**
 * Test to analyze performance of threading with serial threads.
 */
@RunWith(TransformingRunner.class)
@Transform(transformer = FrequentInterruptsTransformer3.class, classPrefixes = "org.serialthreads.performance")
public class SerialThreadTest extends AbstractPerformanceTest {
  private Thread managerThread;
  private volatile boolean ready;
  private final Object lock = new Object();

  @Before
  public void setUp() throws Exception {
    for (int i = 0; i < counters.length; i++) {
      counters[i] = new SerialCounter(i);
    }
  }

  @Override
  protected void doStartThreads() throws Exception {
    ready = false;
    managerThread = new Thread(() -> {
      try {
        SimpleSerialThreadManager manager = new SimpleSerialThreadManager(counters);
        SerialThreadManager.registerManager(manager);
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
  protected void doUnlockThreads() throws Exception {
    Counter.startAll();
    synchronized (lock) {
      lock.notifyAll();
    }
  }

  @Override
  protected void doStopCounters() throws Exception {
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

  public static class SerialCounter extends Counter {
    public SerialCounter(int number) {
      super(number);
    }

    @Override
    protected void waitForStart() throws InterruptedException {
      // not needed
    }

    @Override
    @Interruptible
    protected final void tick(long count) throws Exception {
      SerialThreadManager.interrupt();
    }
  }
}
