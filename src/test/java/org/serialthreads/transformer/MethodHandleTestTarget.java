package org.serialthreads.transformer;

import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.invoke.MethodType.methodType;

/**
 * Test target for method handle test in {@link InvokeDynamicTest}.
 */
class MethodHandleTestTarget {
    static final MethodType METHOD_TYPE = methodType(void.class, Stack.class, StackFrame.class);

    public static MethodHandle overrideHandle;
    public static MethodHandle privateHandle;
    public static MethodHandle privateStaticHandle;
    public static MethodHandle varargsHandle;

    static {
        try {
            var lookup = MethodHandles.lookup();
            overrideHandle = lookup.findVirtual(MethodHandleTestTarget.class, "test", METHOD_TYPE);
            privateHandle = lookup.findVirtual(MethodHandleTestTarget.class, "privateTest", METHOD_TYPE);
            privateStaticHandle = lookup.findStatic(MethodHandleTestTarget.class, "privateStaticTest", METHOD_TYPE);
            varargsHandle = lookup.findVirtual(MethodHandleTestTarget.class, "varargsTest", methodType(void.class, String[].class));
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
