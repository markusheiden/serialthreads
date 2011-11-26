package org.serialthreads.transformer;

import org.serialthreads.transformer.strategies.Strategies;

/**
 * Integration-test for {@link org.serialthreads.transformer.strategies.singleframe.SingleFrameExecutionTransformer}.
 */
public class SingleFrameExecutionTransformer_IntegrationTest extends TransformerIntegration_AbstractTest
{
  @Override
  public void setUp()
  {
    strategy = Strategies.SINGLE_FRAME_EXECUTION;
    super.setUp();
  }
}
