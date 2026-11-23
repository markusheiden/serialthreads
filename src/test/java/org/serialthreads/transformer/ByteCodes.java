package org.serialthreads.transformer;

import java.io.IOException;

/**
 * Access to the byte code of classes for tests.
 */
public final class ByteCodes {
  /**
   * Load the untransformed byte code of a class.
   *
   * @param clazz Class
   */
  public static byte[] byteCode(Class<?> clazz) throws IOException {
    var classFile = clazz.getName().replace('.', '/') + ".class";
    try (var in = clazz.getClassLoader().getResourceAsStream(classFile)) {
      return in.readAllBytes();
    }
  }
}
