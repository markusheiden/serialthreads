package org.serialthreads.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.ASMifierClassVisitor;

import java.dyn.CallSite;
import java.dyn.ConstantCallSite;
import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Test of invoke dynamic.
 */
public class InvokeDynamicTest
{
/*
  static
  {
    Linkage.registerBootstrapMethod("bootstrap");
  }
*/
  public static void main(String[] args)
  {
    try
    {
      ClassReader r = new ClassReader(InvokeDynamicTest.class.getResourceAsStream(InvokeDynamicTest.class.getSimpleName() + ".class"));
      r.accept(new ASMifierClassVisitor(new PrintWriter(System.out)), 0);
      new InvokeDynamicTest().run();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
  }

  public void run()
  {
    try
    {
      MethodHandle handle1a = MethodHandles.lookup().findVirtual(Callee.class, "run1", MethodType.methodType(void.class, String.class));
      handle1a.invokeExact(new Callee("callee1a"), "?");
      MethodHandle handle1b = handle1a.bindTo(new Callee("callee1b"));
      handle1b.invokeExact("?");
      MethodHandle handle2 = MethodHandles.lookup().bind(new Callee("callee2"), "run1", MethodType.methodType(void.class, String.class));
      handle2.invokeGeneric("!");
      handle2.invokeExact("!!");
    }
    catch (Throwable throwable)
    {
      throwable.printStackTrace();
    }
  }

  public static void run2()
  {
    try
    {
      MethodHandle target = MethodHandles.lookup().findVirtual(Callee.class, "run2", MethodType.methodType(void.class, String.class, String.class));
      target = MethodHandles.insertArguments(target, 0, new Callee("dummy"));
      target = MethodHandles.insertArguments(target, 1, "X");
      CallSite site = new ConstantCallSite(target);

      site.dynamicInvoker().invokeExact("Q");
    }
    catch (Throwable throwable)
    {
      throwable.printStackTrace();
    }
  }

  public static class Callee
  {
    private final String name;

    public Callee(String name)
    {
      this.name = name;
    }

    public void run1(String suffix)
    {
      System.out.println(name + ":run1" + suffix);
      System.out.flush();
    }

    public void run2(String suffix, String extra)
    {
      System.out.println(name + ":run2" + suffix + extra);
      System.out.flush();
    }
  }
}
