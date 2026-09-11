package org.serialthreads.transformer.classcache;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.util.Deque;

/**
 * Checks and caches which methods are marked as interruptible.
 */
public class ClassInfoCacheASM extends AbstractClassInfoCache {
  /**
   * Class loader to load class files.
   */
  private final ClassLoader classLoader;

  /**
   * Constructor.
   *
   * @param classLoader class loader for loading classes.
   */
  public ClassInfoCacheASM(ClassLoader classLoader) {
    assert classLoader != null : "Precondition: classLoader != null";

    this.classLoader = classLoader;
  }

  @Override
  protected ClassInfo scan(String className, Deque<String> toProcess) throws IOException {
    logger.debug("Scanning class {}", className);

    try (var classFile = classLoader.getResourceAsStream(className + ".class")) {
      if (classFile == null) {
        throw new IOException("Class file for class " + className + " not found");
      }
      return scan(read(new ClassReader(classFile)), toProcess);
    }
  }
}
