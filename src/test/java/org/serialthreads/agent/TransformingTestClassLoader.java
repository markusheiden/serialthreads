package org.serialthreads.agent;

import org.objectweb.asm.ClassReader;
import org.serialthreads.transformer.IStrategy;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static org.objectweb.asm.ClassReader.SKIP_CODE;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

/**
 * A {@link ClassLoader} that delegates to a {@link TransformingClassLoader}
 * for (test) classes annotated with {@link Transform}.
 */
class TransformingTestClassLoader extends ClassLoader {
    private static final Logger logger = LoggerFactory.getLogger(TransformingTestClassLoader.class);

    TransformingTestClassLoader(ClassLoader parent) {
        super("test", parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        var transform = findTransform(name);
        if (transform == null) {
            var clazz = super.loadClass(name, resolve);
            var classLoader = clazz.getClassLoader();
            var classLoaderName = classLoader != null ? classLoader.getName() : "bootstrap";
            logger.info("{}/{}: Loading without transformation.", classLoaderName, clazz.getName());
            return clazz;
        }

        logger.info("{}: Transforming.", name);
        var classLoader = new TransformingClassLoader(new Strategy(transform.transformer()), transform.classPrefixes());
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

        var transform = findTransform(classFile);
        if (transform == null) {
            logger.info("{}: Transform not found.", name);
            return null;
        }

        logger.info("{}: Transform found.", name);
        return transform;
    }

    private TransformAnnotation findTransform(InputStream classFile) throws ClassNotFoundException {
        try {
            var classReader = new ClassReader(classFile);
            var visitor = new TransformAnnotationVisitor();
            classReader.accept(visitor, SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES);
            return visitor.getTransform();
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