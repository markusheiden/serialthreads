package org.serialthreads.transformer;

import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

/**
 * Test target for method handle test in {@link org.serialthreads.transformer.InvokeDynamicTest}.
 */
public class SubMethodHandleTestTarget extends MethodHandleTestTarget {
  public static MethodHandle overriddenHandle;
  public static MethodHandle overriddenSuperHandle;

  static {
    try {
      Lookup lookup = MethodHandles.lookup();
      overriddenHandle = lookup.findVirtual(SubMethodHandleTestTarget.class, "test", StackFrame.METHOD_TYPE);
      overriddenSuperHandle = lookup.findSpecial(MethodHandleTestTarget.class, "test", StackFrame.METHOD_TYPE, SubMethodHandleTestTarget.class);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  void test(Stack stack, StackFrame frame) {
    System.out.println("overridden");
  }
}
