package org.serialthreads.transformer;

/**
 * Integration-test for {@link FrequentInterruptsTransformer}.
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
