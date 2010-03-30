package org.serialthreads.transformer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.TraceClassVisitor;
import org.serialthreads.agent.TransformingClassLoader;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.SerialThreadManager;
import org.serialthreads.context.SimpleSerialThreadManager;

import java.io.PrintWriter;

/**
 * Test.
 */
public class Test
{
  public static void main(String[] args) throws Exception
  {
    ClassLoader transformer = new TransformingClassLoader(Strategies.FREQUENT)
    {
      @Override
      protected ClassVisitor createVisitor(ClassWriter writer)
      {
        return new TraceClassVisitor(writer, new PrintWriter(System.out));
      }
    };

    SerialThreadManager.DEBUG = false;
    Class<? extends IRunnable> clazz = (Class<? extends IRunnable>)
      transformer.loadClass(TransformerIntegrationTest.class.getName() + "$TestInterruptible");
//    Class<? extends  Runnable> clazz = (Class<? extends Runnable>)
//      transformer.loadClass("org.serialthreads.transformer.Dummy");
    IRunnable dummy = clazz.newInstance();
    System.out.println("start");
    SimpleSerialThreadManager manager = new SimpleSerialThreadManager(dummy);
    SerialThreadManager.registerManager(manager);
    manager.execute();
    System.out.println("stop");
  }
}
