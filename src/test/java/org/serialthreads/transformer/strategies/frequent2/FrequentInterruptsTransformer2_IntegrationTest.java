package org.serialthreads.transformer.strategies.frequent2;

import org.junit.runner.RunWith;
import org.serialthreads.agent.Transform;
import org.serialthreads.agent.TransformingRunner;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link org.serialthreads.transformer.strategies.frequent2.FrequentInterruptsTransformer2}.
 */
@RunWith(TransformingRunner.class)
@Transform(transformer = FrequentInterruptsTransformer2.class, trace = true)
public class FrequentInterruptsTransformer2_IntegrationTest extends TransformerIntegration_AbstractTest {
}
