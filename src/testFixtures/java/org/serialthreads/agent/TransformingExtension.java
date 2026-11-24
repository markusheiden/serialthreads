package org.serialthreads.agent;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.join;
import static java.util.Arrays.stream;

/**
 * JUnit 5 extension that loads the test class through a {@link TransformingClassLoader}
 * and executes each test method on the transformed instance.
 * <p>
 * Activated automatically via the {@link Transform} meta-annotation.
 * {@code @Nested} test classes are supported, they use the {@link Transform} of their enclosing class by default.
 * <p>
 * Dynamic tests are lambdas already captured inside {@link #interceptTestFactoryMethod},
 * which already runs on the transformed instance.
 * So {@link #interceptDynamicTest(Invocation, DynamicTestInvocationContext, ExtensionContext)}
 * does not need to be overridden.
 */
public class TransformingExtension implements InvocationInterceptor {
    private static final Namespace NAMESPACE = Namespace.create(TransformingExtension.class);
    private static final String TRANSFORMED_INSTANCES = "transformedInstances";

    @Override
    public void interceptBeforeEachMethod(
            Invocation<@Nullable Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        runOnTransformed(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptTestMethod(
            Invocation<@Nullable Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        runOnTransformed(invocation, invocationContext, extensionContext);
    }

    @Override
    public <T extends @Nullable Object> T interceptTestFactoryMethod(
            Invocation<T> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        return runOnTransformed(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptTestTemplateMethod(
            Invocation<@Nullable Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        runOnTransformed(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptAfterEachMethod(
            Invocation<@Nullable Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        runOnTransformed(invocation, invocationContext, extensionContext);
    }

    @SuppressWarnings("unchecked")
    private <T extends @Nullable Object> T runOnTransformed(
            Invocation<T> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        var instance = getOrCreateInstance(invocationContext, extensionContext);
        var method = findMethod(instance.getClass(), invocationContext.getExecutable());
        // Do NOT call the test method of the untransformed instance.
        invocation.skip();
        try {
            // Invoke the transformed test method instead, passing the arguments along, e.g. for parameterized tests.
            // Arguments of types loaded by the transforming class loader are not supported,
            // because the arguments have been resolved with the original class loader.
            return (T) method.invoke(instance, invocationContext.getArguments().toArray());
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Returns the transformed test instance for the current test method execution,
     * creating it on first access and caching it for reuse by {@code @BeforeEach} / {@code @AfterEach}.
     * <p>
     * For {@code @Nested} test classes the transformed instance of the class is returned, the method is invoked on:
     * The lifecycle methods of the enclosing classes are invoked on the enclosing instances.
     */
    private Object getOrCreateInstance(
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) {
        var testClass = extensionContext.getRequiredTestClass();
        var originals = extensionContext.getRequiredTestInstances().getAllInstances();
        var instances = extensionContext
                .getStore(NAMESPACE)
                .computeIfAbsent(TRANSFORMED_INSTANCES, _ -> createInstances(testClass, originals), List.class);
        var target = invocationContext.getTarget().orElse(null);
        for (var i = 0; i < originals.size(); i++) {
            if (originals.get(i) == target) {
                return instances.get(i);
            }
        }

        return instances.getLast();
    }

    /**
     * Creates the transformed test instances: The enclosing instances of {@code @Nested} test classes first,
     * the instance of the test class itself last. All are loaded by the same transforming class loader.
     */
    private List<Object> createInstances(Class<?> testClass, List<Object> originals) {
        try {
            var classLoader = createTransformingClassLoader(testClass);
            var instances = new ArrayList<>();
            for (var original : originals) {
                createInstance(original, classLoader, instances);
            }
            return instances;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create transformed test instance for " + testClass.getName(), e);
        }
    }

    private void createInstance(Object original, TransformingClassLoader classLoader, List<Object> instances)
            throws ReflectiveOperationException {
        var clazz = classLoader.loadClass(original.getClass().getName());
        if (instances.isEmpty()) {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            instances.add(constructor.newInstance());
        } else {
            // The constructor of an inner class takes its enclosing instance.
            var constructor = clazz.getDeclaredConstructor(clazz.getEnclosingClass());
            constructor.setAccessible(true);
            instances.add(constructor.newInstance(instances.getLast()));
        }
    }

    private TransformingClassLoader createTransformingClassLoader(Class<?> testClass) {
        var transform = findTransform(testClass);
        return new TransformingClassLoader(
                testClass.getClassLoader(),
                classInfoCache -> createTransformer(transform.transformer(), classInfoCache),
                transform.classPrefixes());
    }

    /**
     * Finds the transformation parameters at the test class or, for {@code @Nested} test classes,
     * at one of its enclosing classes.
     */
    private Transform findTransform(Class<?> testClass) {
        for (var clazz = testClass; clazz != null; clazz = clazz.getEnclosingClass()) {
            var transform = clazz.getAnnotation(Transform.class);
            if (transform != null) {
                return transform;
            }
        }

        throw new IllegalStateException("No @Transform found for " + testClass.getName());
    }

    private ITransformer createTransformer(
            Class<? extends ITransformer> transformerClass,
            IClassInfoCache classInfoCache) {
        try {
            return transformerClass
                    .getConstructor(IClassInfoCache.class)
                    .newInstance(classInfoCache);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid transformer " + transformerClass.getName(), e);
        }
    }

    /**
     * Finds the method on the transformed class by name and parameter type names.
     * Comparing type names (rather than {@link Class} identity) handles the case where
     * parameter types were loaded by different {@link ClassLoader}s.
     * Walks up the class hierarchy to cover methods inherited from superclasses.
     */
    private Method findMethod(Class<?> clazz, Method original) {
        var name = original.getName();
        var parameterTypeNames = parameterTypeNames(original);
        for (var method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(name) &&
                parameterTypeNames(method).equals(parameterTypeNames)) {
                method.setAccessible(true);
                return method;
            }
        }

        // Search at super class, if any.
        var superclass = clazz.getSuperclass();
        if (superclass == null) {
            throw new IllegalStateException("Method %s#%s(%s) not found.".formatted(
                    clazz.getName(), name, join(", ", parameterTypeNames)));
        }

        return findMethod(superclass, original);
    }

    private List<String> parameterTypeNames(Method method) {
        return stream(method.getParameterTypes())
                .map(Class::getName)
                .toList();
    }
}
