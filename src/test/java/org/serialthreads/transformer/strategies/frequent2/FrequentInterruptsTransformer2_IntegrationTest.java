package org.serialthreads.transformer.strategies.frequent2;

import org.serialthreads.agent.Transform;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link FrequentInterruptsTransformer2}.
 */
@Transform(transformer = FrequentInterruptsTransformer2.class)
class FrequentInterruptsTransformer2_IntegrationTest extends TransformerIntegration_AbstractTest {
}
