package org.serialthreads.transformer.code;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Value;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.SerialThreadManager;
import org.serialthreads.transformer.analyzer.ExtendedValue;
import org.serialthreads.transformer.classcache.IClassInfoCache;

/**
 * Method related code.
 */
public class MethodCode
{
  private static final String IRUNNABLE_NAME = Type.getType(IRunnable.class).getInternalName();
  private static final String MANAGER_NAME = Type.getType(SerialThreadManager.class).getInternalName();

  //
  // method call specific code
  //

  /**
   * Is a not static method been called?.
   *
   * @param methodCall method call
   */
  public static boolean isNotStatic(MethodInsnNode methodCall)
  {
    return methodCall.getOpcode() != Opcodes.INVOKESTATIC;
  }

  /**
   * Is a not void method been called?.
   *
   * @param methodCall method call
   */
  public static boolean isNotVoid(MethodInsnNode methodCall)
  {
    return !Type.getReturnType(methodCall.desc).equals(Type.VOID_TYPE);
  }

  /**
   * Is the call a call to a method on the same object (this)?
   *
   * @param methodCall method call
   * @param frameBefore frame directly before method call
   */
  public static boolean isSelfCall(MethodInsnNode methodCall, Frame frameBefore)
  {
    if (methodCall.getOpcode() == Opcodes.INVOKESTATIC)
    {
      // static methods have no owner
      return false;
    }

    // "pop" all arguments from stack
    Type[] argumentTypes = Type.getArgumentTypes(methodCall.desc);
    int s = frameBefore.getStackSize();
    for (int i = argumentTypes.length - 1; i >= 0; i--)
    {
      s -= argumentTypes[i].getSize();
      assert s > 0 : "Check: stack pointer is positive";
    }

    Value ownerType = frameBefore.getStack(s - 1);
    return ownerType instanceof ExtendedValue && ((ExtendedValue) ownerType).getLocals().contains(0);
  }

  /**
   * Check if the called method is IRunnable.run().
   *
   * @param methodCall method call
   * @param classInfoCache classInfoCache
   */
  public static boolean isRun(MethodInsnNode methodCall, IClassInfoCache classInfoCache)
  {
    return
      classInfoCache.hasSuperClass(methodCall.owner, IRUNNABLE_NAME) &&
        methodCall.name.equals("run") &&
        methodCall.desc.equals("()V");
  }

  /**
   * Check if the called method is an interrupt.
   *
   * @param methodCall method call
   * @param classInfoCache classInfoCache
   */
  public static boolean isInterrupt(MethodInsnNode methodCall, IClassInfoCache classInfoCache)
  {
    // TODO 2010-03-15 mh: make sure that interrupt method has descriptor ()V!
    return classInfoCache.isInterrupt(methodCall);
  }

  /**
   * Create dummy arguments for call to the givem method for restoring code.
   * Creates appropriate argument values for a method call with 0 or null values.
   *
   * @param method method node to create arguments for
   */
  public static InsnList dummyArguments(MethodInsnNode method)
  {
    InsnList instructions = new InsnList();

    for (Type type : Type.getArgumentTypes(method.desc))
    {
      instructions.add(ValueCodeFactory.code(type).pushNull());
    }

    return instructions;
  }

  //
  // class specific code
  //

  /**
   * Is the class an interface?.
   *
   * @param clazz class
   */
  public static boolean isInterface(ClassNode clazz)
  {
    return (clazz.access & Opcodes.ACC_INTERFACE) != 0;
  }

  //
  // method specific code
  //

  /**
   * Check if method is static.
   *
   * @param method method
   */
  public static boolean isStatic(MethodNode method)
  {
    return (method.access & Opcodes.ACC_STATIC) != 0;
  }

  /**
   * Check if method is not static.
   *
   * @param method method
   */
  public static boolean isNotStatic(MethodNode method)
  {
    return (method.access & Opcodes.ACC_STATIC) == 0;
  }

  /**
   * Is the method abstract?.
   *
   * @param method method
   */
  public static boolean isAbstract(MethodNode method)
  {
    return (method.access & Opcodes.ACC_ABSTRACT) != 0;
  }

  /**
   * Check if the method is IRunnable.run().
   *
   * @param clazz owner of method
   * @param method method
   * @param classInfoCache class info cache
   */
  public static boolean isRun(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache)
  {
    // TODO 2009-11-04 mh: remove special handling for clazz == null
    return
      (clazz == null || classInfoCache.hasSuperClass(clazz.name, IRUNNABLE_NAME)) &&
        method.name.equals("run") &&
        method.desc.equals("()V");
  }

  /**
   * The first local of a method that is no parameter.
   *
   * @param method method
   */
  public static int firstLocal(MethodNode method)
  {
    Type[] types = Type.getArgumentTypes(method.desc);
    int local = isNotStatic(method) ? 1 : 0;
    for (Type type : types)
    {
      local += type.getSize();
    }

    return local;
  }

  /**
   * Create dummy return statement for capturing code.
   * Creates appropriate return statement for a method with a 0, null or void return value.
   *
   * @param method method node to create return statement for
   */
  public static InsnList dummyReturnStatement(MethodNode method)
  {
    Type returnType = Type.getReturnType(method.desc);
    if (returnType.getSort() == Type.VOID)
    {
      InsnList instructions = new InsnList();
      instructions.add(new InsnNode(Opcodes.RETURN));
      return instructions;
    }

    return ValueCodeFactory.code(returnType).returnNull();
  }

  /**
   * Check if frame is still compatible with the method arguments.
   *
   * @param method method
   * @param frame frame check
   */
  public static boolean isCompatible(MethodNode method, Frame frame)
  {
    Type[] arguments = Type.getArgumentTypes(method.desc);
    int a = 0;
    for (int l = isNotStatic(method) ? 1 : 0; l < frame.getLocals() && a < arguments.length; a++)
    {
      BasicValue local = (BasicValue) frame.getLocal(l);
      if (BasicValue.UNINITIALIZED_VALUE.equals(local))
      {
        throw new IllegalArgumentException("Locals have to be initialized at least with arguments");
      }

      Type argument = arguments[a];
      if (!ValueCodeFactory.code(local).isCompatibleWith(argument))
      {
        return false;
      }

      l += argument.getSize();
    }

    if (a != arguments.length)
    {
      // there had to be always at least as many locals as arguments,
      // because the arguments are always put into locals before a method is entered
      throw new IllegalArgumentException("Less locals than arguments");
    }

    // scanned all arguments and they passed the test
    return true;
  }

  //
  // Log / debug code
  //

  /**
   * Generate identification string for a method.
   *
   * @param clazz owning class
   * @param method method
   */
  public static String methodName(ClassNode clazz, MethodNode method)
  {
    return methodName(clazz.name, method.name, method.desc);
  }

  /**
   * Generate identification string for a method call.
   *
   * @param method method call
   */
  public static String methodName(MethodInsnNode method)
  {
    return methodName(method.owner, method.name, method.desc);
  }

  /**
   * Generate identification string for a method.
   *
   * @param owner owning class
   * @param name method name
   * @param desc method descriptor
   */
  public static String methodName(String owner, String name, String desc)
  {
    return owner + "#" + name + desc;
  }
}
