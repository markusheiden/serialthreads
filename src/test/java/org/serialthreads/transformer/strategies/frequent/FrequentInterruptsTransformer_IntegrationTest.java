package org.serialthreads.transformer.strategies.frequent;

import org.serialthreads.agent.Transform;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link FrequentInterruptsTransformer}.
 */
@Transform(transformer = FrequentInterruptsTransformer.class)
class FrequentInterruptsTransformer_IntegrationTest extends TransformerIntegration_AbstractTest {
}
