package org.serialthreads.transformer.code;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Value;
import org.serialthreads.context.IRunnable;
import org.serialthreads.transformer.analyzer.ExtendedValue;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.MetaInfo;

import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.RETURN;

/**
 * Method related code.
 */
public class MethodCode {
  private static final String IRUNNABLE_NAME = Type.getType(IRunnable.class).getInternalName();

  //
  // method call specific code
  //

  /**
   * Is a not static method been called?.
   *
   * @param methodCall method call
   */
  public static boolean isNotStatic(MethodInsnNode methodCall) {
    return methodCall.getOpcode() != Opcodes.INVOKESTATIC;
  }

  /**
   * Is a not void method been called?.
   *
   * @param methodCall method call
   */
  public static boolean isNotVoid(MethodInsnNode methodCall) {
    return !Type.getReturnType(methodCall.desc).equals(Type.VOID_TYPE);
  }

  /**
   * Is the call a call to a method on the same object (this)?
   *
   * @param methodCall method call
   * @param metaInfo Meta information about method call
   */
  public static boolean isSelfCall(MethodInsnNode methodCall, MetaInfo metaInfo) {
    if (methodCall.getOpcode() == Opcodes.INVOKESTATIC) {
      // static methods have no owner
      return false;
    }

    Frame frameBefore = metaInfo.frameBefore;

    // "pop" all arguments from stack
    Type[] argumentTypes = Type.getArgumentTypes(methodCall.desc);
    int s = frameBefore.getStackSize() - argumentTypes.length;
    assert s > 0 : "Check: stack pointer is positive";
    Value ownerType = frameBefore.getStack(s - 1);
    return ownerType instanceof ExtendedValue && ((ExtendedValue) ownerType).getLocals().contains(0);
  }

  /**
   * Check if the called method is IRunnable.run().
   *
   * @param methodCall method call
   * @param classInfoCache classInfoCache
   */
  public static boolean isRun(MethodInsnNode methodCall, IClassInfoCache classInfoCache) {
    return
      classInfoCache.hasSuperClass(methodCall.owner, IRUNNABLE_NAME) &&
        methodCall.name.equals("run") &&
        methodCall.desc.equals("()V");
  }

