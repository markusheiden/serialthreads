package org.serialthreads.transformer.code;

import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.tree.AbstractInsnNode.IINC_INSN;
import static org.objectweb.asm.tree.AbstractInsnNode.VAR_INSN;

/**
 * Shifts the local variables of a method.
 * Used for adding parameters.
 */
public class LocalVariablesShifter {
    /**
     * Shift all locals >= point to the left by shift.
     *
     * @param point
     *         First local to shift.
     * @param shift
     *         Number of locals to shift.
     * @param method
     *         Method to transform.
     */
    public static void shift(int point, int shift, MethodNode method) {
        // Adapt instructions.
        for (var instruction : method.instructions) {
            if (instruction.getType() == VAR_INSN && instruction instanceof VarInsnNode varInstruction) {
                varInstruction.var = remap(point, shift, varInstruction.var);
            } else if (instruction.getType() == IINC_INSN && instruction instanceof IincInsnNode incInstruction) {
                incInstruction.var = remap(point, shift, incInstruction.var);
            }
        }

        // Adapt local variable debug info.
        for (var local : method.localVariables) {
            local.index = remap(point, shift, local.index);
        }

        // Fix max locals.
        method.maxLocals += shift;
    }

    /**
     * Remap a local.
     *
     * @param point
     *         Number of first local to alter.
     * @param shift
     *         Increment for locals >= point.
     * @param local
     *         Local to remap.
     * @return Remapped local.
     */
    protected static int remap(int point, int shift, int local) {
        return local < point ? local : local + shift;
    }
}
