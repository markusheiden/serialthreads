package org.serialthreads.transformer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.TraceClassVisitor;
import org.serialthreads.agent.TransformingClassLoader;
import org.serialthreads.context.SerialThreadManager;

import java.io.PrintWriter;

/**
 * Integration test for transformer.
 */
public abstract class TransformerIntegration_AbstractTest
{
  protected IStrategy strategy;

  @Before
  @After
  public void setUp()
  {
    SerialThreadManager.DEBUG = true;
  }

  /**
   * Check that transformation does not alter behaviour.
   */
  @Test
  public void testTransform() throws Exception
  {
    Class<?> clazz = new TransformingClassLoader(strategy)
    {
      @Override
      protected ClassVisitor createVisitor(ClassWriter writer)
      {
        return new TraceClassVisitor(writer, new PrintWriter(System.out));
      }
    }.loadClass(TransformerIntegration_AbstractTest.class.getPackage().getName() + ".TestInterruptible");
    clazz.getMethod("runTransformed").invoke(clazz.newInstance());
  }

  /**
   * Check if the test assumptions are correct by executing without a transformer.
   */
  @Test
  public void testNoTransform() throws Exception
  {
    // disable debug mode to not throw an exception in SerialThreadManager.interrupt()
    SerialThreadManager.DEBUG = false;

    Class<?> clazz = getClass().getClassLoader().loadClass(TransformerIntegration_AbstractTest.class.getPackage().getName() + ".TestInterruptible");
    clazz.getMethod("run").invoke(clazz.newInstance());
  }
}
