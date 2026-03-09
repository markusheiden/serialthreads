package org.serialthreads.transformer.strategies.frequent3;

import org.junit.jupiter.api.Test;
import org.serialthreads.agent.Transform;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link FrequentInterruptsTransformer3}.
 */
@Transform(transformer = FrequentInterruptsTransformer3.class)
class FrequentInterruptsTransformer3_IntegrationTest extends TransformerIntegration_AbstractTest {
  @Test
  @Override
  protected void testException() {
    super.testException();
  }
}
