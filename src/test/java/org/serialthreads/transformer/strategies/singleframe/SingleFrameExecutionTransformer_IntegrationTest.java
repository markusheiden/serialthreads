package org.serialthreads.transformer.strategies.singleframe;

import org.serialthreads.transformer.Strategies;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link org.serialthreads.transformer.strategies.singleframe.SingleFrameExecutionTransformer}.
 */
public class SingleFrameExecutionTransformer_IntegrationTest extends TransformerIntegration_AbstractTest {
  @Override
  public void setUp() {
    strategy = Strategies.SINGLE_FRAME_EXECUTION;
    super.setUp();
  }
}
