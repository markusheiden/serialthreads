package org.serialthreads.transformer.classcache;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
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

  /**
   * Scan a given class for interruptible methods.
   * Does NOT do a deep scan!
   *
   * @param className name of class to scan
   * @param toProcess further classes to scan, will be filled with super class and interfaces of scanned class
   */
  @Override
  protected ClassInfo scan(String className, Deque<String> toProcess) throws IOException {
    logger.debug("Scanning class {}", className);

    var classFile = classLoader.getResourceAsStream(className + ".class");
    return scan(read(new ClassReader(classFile)), toProcess);
  }
}
