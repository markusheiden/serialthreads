package org.serialthreads.transformer.strategies.frequent3;

import org.junit.jupiter.api.extension.ExtendWith;
import org.serialthreads.agent.Transform;
import org.serialthreads.agent.TransformingTestInstanceFactory;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link FrequentInterruptsTransformer3}.
 */
@ExtendWith(TransformingTestInstanceFactory.class)
@Transform(transformer = FrequentInterruptsTransformer3.class)
class FrequentInterruptsTransformer3_IntegrationTest extends TransformerIntegration_AbstractTest {
}
