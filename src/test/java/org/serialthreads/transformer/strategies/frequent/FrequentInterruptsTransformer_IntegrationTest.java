package org.serialthreads.transformer.strategies.frequent;

import org.serialthreads.transformer.Strategies;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link org.serialthreads.transformer.strategies.frequent.FrequentInterruptsTransformer}.
 */
public class FrequentInterruptsTransformer_IntegrationTest extends TransformerIntegration_AbstractTest {
  @Override
  public void setUp() {
    strategy = Strategies.FREQUENT;
    super.setUp();
  }
}
