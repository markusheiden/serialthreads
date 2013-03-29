package org.serialthreads.transformer;

import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

/**
 * Test target for method handle test in {@link InvokeDynamicTest}.
 */
public class MethodHandleTestTarget {
  public static MethodHandle overrideHandle;
  public static MethodHandle privateHandle;
  public static MethodHandle privateStaticHandle;
  public static MethodHandle varargsHandle;

  static {
    try {
      Lookup lookup = MethodHandles.lookup();
      overrideHandle = lookup.findVirtual(MethodHandleTestTarget.class, "test", StackFrame.METHOD_TYPE);
      privateHandle = lookup.findVirtual(MethodHandleTestTarget.class, "privateTest", StackFrame.METHOD_TYPE);
      privateStaticHandle = lookup.findStatic(MethodHandleTestTarget.class, "privateStaticTest", StackFrame.METHOD_TYPE);
      varargsHandle = lookup.findVirtual(MethodHandleTestTarget.class, "varargsTest", MethodType.methodType(void.class, String[].class));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  void test(Stack stack, StackFrame frame) {
    System.out.println("not overridden");
  }

  private void privateTest(Stack stack, StackFrame frame) {
    System.out.println("private");
  }

  private static void privateStaticTest(Stack stack, StackFrame frame) {
    System.out.println("private static");
  }

  private void varargsTest(String... varargs) {
    System.out.println("varargs " + varargs.length);
  }
}
