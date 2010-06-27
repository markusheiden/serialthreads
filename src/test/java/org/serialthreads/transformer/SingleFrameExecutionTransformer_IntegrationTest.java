package org.serialthreads.transformer;

import org.serialthreads.transformer.classcache.IClassInfoCache;

/**
 * Integration-test for SingleFrameExecutionTransformer.
 */
public class SingleFrameExecutionTransformer_IntegrationTest extends TransformerIntegration_AbstractTest
{
  @Override
  public void setUp()
  {
    strategy = new IStrategy()
    {
      @Override
      public ITransformer getTransformer(IClassInfoCache classInfoCache)
      {
        return new SingleFrameExecutionTransformer(classInfoCache);
      }
    };
    super.setUp();
  }
}
