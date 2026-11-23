package org.serialthreads.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

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

  /**
   * Read a class to ASM.
   */
  public static ClassNode readClass(Class<?> clazz) throws IOException {
    return readClass(byteCode(clazz));
  }

  /**
   * Read a class to ASM.
   */
  public static ClassNode readClass(byte[] byteCode) {
    var classNode = new ClassNode();
    new ClassReader(byteCode).accept(classNode, 0);
    return classNode;
  }

  /**
   * Write an ASM class.
   */
  public static byte[] writeClass(ClassNode clazz) {
    var writer = new ClassWriter(0);
    clazz.accept(writer);
    return writer.toByteArray();
  }
}
