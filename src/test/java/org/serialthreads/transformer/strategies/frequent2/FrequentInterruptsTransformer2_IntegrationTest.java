package org.serialthreads.transformer.strategies.frequent2;

import org.serialthreads.transformer.Strategies;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link org.serialthreads.transformer.strategies.frequent2.FrequentInterruptsTransformer2}.
 */
public class FrequentInterruptsTransformer2_IntegrationTest extends TransformerIntegration_AbstractTest
{
  @Override
  public void setUp()
  {
    strategy = Strategies.FREQUENT2;
    super.setUp();
  }
}
