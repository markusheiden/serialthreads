package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.context.IRunnable;
import org.serialthreads.transformer.analyzer.ExtendedValue;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.MetaInfo;

import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Type.VOID_TYPE;

/**
 * Method related code.
 */
public final class MethodCode {
    private static final String IRUNNABLE_NAME = Type.getType(IRunnable.class).getInternalName();

    //
    // Method call specific code.
    //

    /**
     * Has a static method been called?.
     *
     * @param methodCall
     *         Method call.
     */
    public static boolean isStatic(MethodInsnNode methodCall) {
        return methodCall.getOpcode() == INVOKESTATIC;
    }

    /**
     * Has a non-static method been called?.
     *
     * @param methodCall
     *         Method call.
     */
    public static boolean isNotStatic(MethodInsnNode methodCall) {
        return !isStatic(methodCall);
    }

    /**
     * Has a non-void method been called?.
     *
     * @param methodCall
     *         Method call.
     */
    public static boolean isNotVoid(MethodInsnNode methodCall) {
        return !Type.getReturnType(methodCall.desc).equals(VOID_TYPE);
    }

    /**
     * Is the call a call to a method on the same object (this)?
     *
     * @param method
     *         Method containing the method call.
     * @param methodCall
     *         Method call.
     * @param metaInfo
     *         Meta information about method call.
     */
    public static boolean isSelfCall(MethodNode method, MethodInsnNode methodCall, MetaInfo metaInfo) {
        if (isStatic(method)) {
            // Static methods have no this, their local 0 is a normal local.
            return false;
        }
        if (methodCall.getOpcode() == INVOKESTATIC) {
            // Static methods have no owner.
            return false;
        }

        var frameBefore = metaInfo.frameBefore;

        // "Pop" all arguments from stack.
        var argumentTypes = Type.getArgumentTypes(methodCall.desc);
        var s = frameBefore.getStackSize() - argumentTypes.length;
        assert s > 0 : "Check: stack pointer is positive";
        var ownerType = frameBefore.getStack(s - 1);
        return ownerType instanceof ExtendedValue o && o.getLocals().contains(0);
    }

    /**
     * Check if the called method is IRunnable.run().
     *
     * @param methodCall
     *         Method call.
     * @param classInfoCache
     *         classInfoCache.
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
     * @param method
     *         Method node to create arguments for.
     */
    public static InsnList dummyArguments(MethodInsnNode method) {
        var instructions = new InsnList();

        for (var type : Type.getArgumentTypes(method.desc)) {
            instructions.add(ValueCodeFactory.code(type).pushNull());
        }

        return instructions;
    }

    //
    // Class specific code.
    //

    /**
     * Is the class an interface?.
     *
     * @param clazz
     *         Class.
     */
    public static boolean isInterface(ClassNode clazz) {
        return (clazz.access & ACC_INTERFACE) != 0;
    }

    //
    // Method specific code.
    //

    /**
     * Check if method is static.
     *
     * @param method
     *         Method.
     */
    public static boolean isStatic(MethodNode method) {
        return (method.access & ACC_STATIC) != 0;
    }

    /**
     * Check if method is not static.
     *
     * @param method
     *         Method.
     */
    public static boolean isNotStatic(MethodNode method) {
        return (method.access & ACC_STATIC) == 0;
    }

    /**
     * Is the method abstract?.
     *
     * @param method
     *         Method.
     */
    public static boolean isAbstract(MethodNode method) {
        return (method.access & ACC_ABSTRACT) != 0;
    }

    /**
     * Check if the method is not void.
     *
     * @param method
     *         Method.
     */
    public static boolean isNotVoid(MethodNode method) {
        return !Type.getReturnType(method.desc).equals(VOID_TYPE);
    }

