package org.serialthreads.transformer.code;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Iterator;

/**
 * Shifts the local variables of a method.
 * Used for adding parameters.
 */
public class LocalVariablesShifter
{
  /**
   * Shift all locals >= point to the left by shift.
   *
   * @param point first local to shift
   * @param shift number of to locals to shift
   * @param method method to transform
   */
  public static void shift(int point, int shift, MethodNode method)
  {
    // adopt instructions
    for (Iterator<AbstractInsnNode> iter = method.instructions.iterator(); iter.hasNext(); )
    {
      AbstractInsnNode instruction = iter.next();
      if (instruction instanceof VarInsnNode)
      {
        VarInsnNode varInstruction = (VarInsnNode) instruction;
        varInstruction.var = remap(point, shift, varInstruction.var);
      }
      else if (instruction instanceof IincInsnNode)
      {
        IincInsnNode incInstruction = (IincInsnNode) instruction;
        incInstruction.var = remap(point, shift, incInstruction.var);
      }
    }

    // adopt local variable debug info
    for (LocalVariableNode local : method.localVariables)
    {
      local.index = remap(point, shift, local.index);
    }

    // fix max locals
    method.maxLocals += shift;
  }

  /**
   * Remap a local.
   *
   * @param point number of first local to alter
   * @param shift increment for locals >= point
   * @param local local to remap
   * @return remapped local
   */
  protected static int remap(int point, int shift, int local)
  {
    return local < point? local : local + shift;
  }
}