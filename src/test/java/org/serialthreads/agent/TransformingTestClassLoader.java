package org.serialthreads.agent;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.serialthreads.transformer.IStrategy;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.objectweb.asm.ClassReader.SKIP_CODE;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

/**
 * A {@link ClassLoader} that delegates to a {@link TransformingClassLoader}
 * for (test) classes annotated with {@link Transform}.
 */
class TransformingTestClassLoader extends ClassLoader {
    private static final Logger logger = LoggerFactory.getLogger(TransformingTestClassLoader.class);

    private final Map<String, String> superClassNames = new HashMap<>();
    private final Map<String, TransformAnnotation> transforms = new HashMap<>();

    TransformingTestClassLoader(ClassLoader parent) {
        super("test", parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        var transform = transforms.get(name);
        if (transform == null) {
            transform = findTransform(name);
        }
        if (transform == null) {
            return loadClassUntransformed(name, resolve);
        }
        return loadClassTransformed(name, resolve, transform);
    }

    private Class<?> loadClassUntransformed(String name, boolean resolve)
            throws ClassNotFoundException {
        var clazz = super.loadClass(name, resolve);
        var classLoader = clazz.getClassLoader();
        var classLoaderName = classLoader != null ? classLoader.getName() : "bootstrap";
        logger.info("{}/{}: Loaded without transformation.", classLoaderName, clazz.getName());
        return clazz;
    }

    private Class<?> loadClassTransformed(String name, boolean resolve, TransformAnnotation transform)
            throws ClassNotFoundException {
        logger.info("{}: Transforming.", name);
        var classLoader = new TransformingClassLoader(getParent(), new Strategy(transform.transformer()), transform.classPrefixes());
        return classLoader.loadClass(name, resolve);
    }

    /**
     * Find {@link Transform} annotation at a (test) class.
     *
     * @param name Class name.
     * @return Annotation, or {@code null} if none.
     * @throws ClassNotFoundException on any class loading problems.
     */
    private TransformAnnotation findTransform(String name) throws ClassNotFoundException {
        var classFile = getResourceAsStream(name.replace('.', '/') + ".class");
        if (classFile == null) {
            logger.info("{}: Class not found.", name);
            return null;
        }

        var transform = findTransform(name, classFile);
        for (var className = name; transform == null; transform = findTransform(className)) {
            className = superClassNames.get(className);
            if (className == null) {
                break;
            }
        }
        if (transform == null) {
            logger.info("{}: Transform not found.", name);
            return null;
        }

        logger.info("{}: Transform found.", name);
        return transform;
    }

    /**
     * Find {@link Transform} annotation and populated {@link #superClassNames} and {@link #transforms}.
     */
    private TransformAnnotation findTransform(String name, InputStream classFile) throws ClassNotFoundException {
        var visitor = new TransformAnnotationVisitor();

        visitClass(classFile, visitor);

        superClassNames.put(name, visitor.getSuperClassName());
        var transform = visitor.getTransform();
        transforms.put(name, transform);
        return transform;
    }

    private void visitClass(InputStream classFile, TransformAnnotationVisitor visitor) throws ClassNotFoundException {
        try {
            var classReader = new ClassReader(classFile);
            classReader.accept(visitor, SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES);
        } catch (IllegalArgumentException e) {
            throw new ClassNotFoundException("Invalid class.", e);
        } catch (IOException e) {
            throw new ClassNotFoundException("Failed to read class.", e);
        }
    }

    /**
     * Transformation strategy.
     *
     * @param transformerClass Transformer class.
     */
    private record Strategy(Class<? extends ITransformer> transformerClass) implements IStrategy {
        @Override
        public ITransformer getTransformer(IClassInfoCache classInfoCache) {
            try {
                return transformerClass.getConstructor(IClassInfoCache.class).newInstance(classInfoCache);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid transformer specified.", e);
            }
        }
    }
}