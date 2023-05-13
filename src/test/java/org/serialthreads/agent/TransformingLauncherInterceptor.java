package org.serialthreads.agent;

import org.junit.platform.launcher.LauncherInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link LauncherInterceptor} that applies the {@link TransformingTestClassLoader} to all test classes.
 */
public class TransformingLauncherInterceptor implements LauncherInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(TransformingLauncherInterceptor.class);

    public TransformingLauncherInterceptor() {
        logger.info("Intercepting launches.");
    }

    @Override
    public <T> T intercept(Invocation<T> invocation) {
        var currentThread = Thread.currentThread();
        var originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(new TransformingTestClassLoader(originalClassLoader));
        try {
            return invocation.proceed();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    @Override
    public void close() {
        // Nothing to do.
    }
}
