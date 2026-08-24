package org.serialthreads.agent;

import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static java.lang.String.join;
import static java.util.Arrays.stream;

/**
 * JUnit 5 extension that loads the test class through a {@link TransformingClassLoader}
 * and executes each test method on the transformed instance.
 * <p>
 * Activated automatically via the {@link Transform} meta-annotation.
 * <p>
 * Dynamic tests are lambdas already captured inside {@link #interceptTestFactoryMethod},
 * which already runs on the transformed instance.
 * So {@link #interceptDynamicTest(Invocation, DynamicTestInvocationContext, ExtensionContext)}
 * does not need to be overridden.
 */
@SuppressWarnings("NullableProblems")
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
    public <T> T interceptTestFactoryMethod(
            Invocation<T> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext context) throws Throwable {
        return runOnTransformed(invocation, invocationContext, context);
    }

    @Override
    public void interceptTestTemplateMethod(
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

    @SuppressWarnings("unchecked")
    private <T> T runOnTransformed(
            Invocation<T> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext context) throws Throwable {
        var instance = getOrCreateInstance(context);
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
     */
    private Object getOrCreateInstance(ExtensionContext context) {
        return context.getStore(NAMESPACE)
                .computeIfAbsent(TRANSFORMED_INSTANCE, _ ->
                        createInstance(context), Object.class);
    }

    private Object createInstance(ExtensionContext context) {
        var testClass = context.getRequiredTestClass();
        var classLoader = createTransformingClassLoader(testClass);
        try {
            var constructor = classLoader.loadClass(testClass.getName()).getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create transformed test instance for " + testClass.getName(), e);
        }
    }

    private TransformingClassLoader createTransformingClassLoader(Class<?> testClass) {
        var transform = testClass.getAnnotation(Transform.class);
        return new TransformingClassLoader(
                testClass.getClassLoader(),
                classInfoCache -> createTransformer(transform.transformer(), classInfoCache),
                transform.classPrefixes());
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
