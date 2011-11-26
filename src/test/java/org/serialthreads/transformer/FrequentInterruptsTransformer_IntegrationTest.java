package org.serialthreads.transformer;

import org.serialthreads.transformer.strategies.Strategies;

/**
 * Integration-test for {@link org.serialthreads.transformer.strategies.frequent.FrequentInterruptsTransformer}.
 */
public class FrequentInterruptsTransformer_IntegrationTest extends TransformerIntegration_AbstractTest
{
  @Override
  public void setUp()
  {
    strategy = Strategies.FREQUENT;
    super.setUp();
  }
}
