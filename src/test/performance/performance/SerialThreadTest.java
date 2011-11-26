package performance;

import org.junit.Before;
import org.serialthreads.Interruptible;
import org.serialthreads.agent.TransformingClassLoader;
import org.serialthreads.context.SerialThreadManager;
import org.serialthreads.context.SimpleSerialThreadManager;
import org.serialthreads.transformer.strategies.Strategies;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Test to analyze performance of threading with serial threads.
 */
public class SerialThreadTest extends AbstractPerformanceTest
{
  private Class<? extends ICounter> counterClass;
  private Field run;

  private Thread managerThread;
  private volatile boolean ready;
  private final Object lock = new Object();

  @Before
  public void setUp() throws Exception
  {
    ClassLoader cl = new TransformingClassLoader(Strategies.DEFAULT, getClass().getPackage().getName());
    counterClass = (Class<? extends ICounter>) cl.loadClass(SerialCounter.class.getName());
    Constructor<? extends ICounter> constructor = counterClass.getConstructor(int.class);
    run = counterClass.getSuperclass().getDeclaredField("run");
    run.setAccessible(true);
    run.set(null, true);
    for (int i = 0; i < counters.length; i++)
    {
      counters[i] = constructor.newInstance(i);
    }
  }

  @Override
  protected void doStartThreads() throws Exception
  {
    ready = false;
    managerThread = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          SimpleSerialThreadManager manager = new SimpleSerialThreadManager(counters);
          SerialThreadManager.registerManager(manager);
          synchronized (lock)
          {
            ready = true;
            lock.wait();
          }
          manager.execute();
          System.out.println("stopped scheduling");
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    }, "Serial thread manager");
    managerThread.start();

    do
    {
      Thread.yield();
    } while (!ready);
  }

  @Override
  protected void doUnlockThreads() throws Exception
  {
    System.out.println("starting all");
    synchronized (lock)
    {
      lock.notifyAll();
    }
  }

  protected void doStopCounters() throws Exception
  {
    System.out.println("stopping all");
    run.setBoolean(null, false);
  }

  protected void doStop()
  {
    // not needed
  }

  @Override
  protected void doJoinThreads() throws Exception
  {
    managerThread.join();
  }

  public static class SerialCounter extends Counter
  {
    public SerialCounter(int number)
    {
      super(number);
    }

    @Override
    protected void waitForStart() throws InterruptedException
    {
      // not needed
    }

    @Interruptible
    protected final void tick(long count) throws Exception
    {
      SerialThreadManager.interrupt();
    }
  }
}
