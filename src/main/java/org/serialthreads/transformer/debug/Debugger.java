package org.serialthreads.transformer.debug;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.serialthreads.transformer.analyzer.ExtendedAnalyzer;
import org.serialthreads.transformer.analyzer.ExtendedFrame;
import org.serialthreads.transformer.classcache.ClassInfoCacheASM;

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
    method.accept(new TraceMethodVisitor(printer));

    StringWriter result = new StringWriter(4096);
    result.append("Method ").append(methodName(clazz.name, method.name, method.desc)).append("\n");
    printer.print(new PrintWriter(result));

    return result.toString();
  }

  /**
   * Byte code.
   */
  public static String debug(byte[] byteCode) {
    StringWriter result = new StringWriter(4096);
    new ClassReader(byteCode).accept(new TraceClassVisitor(new PrintWriter(result)), SKIP_FRAMES + SKIP_DEBUG);
    return result.toString();
  }
}
