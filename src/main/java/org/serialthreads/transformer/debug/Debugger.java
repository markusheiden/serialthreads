package org.serialthreads.transformer.debug;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.serialthreads.transformer.analyzer.ExtendedAnalyzer;
import org.serialthreads.transformer.analyzer.ExtendedFrame;
import org.serialthreads.transformer.classcache.ClassInfoCacheASM;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;
import static org.serialthreads.transformer.code.MethodCode.methodName;

/**
 * Class to aid debugging of byte code.
 */
public class Debugger {
  /**
   * Byte code of all methods of a class.
   *
   * @param className Fully qualified class name.
   */
  public static String debug(String className) {
    try {
      return debug(new ClassReader(className));
    } catch (IOException e) {
      return "Class " + className + " not found.";
    }
  }

  /**
   * Byte code of all methods of a class.
   *
   * @param byteCode Byte code of the class.
   */
  public static String debug(byte[] byteCode) {
    return debug(new ClassReader(byteCode));
  }

  /**
   * Byte code of all methods of a class.
   *
   * @param classReader Class reader with the class.
   */
  private static String debug(ClassReader classReader) {
    ClassNode clazz = new ClassNode();
    classReader.accept(clazz, SKIP_DEBUG + SKIP_FRAMES);
    return debug(clazz);
  }

  /**
   * Byte code of all methods of a class.
   *
   * @param clazz Class node.
   */
  public static String debug(ClassNode clazz) {
    StringBuilder result = new StringBuilder(65536);
    result.append("Class ").append(clazz.name).append("\n");
    clazz.methods.stream()
      .map(method -> debug(clazz, method))
      .forEach(result::append);
    return result.toString();
  }

  /**
   * Byte code of a method of a class.
   *
   * @param clazz Class node.
   * @param methodToDebug Name of method to debug.
   */
  public static String debug(ClassNode clazz, String methodToDebug) {
    StringBuilder result = new StringBuilder(65536);
    clazz.methods.stream()
      .filter(method -> method.name.startsWith(methodToDebug))
      .map(method -> debug(clazz, method))
      .forEach(result::append);
    return result.toString();
  }

  /**
   * Byte code of a method of a class.
   *
   * @param clazz Class node.
   * @param method Method node.
   */
  public static String debug(ClassNode clazz, MethodNode method) {
    ExtendedAnalyzer analyzer = ExtendedAnalyzer.create(clazz, new ClassInfoCacheASM(Debugger.class.getClassLoader()));
    try {
      analyzer.analyze(clazz.name, method);
    } catch (Exception e) {
      // Ignore, we are only interested in the frames.
    }
    ExtendedFrame[] frames = analyzer.getFrames();

    DebugPrinter printer = new DebugPrinter(frames);
    try {
      method.accept(new TraceMethodVisitor(printer));
    } catch (Exception e) {
      // Ignore, to get at least the already generated output.
    }

    StringWriter result = new StringWriter(4096);
    result.append("Method ").append(methodName(clazz.name, method.name, method.desc)).append("\n");
    printer.print(new PrintWriter(result));

    return result.toString();
  }
}
