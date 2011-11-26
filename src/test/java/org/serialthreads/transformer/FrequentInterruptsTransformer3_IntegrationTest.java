package org.serialthreads.transformer;

import org.serialthreads.transformer.strategies.Strategies;

/**
 * Integration-test for {@link org.serialthreads.transformer.strategies.frequent3.FrequentInterruptsTransformer3}.
 */
public class FrequentInterruptsTransformer3_IntegrationTest extends TransformerIntegration_AbstractTest
{
  @Override
  public void setUp()
  {
    strategy = Strategies.FREQUENT3;
    super.setUp();
  }
}
