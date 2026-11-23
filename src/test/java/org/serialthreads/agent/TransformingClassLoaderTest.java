package org.serialthreads.agent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.serialthreads.transformer.Strategies.DEFAULT;

/**
 * Test for {@link TransformingClassLoader}.
 */
class TransformingClassLoaderTest {
    private static final String TRANSFORMED = AgentTest.TestRunnable.class.getName();
    private static final String UNTRANSFORMED = AgentTest.TestMain.class.getName();

    @Test
    void testParallelCapable() {
        var loader = new TransformingClassLoader(DEFAULT);

        assertThat(loader.isRegisteredAsParallelCapable()).isTrue();
    }

    /**
     * Test that transformed and untransformed classes are defined with the code source and package of their class file.
     */
    @Test
    void testCodeSource() throws Exception {
        var loader = new TransformingClassLoader(DEFAULT);
        var expected = AgentTest.class.getProtectionDomain().getCodeSource().getLocation();

        for (var name : new String[] { TRANSFORMED, UNTRANSFORMED }) {
            var clazz = loader.loadClass(name);

            assertThat(clazz.getClassLoader()).isSameAs(loader);
            assertThat(clazz.getProtectionDomain().getCodeSource().getLocation()).isEqualTo(expected);
            assertThat(clazz.getPackage().getName()).isEqualTo(AgentTest.class.getPackageName());
        }
    }

    /**
     * Test that concurrent loads of the same class define it just once.
     */
    @Test
    void testConcurrentLoad() throws Exception {
        var loader = new TransformingClassLoader(DEFAULT);

        try (var executor = Executors.newFixedThreadPool(8)) {
            var loads = IntStream.range(0, 32)
                    .mapToObj(i -> (Callable<Class<?>>) () -> loader.loadClass(i % 2 == 0 ? TRANSFORMED : UNTRANSFORMED))
                    .toList();
            for (var loaded : executor.invokeAll(loads)) {
                assertThat(loaded.get().getClassLoader()).isSameAs(loader);
            }
        }
    }
}
