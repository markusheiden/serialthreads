package org.serialthreads.transformer.strategies.frequent;

import org.junit.jupiter.api.extension.ExtendWith;
import org.serialthreads.agent.Transform;
import org.serialthreads.agent.TransformingTestInstanceFactory;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link org.serialthreads.transformer.strategies.frequent.FrequentInterruptsTransformer}.
 */
@ExtendWith(TransformingTestInstanceFactory.class)
@Transform(transformer = FrequentInterruptsTransformer.class)
public class FrequentInterruptsTransformer_IntegrationTest extends TransformerIntegration_AbstractTest {
}
