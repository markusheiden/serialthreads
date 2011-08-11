package org.serialthreads.transformer;

import org.serialthreads.transformer.classcache.IClassInfoCache;

/**
 * Integration-test for FrequentInterruptsTransformer2.
 */
public class FrequentInterruptsTransformer2_IntegrationTest extends TransformerIntegration_AbstractTest
{
  @Override
  public void setUp()
  {
    strategy = new IStrategy()
    {
      @Override
      public ITransformer getTransformer(IClassInfoCache classInfoCache)
      {
        return new FrequentInterruptsTransformer2(classInfoCache);
      }
    };
    super.setUp();
  }
}
