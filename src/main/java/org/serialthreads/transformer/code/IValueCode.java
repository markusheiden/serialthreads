package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Value specific code generation.
 */
public interface IValueCode
{
  /**
   * Generate code to get the array for locals of the frame.
   *
   * @param localFrame local with frame
   */
  public InsnList getLocals(int localFrame);

  /**
   * Generate code to capture a local variable to a frame.
   * The frame is expected to be already on the top of the stack.
   *
   * @param local number of local variable
   * @param index index of local among locals of the same type
   * @param localFrame frame to push to
   */
  public InsnList pushLocalVariableFast(int local, int index, int localFrame);

  /**
   * Generate code to capture a local variable to a frame.
   * The frame is expected to be already on the top of the stack.
   *
   * @param local number of local variable
   * @param index index of local among locals of the same type
   */
  public InsnList pushLocalVariable(int local, int index);

  /**
   * Generate code to capture the current stack as a return value to a frame.
   *
   * @param localFrame frame to push to
   */
  public InsnList pushReturnValue(int localFrame);

  /**
   * Generate code to restore a local variable from a frame.
   * The frame is expected to be already on the top of the stack.
   *
   * @param local number of local variable
   * @param index index of local among locals of the same type
   * @param localFrame frame to push to
   */
  public InsnList popLocalVariableFast(int local, int index, int localFrame);

  /**
   * Generate code to restore a local variable from a frame.
   * The frame is expected to be already on the top of the stack.
   *
   * @param local number of local variable
   * @param index index of local among locals of the same type
   */
  public InsnList popLocalVariable(int local, int index);

  /**
   * Generate code to restore the return value from a frame.
   *
   * @param localFrame frame to pop from
   */
  public InsnList popReturnValue(int localFrame);

  /**
   * Generate code to get the array for stack elements of the frame.
   *
   * @param localFrame local with frame
   */
  public InsnList getStacks(int localFrame);

  /**
   * Generate code to capture the top stack element to a frame.
   * The frame is expected to be already on the top of the stack.
   * The stack element to push is expected to be the element under the frame.
   *
   * @param index index of stack element among stack elements of the same type
   * @param localFrame frame to push to
   */
  public InsnList pushStack(int index, int localFrame);

  /**
   * Generate code to restore a stack element from a frame.
   * The frame is expected to be already on the top of the stack.
   *
   * @param index index of stack element among stack elements of the same type
   * @param localFrame frame to pop from
   */
  public InsnList popStack(int index, int localFrame);

  /**
   * Push value from local onto stack.
   *
   * @param i number of local variable
   */
  public VarInsnNode load(int i);

  /**
   * Pop value from stack into local.
   *
   * @param i number of local variable
   */
  public VarInsnNode store(int i);

  /**
   * Pop value from stack.
   */
  public AbstractInsnNode pop();

  /**
   * Push null value on stack.
   */
  public AbstractInsnNode pushNull();

  /**
   * Push value on stack.
   *
   * @param value value
   */
  public AbstractInsnNode push(Object value);

  /**
   * Return statement.
   * Returns null value (if not void).
   */
  public InsnList returnNull();

  /**
   * Is this type compatible with another type?
   *
   * @param type type
   */
  public boolean isCompatibleWith(Type type);

  /**
   * Is this code responsible for handling the given type?
   *
   * @param type type
   */
  public boolean isResponsibleFor(Type type);
}
