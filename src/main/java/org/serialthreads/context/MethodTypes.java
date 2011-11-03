package org.serialthreads.context;

import java.lang.invoke.MethodType;

/**
 * Constants for method types.
 * Helper for byte code enhancement.
 */
public class MethodTypes
{
  public static MethodType VOID_TYPE = MethodType.methodType(void.class, Stack.class, StackFrame.class);
}
