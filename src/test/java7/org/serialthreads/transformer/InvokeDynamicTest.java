package org.serialthreads.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.TraceClassVisitor;
import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Test of invoke dynamic.
 */
public class InvokeDynamicTest
{
  public static void main(String[] args)
  {
    try
    {
      System.out.println("================================================================================");
      printClass(InvokeDynamicTest.class);

      System.out.println("================================================================================");
      printClass(MethodHandleTestTarget.class);

      System.out.println("================================================================================");
      printClass(SubMethodHandleTestTarget.class);

      System.out.println("================================================================================");
      new InvokeDynamicTest().run();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
  }

  private static void printClass(Class<?> clazz) throws IOException
  {
    ClassReader r = new ClassReader(clazz.getResourceAsStream(clazz.getSimpleName() + ".class"));
    Printer printer = new ASMifier();
    r.accept(new TraceClassVisitor(null, printer, new PrintWriter(System.out)), 0);
  }

  public void run()
  {
    try
    {
      System.out.println("Unbound:");
      MethodHandleTestTarget.overrideHandle.invoke(new MethodHandleTestTarget(), new Stack("", 0), new StackFrame(null, 0));
      SubMethodHandleTestTarget.overriddenHandle.invoke(new SubMethodHandleTestTarget(), new Stack("", 0), new StackFrame(null, 0));
      SubMethodHandleTestTarget.overriddenSuperHandle.invoke(new SubMethodHandleTestTarget(), new Stack("", 0), new StackFrame(null, 0));
      MethodHandleTestTarget.privateHandle.invoke(new MethodHandleTestTarget(), new Stack("", 0), new StackFrame(null, 0));
      MethodHandleTestTarget.privateStaticHandle.invoke(new Stack("", 0), new StackFrame(null, 0));
      System.out.println("Bound:");
      MethodHandleTestTarget.overrideHandle.bindTo(new MethodHandleTestTarget()).invoke(new Stack("", 0), new StackFrame(null, 0));
      SubMethodHandleTestTarget.overriddenHandle.bindTo(new SubMethodHandleTestTarget()).invoke(new Stack("", 0), new StackFrame(null, 0));
      SubMethodHandleTestTarget.overriddenSuperHandle.bindTo(new SubMethodHandleTestTarget()).invoke(new Stack("", 0), new StackFrame(null, 0));
      MethodHandleTestTarget.privateHandle.bindTo(new MethodHandleTestTarget()).invoke(new Stack("", 0), new StackFrame(null, 0));
      MethodHandleTestTarget.privateStaticHandle.invoke(new Stack("", 0), new StackFrame(null, 0));
    }
    catch (Throwable throwable)
    {
      throwable.printStackTrace();
    }
  }
}