    /**
     * Check if the method is IRunnable.run() or an implementation of it.
     *
     * @param clazz
     *         Owner of method.
     * @param method
     *         Method.
     * @param classInfoCache
     *         Class info cache.
     */
    public static boolean isRun(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
        // TODO 2009-11-04 mh: Remove special handling for clazz == null.
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
     * @param method
     *         Method.
     */
    public static int firstParam(MethodNode method) {
        return isNotStatic(method) ? 1 : 0;
    }

    /**
     * The first local of a method that is no parameter.
     *
     * @param method
     *         Method.
     */
    public static int firstLocal(MethodNode method) {
        int local = firstParam(method);
        for (var type : Type.getArgumentTypes(method.desc)) {
            local += type.getSize();
        }

        return local;
    }

    /**
     * Create dummy return statement for capturing code.
     * Creates appropriate return statement for a method with a 0, null or void return value.
     *
     * @param method
     *         Method node to create return statement for.
     */
    public static InsnList dummyReturnStatement(MethodNode method) {
        var returnType = Type.getReturnType(method.desc);
        if (returnType.getSort() == Type.VOID) {
            var instructions = new InsnList();
            instructions.add(new InsnNode(RETURN));
            return instructions;
        }

        return ValueCodeFactory.code(returnType).returnNull();
    }

    /**
     * Check if frame is still compatible with the method arguments.
     *
     * @param method
     *         Method.
     * @param frame
     *         Frame to check.
     */
    public static boolean isCompatible(MethodNode method, Frame<?> frame) {
        var arguments = Type.getArgumentTypes(method.desc);
        // Condition "l < frame.getLocals()" always holds, because each argument is stored in a local.
        for (int l = isNotStatic(method) ? 1 : 0, a = 0; a < arguments.length; a++) {
            var local = (BasicValue) frame.getLocal(l);
            if (BasicValue.UNINITIALIZED_VALUE.equals(local)) {
                throw new IllegalArgumentException("Locals have to be initialized at least with arguments");
            }

            var argument = arguments[a];
            if (!ValueCodeFactory.code(local).isCompatibleWith(argument)) {
                return false;
            }

            l += argument.getSize();
        }

        // Scanned all arguments and they passed the test.
        return true;
    }

    /**
     * Escapes descriptor to make it suitable for method names.
     *
     * @param desc
     *         Descriptor.
     */
    public static String escapeForMethodName(String desc) {
        return desc.replaceAll("[()\\[/;]", "_");
    }

    //
    // Code related to the instructions of a method.
    //

    /**
     * All return instructions.
     *
     * @param method
     *         Method.
     */
    public static List<AbstractInsnNode> returnInstructions(MethodNode method) {
        var result = new ArrayList<AbstractInsnNode>();
        // TODO markus 2023-05-12: Why toArray()?
        for (var instruction : method.instructions) {
            if (isReturn(instruction)) {
                result.add(instruction);
            }
        }

        return result;
    }

    /**
     * Is an instruction a return?.
     *
     * @param instruction
     *         Instruction.
     */
    public static boolean isReturn(AbstractInsnNode instruction) {
        return instruction != null &&
               instruction.getOpcode() >= IRETURN && instruction.getOpcode() <= RETURN;
    }

    /**
     * Is an instruction a load?.
     *
     * @param instruction
     *         Instruction.
     */
    public static boolean isLoad(AbstractInsnNode instruction) {
        var opcode = instruction.getOpcode();
        return opcode >= ILOAD && opcode <= ALOAD;
    }

    /**
     * Is an instruction a store?.
     *
     * @param instruction
     *         Instruction.
     */
    public static boolean isStore(AbstractInsnNode instruction) {
        var opcode = instruction.getOpcode();
        return opcode >= ISTORE && opcode <= ASTORE;
    }

    /**
     * The previous instruction. Skips labels etc.
     *
     * @param instruction
     *         Instruction.
     */
    public static AbstractInsnNode previousInstruction(AbstractInsnNode instruction) {
        for (var result = instruction.getPrevious(); result != null; result = result.getPrevious()) {
            if (result.getOpcode() >= 0) {
                return result;
            }
        }

        return null;
    }

    /**
     * The next instruction. Skips labels etc.
     *
     * @param instruction
     *         Instruction.
     */
    public static AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
        for (var result = instruction.getNext(); result != null; result = result.getNext()) {
            if (result.getOpcode() >= 0) {
                return result;
            }
        }

        return null;
    }

    //
    // Log / debug code.
    //

    /**
     * Generate identification string for a method.
     *
     * @param clazz
     *         Owning class.
     * @param method
     *         Method.
     */
    public static String methodName(ClassNode clazz, MethodNode method) {
        return methodName(clazz.name, method.name, method.desc);
    }

    /**
     * Generate identification string for a method call.
     *
     * @param method
     *         Method call.
     */
    public static String methodName(MethodInsnNode method) {
        return methodName(method.owner, method.name, method.desc);
    }

    /**
     * Generate identification string for a method.
     *
     * @param owner
     *         Owning class.
     * @param name
     *         Method name.
     * @param desc
     *         Method descriptor.
     */
    public static String methodName(String owner, String name, String desc) {
        return owner + "#" + name + desc;
    }
}
