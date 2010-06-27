package org.serialthreads.transformer;

import org.junit.Test;
import org.serialthreads.agent.TransformingClassLoader;
import org.serialthreads.transformer.classcache.IClassInfoCache;

/**
 * Integration-test for FrequentInterruptsTransformer3.
 */
public class FrequentInterruptsTransformer3_IntegrationTest
{
  @Test
  public void testDefault() throws Exception
  {
    ClassLoader cl = new TransformingClassLoader(new IStrategy()
    {
      @Override
      public ITransformer getTransformer(IClassInfoCache classInfoCache)
      {
        return new FrequentInterruptsTransformer3(classInfoCache);
      }
    });
    cl.loadClass("org.serialthreads.transformer.Dummy");
  }
}
