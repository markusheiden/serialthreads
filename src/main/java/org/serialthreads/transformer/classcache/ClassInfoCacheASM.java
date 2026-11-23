package org.serialthreads.transformer.classcache;

import java.io.IOException;
import java.util.Deque;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Checks and caches which methods are marked as interruptible.
 */
public class ClassInfoCacheASM extends AbstractClassInfoCache {
    /**
     * Constructor.
     *
     * @param classLoader
     *         Class loader for loading classes.
     */
    public ClassInfoCacheASM(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected ClassInfo scan(String className, Deque<String> toProcess) throws IOException {
        logger.debug("Scanning class {}", className);

        var classInfo = scanClassFile(className, toProcess);
        if (classInfo == null) {
            throw new IOException("Class file for class " + className + " not found");
        }

        requireThat(classInfo, "classInfo").isNotNull();
        return classInfo;
    }
}
