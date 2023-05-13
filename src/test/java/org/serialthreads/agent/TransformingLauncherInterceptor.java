package org.serialthreads.agent;

import org.junit.platform.launcher.LauncherInterceptor;

/**
 * {@link LauncherInterceptor} that applies the {@link TransformingTestClassLoader} to all test classes.
 */
public class TransformingLauncherInterceptor implements LauncherInterceptor {
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
