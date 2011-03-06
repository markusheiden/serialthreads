package org.serialthreads.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.ASMifierClassVisitor;

import java.dyn.CallSite;
import java.dyn.InvokeDynamic;
import java.dyn.JavaMethodHandle;
import java.dyn.Linkage;
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
  static
  {
    Linkage.registerBootstrapMethod("bootstrap");
  }

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
      MethodHandle handle1 = MethodHandles.lookup().findVirtual(Callee.class, "run1", MethodType.methodType(void.class, String.class));
//      handle1.invokeGeneric(new Callee("callee1"), "?");
      handle1.<void>invoke(new Callee("callee1"), "?");
      MethodHandle handle2 = MethodHandles.lookup().bind(new Callee("callee2"), "run1", MethodType.methodType(void.class, String.class));
//      handle2.invokeGeneric("!");
      handle2.<void>invoke("!!");
//      handle2.<void>invokeExact("!!");

//      InvokeDynamic.<void>run1("...");
    }
    catch (Throwable throwable)
    {
      throwable.printStackTrace();
    }
  }

  public static CallSite bootstrap(Class<?> clazz, String name, MethodType methodType)
  {
    System.out.println("bootstrap: " + clazz.getName() + " / " + name + " / " + methodType.toString());
    System.out.flush();
    CallSite result = new CallSite(clazz, name, methodType);
    MethodHandle target = MethodHandles.lookup().findVirtual(Callee.class, "run2", MethodType.methodType(void.class, String.class, String.class));
    target = MethodHandles.insertArguments(target, 0, new Callee("dummy"));
    target = MethodHandles.insertArguments(target, 1, "X");
    result.setTarget(target);
    return result;
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