  /**
   * Create dummy arguments for call to the given method for restoring code.
   * Creates appropriate argument values for a method call with 0 or null values.
   *
   * @param method method node to create arguments for
   */
  public static InsnList dummyArguments(MethodInsnNode method) {
    InsnList instructions = new InsnList();

    for (Type type : Type.getArgumentTypes(method.desc)) {
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
  public static boolean isInterface(ClassNode clazz) {
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
  public static boolean isStatic(MethodNode method) {
    return (method.access & Opcodes.ACC_STATIC) != 0;
  }

  /**
   * Check if method is not static.
   *
   * @param method method
   */
  public static boolean isNotStatic(MethodNode method) {
    return (method.access & Opcodes.ACC_STATIC) == 0;
  }

  /**
   * Is the method abstract?.
   *
   * @param method method
   */
  public static boolean isAbstract(MethodNode method) {
    return (method.access & Opcodes.ACC_ABSTRACT) != 0;
  }

  /**
   * Check if the method is not void.
   *
   * @param method method
   */
  public static boolean isNotVoid(MethodNode method) {
    return !Type.getReturnType(method.desc).equals(Type.VOID_TYPE);
  }

  /**
   * Check if the method is IRunnable.run() or an implementation of it.
   *
   * @param clazz owner of method
   * @param method method
   * @param classInfoCache class info cache
   */
  public static boolean isRun(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
    // TODO 2009-11-04 mh: remove special handling for clazz == null
    return
      (clazz == null || classInfoCache.hasSuperClass(clazz.name, IRUNNABLE_NAME)) &&
        method.name.equals("run") &&
        method.desc.equals("()V");
  }

  /**
   * The first parameter of a method.
   * For static methods the first parameter is in local 0.
   * For non-static methods local 0 contains "this" and the first parameter is in local 1.
   *
   * @param method method
   */
  public static int firstParam(MethodNode method) {
    return isNotStatic(method) ? 1 : 0;
  }

  /**
   * The first local of a method that is no parameter.
   *
   * @param method method
   */
  public static int firstLocal(MethodNode method) {
    int local = firstParam(method);
    for (Type type : Type.getArgumentTypes(method.desc)) {
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
  public static InsnList dummyReturnStatement(MethodNode method) {
    Type returnType = Type.getReturnType(method.desc);
    if (returnType.getSort() == Type.VOID) {
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
  public static boolean isCompatible(MethodNode method, Frame frame) {
    Type[] arguments = Type.getArgumentTypes(method.desc);
    // Condition "l < frame.getLocals()" holds  always, because each argument is stored in a local
    for (int l = isNotStatic(method) ? 1 : 0, a = 0; a < arguments.length; a++) {
      BasicValue local = (BasicValue) frame.getLocal(l);
      if (BasicValue.UNINITIALIZED_VALUE.equals(local)) {
        throw new IllegalArgumentException("Locals have to be initialized at least with arguments");
      }

      Type argument = arguments[a];
      if (!ValueCodeFactory.code(local).isCompatibleWith(argument)) {
        return false;
      }

      l += argument.getSize();
    }

    // scanned all arguments and they passed the test
    return true;
  }

  /**
   * Escapes descriptor to make it suitable for method names.
   *
   * @param desc Descriptor.

   */
  public static String escapeForMethodName(String desc) {
    return desc.replaceAll("[()\\[/;]", "_");
  }

  //
  // code related to the instructions of a method
  //

  /**
   * All return instructions.
   *
   * @param method method
   */
  public static List<AbstractInsnNode> returnInstructions(MethodNode method) {
    List<AbstractInsnNode> result = new ArrayList<>();
    for (AbstractInsnNode instruction : method.instructions.toArray()) {
      if (isReturn(instruction)) {
        result.add(instruction);
      }
    }

    return result;
  }

  /**
   * Is an instruction a return?.
   *
   * @param instruction Instruction
   */
  public static boolean isReturn(AbstractInsnNode instruction) {
    int opcode = instruction.getOpcode();
    return opcode >= IRETURN && opcode <= RETURN;
  }

  /**
   * Is an instruction a load?.
   *
   * @param instruction Instruction
   */
  public static boolean isLoad(AbstractInsnNode instruction) {
    int opcode = instruction.getOpcode();
    return opcode >= ILOAD && opcode <= ALOAD;
  }

  /**
   * The previous instruction. Skips labels etc.
   *
   * @param instruction Instruction
   */
  public static AbstractInsnNode previousInstruction(AbstractInsnNode instruction) {
    for (AbstractInsnNode result = instruction.getPrevious(); result != null; result = result.getPrevious()) {
      if (result.getOpcode() >= 0) {
        return result;
      }
    }

    return null;
  }

  /**
   * The next instruction. Skips labels etc.
   *
   * @param instruction Instruction
   */
  public static AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
    for (AbstractInsnNode result = instruction.getNext(); result != null; result = result.getNext()) {
      if (result.getOpcode() >= 0) {
        return result;
      }
    }

    return null;
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
  public static String methodName(ClassNode clazz, MethodNode method) {
    return methodName(clazz.name, method.name, method.desc);
  }

  /**
   * Generate identification string for a method call.
   *
   * @param method method call
   */
  public static String methodName(MethodInsnNode method) {
    return methodName(method.owner, method.name, method.desc);
  }

  /**
   * Generate identification string for a method.
   *
   * @param owner owning class
   * @param name method name
   * @param desc method descriptor
   */
  public static String methodName(String owner, String name, String desc) {
    return owner + "#" + name + desc;
  }
}
