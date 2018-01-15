package org.serialthreads.transformer.strategies.frequent4;

import org.junit.runner.RunWith;
import org.serialthreads.agent.Transform;
import org.serialthreads.agent.TransformingRunner;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link FrequentInterruptsTransformer4}.
 */
@RunWith(TransformingRunner.class)
@Transform(transformer = FrequentInterruptsTransformer4.class)
public class FrequentInterruptsTransformer4_IntegrationTest extends TransformerIntegration_AbstractTest {
}