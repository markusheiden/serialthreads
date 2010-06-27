package org.serialthreads.transformer;

import org.serialthreads.agent.TransformingClassLoader;
import org.serialthreads.transformer.classcache.IClassInfoCache;

/**
 * Integration-test for FrequentInterruptsTransformer3.
 */
public class FrequentInterruptsTransformer2_IntegrationTest
{
  @org.junit.Test
  public void testDefault() throws Exception
  {
    ClassLoader cl = new TransformingClassLoader(new IStrategy()
    {
      @Override
      public ITransformer getTransformer(IClassInfoCache classInfoCache)
      {
        return new FrequentInterruptsTransformer2(classInfoCache);
      }
    });
    cl.loadClass("org.serialthreads.transformer.Dummy");
  }
}
