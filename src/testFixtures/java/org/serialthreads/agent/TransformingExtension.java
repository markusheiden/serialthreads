package org.serialthreads.agent;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.serialthreads.transformer.IStrategy;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * JUnit 5 extension that loads the test class through a {@link TransformingClassLoader}
 * and executes each test method on the transformed instance.
 *
 * <p>Activated automatically via the {@link Transform} meta-annotation.
 */
public class TransformingExtension implements InvocationInterceptor {
    private static final Namespace NAMESPACE = Namespace.create(TransformingExtension.class);
    private static final String TRANSFORMED_INSTANCE = "transformedInstance";

    @Override
    public void interceptBeforeEachMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext context) throws Throwable {
        runOnTransformed(invocation, invocationContext, context);
    }

    @Override
    public void interceptTestMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext context) throws Throwable {
        runOnTransformed(invocation, invocationContext, context);
    }

    @Override
    public void interceptAfterEachMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext context) throws Throwable {
        runOnTransformed(invocation, invocationContext, context);
    }

    private void runOnTransformed(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext context) throws Throwable {
        var instance = getOrCreateInstance(context);
        var method = findMethod(instance.getClass(), invocationContext.getExecutable());
        invocation.skip();
        try {
            method.invoke(instance);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Returns the transformed test instance for the current test method execution,
     * creating it on first access and caching it for reuse by {@code @BeforeEach} / {@code @AfterEach}.
     */
    private Object getOrCreateInstance(ExtensionContext context) {
        return context.getStore(NAMESPACE).computeIfAbsent(TRANSFORMED_INSTANCE, _ -> {
            var testClass = context.getRequiredTestClass();
            var transform = testClass.getAnnotation(Transform.class);
            var classLoader = new TransformingClassLoader(
                    testClass.getClassLoader(),
                    new Strategy(transform.transformer()),
                    transform.classPrefixes());
            try {
                var constructor = classLoader.loadClass(testClass.getName()).getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create transformed test instance for " + testClass.getName(), e);
            }
        }, Object.class);
    }

    /**
     * Finds the method on the transformed class by name and arity.
     * Walks up the class hierarchy to cover methods inherited from superclasses.
     */
    private Method findMethod(Class<?> clazz, Method original) {
        // Try exact match first (parameter types from the same classloader)
        try {
            var method = clazz.getDeclaredMethod(original.getName(), original.getParameterTypes());
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
        }
        // Fall back to name + arity match (parameter types may differ by classloader)
        for (var method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(original.getName())
                    && method.getParameterCount() == original.getParameterCount()) {
                method.setAccessible(true);
                return method;
            }
        }
        var superclass = clazz.getSuperclass();
        if (superclass != null) {
            return findMethod(superclass, original);
        }
        throw new IllegalStateException("Method not found in transformed class: " + original.getName());
    }

    private record Strategy(Class<? extends ITransformer> transformerClass) implements IStrategy {
        @Override
        public ITransformer getTransformer(IClassInfoCache classInfoCache) {
            try {
                return transformerClass.getConstructor(IClassInfoCache.class).newInstance(classInfoCache);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid transformer: " + transformerClass.getName(), e);
            }
        }
    }
}
