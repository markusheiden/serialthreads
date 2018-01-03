package org.serialthreads.transformer.strategies.frequent;

import org.junit.runner.RunWith;
import org.serialthreads.agent.Transform;
import org.serialthreads.agent.TransformingRunner;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link org.serialthreads.transformer.strategies.frequent.FrequentInterruptsTransformer}.
 */
@RunWith(TransformingRunner.class)
@Transform(transformer = FrequentInterruptsTransformer.class)
public class FrequentInterruptsTransformer_IntegrationTest extends TransformerIntegration_AbstractTest {
}
