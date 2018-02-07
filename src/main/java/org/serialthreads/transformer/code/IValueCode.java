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

   //
   // Stack.
   //

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

   //
   // Locals.
   //

   /**
    * Generate code to capture a local to a frame.
    *
    * @param local
    *           number of local.
    * @param index
    *           index of local among locals of the same type.
    * @param more
    *           Are there any more values to capture?
    * @param localFrame
    *           frame to push to.
    */
   InsnList pushLocal(int local, int index, boolean more, int localFrame);

   /**
    * Generate code to restore a local from a frame.
    *
    * @param local
    *           number of local.
    * @param index
    *           index of local among locals of the same type.
    * @param more
    *           Are there any more values to capture?
    * @param localFrame
    *           frame to pop from.
    */
   InsnList popLocal(int local, int index, boolean more, int localFrame);

   //
   // Return value.
   //

   /**
    * Generate code to capture the current stack as a return value into the thread.
    *
    * @param localThread
    *           Local with thread.
    */
   InsnList pushReturnValue(int localThread);

   /**
    * Generate code to restore the return value from a frame.
    *
    * @param localThread
    *           Local with thread.
    */
   InsnList popReturnValue(int localThread);

   //
   // Instructions.
   //

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
}
