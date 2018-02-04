package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Value specific code generation.
 */
public interface IValueCode {
   /**
    * Generate code to get the array for locals of the frame.
    *
    * @param localFrame
    *           local with frame.
    */
   InsnList getLocals(int localFrame);

   /**
    * Generate code to capture a local variable to a frame. The frame is expected to be already on the top of the stack.
    *
    * @param local
    *           number of local variable.
    * @param index
    *           index of local among locals of the same type.
    * @param localFrame
    *           frame to push to.
    */
   InsnList pushLocalVariableFast(int local, int index, int localFrame);

   /**
    * Generate code to capture a local variable to a frame. The frame is expected to be already on the top of the stack.
    *
    * @param local
    *           number of local variable.
    * @param index
    *           index of local among locals of the same type.
    */
   InsnList pushLocalVariable(int local, int index);

   /**
    * Generate code to capture the current stack as a return value into the thread.
    *
    * @param localPreviousFrame
    *           Local with previous frame.
    */
   InsnList pushReturnValue(int localPreviousFrame);

   /**
    * Generate code to capture the current stack as a return value into the thread.
    *
    * @param localThread
    *           Local with stack.
    */
   @Deprecated // TODO 2018-02-04 markus: Remove ASAP, if storing of return values in frames has been fixed.
   InsnList pushReturnValueStack(int localThread);

   /**
    * Generate code to restore a local variable from a frame. The frame is expected to be already on the top of the
    * stack.
    *
    * @param local
    *           number of local variable.
    * @param index
    *           index of local among locals of the same type.
    * @param localFrame
    *           frame to push to.
    */
   InsnList popLocalVariableFast(int local, int index, int localFrame);

   /**
    * Generate code to restore a local variable from a frame. The frame is expected to be already on the top of the
    * stack.
    *
    * @param local
    *           number of local variable.
    * @param index
    *           index of local among locals of the same type.
    */
   InsnList popLocalVariable(int local, int index);

   /**
    * Generate code to restore the return value from a frame.
    *
    * @param localFrame
    *           Local with frame.
    */
   InsnList popReturnValue(int localFrame);

   /**
    * Generate code to restore the return value from a frame.
    *
    * @param localThread
    *           Local with stack.
    */
   @Deprecated // TODO 2018-02-04 markus: Remove ASAP, if storing of return values in frames has been fixed.
   InsnList popReturnValueStack(int localThread);

   /**
    * Generate code to get the array for stack elements of the frame.
    *
    * @param localFrame
    *           local with frame.
    */
   InsnList getStacks(int localFrame);

   /**
    * Generate code to capture the top stack element to a frame. The frame is expected to be already on the top of the
    * stack. The stack element to push is expected to be the element under the frame.
    *
    * @param index
    *           index of stack element among stack elements of the same type.
    * @param localFrame
    *           frame to push to.
    */
   InsnList pushStack(int index, int localFrame);

   /**
    * Generate code to restore a stack element from a frame. The frame is expected to be already on the top of the
    * stack.
    *
    * @param index
    *           index of stack element among stack elements of the same type.
    * @param localFrame
    *           frame to pop from.
    */
   InsnList popStack(int index, int localFrame);

   /**
    * Push value from local onto stack.
    *
    * @param i
    *           number of local variable.
    */
   VarInsnNode load(int i);

   /**
    * Pop value from stack into local.
    *
    * @param i
    *           number of local variable.
    */
   VarInsnNode store(int i);

   /**
    * Copy a local into another local.
    *
    * @param from
    *           Number of local variable to copy from.
    * @param to
    *           Number of local variable to copy to.
    */
   InsnList move(int from, int to);

   /**
    * Pop value from stack.
    */
   AbstractInsnNode pop();

   /**
    * Push null value on stack.
    */
   AbstractInsnNode pushNull();

   /**
    * Push value on stack.
    *
    * @param value
    *           value.
    */
   AbstractInsnNode push(Object value);

   /**
    * Return statement. Returns null value (if not void).
    */
   InsnList returnNull();

   /**
    * Is this type compatible with another type?
    *
    * @param type
    *           type.
    */
   boolean isCompatibleWith(Type type);

   /**
    * Is this code responsible for handling the given type?
    *
    * @param type
    *           type.
    */
   boolean isResponsibleFor(Type type);
}
