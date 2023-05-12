package org.serialthreads.transformer.strategies.frequent4;

import org.junit.jupiter.api.extension.ExtendWith;
import org.serialthreads.agent.Transform;
import org.serialthreads.agent.TransformingTestInstanceFactory;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link FrequentInterruptsTransformer4}.
 */
@ExtendWith(TransformingTestInstanceFactory.class)
@Transform(transformer = FrequentInterruptsTransformer4.class)
class FrequentInterruptsTransformer4_IntegrationTest extends TransformerIntegration_AbstractTest {
}
