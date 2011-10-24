package org.serialthreads.transformer;

/**
 * Integration-test for SingleFrameExecutionTransformer.
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
