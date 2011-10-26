package org.serialthreads.transformer;

/**
 * Integration-test for {@link FrequentInterruptsTransformer2}.
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
