package org.serialthreads.transformer.debug;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SimpleVerifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.serialthreads.transformer.code.MethodCode;

import java.io.PrintWriter;
import java.io.StringWriter;

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
        result.append(debug(clazz.name, method));
      }
    }

    return result.toString();
  }

  public static String debug(ClassNode clazz, MethodNode method) {
    return debug(clazz.name, method);
  }

  public static String debug(String owner, MethodNode method) {
    Analyzer<BasicValue> analyzer = new Analyzer<>(new SimpleVerifier());
    try {
      analyzer.analyze(owner, method);
    } catch (Exception e) {
      // ignore, we are only interested in the frames
    }
    Frame[] frames = analyzer.getFrames();

    DebugPrinter printer = new DebugPrinter(frames);
    method.accept(new TraceMethodVisitor(printer));

    StringWriter result = new StringWriter(4096);
    result.append("Method ").append(MethodCode.methodName(owner, method.name, method.desc)).append("\n");
    PrintWriter writer = new PrintWriter(result);
    printer.print(writer);
    writer.flush();

    return result.toString();
  }
}
