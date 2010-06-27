package org.serialthreads.transformer;

import org.junit.Test;
import org.serialthreads.agent.TransformingClassLoader;
import org.serialthreads.transformer.classcache.IClassInfoCache;

/**
 * Integration-test for FrequentInterruptsTransformer.
 */
public class FrequentInterruptsTransformer_IntegrationTest extends TransformerIntegration_AbstractTest
{
  @Override
  public void setUp()
  {
    strategy = new IStrategy()
    {
      @Override
      public ITransformer getTransformer(IClassInfoCache classInfoCache)
      {
        return new FrequentInterruptsTransformer(classInfoCache);
      }
    };
    super.setUp();
  }
}
