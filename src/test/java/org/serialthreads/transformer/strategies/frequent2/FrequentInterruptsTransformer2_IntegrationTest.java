package org.serialthreads.transformer.strategies.frequent2;

import org.junit.jupiter.api.extension.ExtendWith;
import org.serialthreads.agent.Transform;
import org.serialthreads.agent.TransformingTestInstanceFactory;
import org.serialthreads.transformer.strategies.TransformerIntegration_AbstractTest;

/**
 * Integration-test for {@link org.serialthreads.transformer.strategies.frequent2.FrequentInterruptsTransformer2}.
 */
@ExtendWith(TransformingTestInstanceFactory.class)
@Transform(transformer = FrequentInterruptsTransformer2.class)
public class FrequentInterruptsTransformer2_IntegrationTest extends TransformerIntegration_AbstractTest {
}
