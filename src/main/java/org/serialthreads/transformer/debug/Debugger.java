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
 * Class to aid debugging of a method's byte code.
 */
public class Debugger {
  public static String debug(ClassNode clazz) {
    StringBuilder result = new StringBuilder(65536);
    result.append("Class ").append(clazz.name).append("\n");
    result.append(debug(clazz, (String) null));
    return result.toString();
  }

  public static String debug(ClassNode clazz, String methodToDebug) {
    StringBuilder result = new StringBuilder(65536);
    for (MethodNode method : clazz.methods) {
      if (methodToDebug == null || method.name.startsWith(methodToDebug)) {
        result.append(debug(clazz, method));
      }
    }

    return result.toString();
  }

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
    PrintWriter writer = new PrintWriter(result);
    printer.print(writer);
    writer.flush();

    return result.toString();
  }

  /**
   * Byte code as its string representation.
   */
  public static String debug(byte[] result) {
    StringWriter output = new StringWriter(4096);
    new ClassReader(result).accept(new TraceClassVisitor(new PrintWriter(output)), SKIP_FRAMES + SKIP_DEBUG);
    return output.toString();
  }
}
