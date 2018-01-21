package org.serialthreads.transformer.strategies;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.IntValueCode.push;

/**
 * Base code that is independent from stack frame storage algorithm.
 */
public abstract class AbstractStackCode implements StackCode {
   private static final String OBJECT_DESC = Type.getType(Object.class).getDescriptor();
   private static final String THREAD_IMPL_NAME = Type.getType(Stack.class).getInternalName();
   private static final String THREAD_IMPL_DESC = Type.getType(Stack.class).getDescriptor();
   private static final String FRAME_IMPL_NAME = Type.getType(StackFrame.class).getInternalName();
   private static final String FRAME_IMPL_DESC = Type.getType(StackFrame.class).getDescriptor();

   //
   // run() methods.
   //

   @Override
   public InsnList firstFrame(int localThread, int localFrame) {
      InsnList result = new InsnList();
      // frame = thread.first;
      result.add(new VarInsnNode(ALOAD, localThread));
      result.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "first", FRAME_IMPL_DESC));
      result.add(new VarInsnNode(ASTORE, localFrame));
      return result;
   }

   @Override
   public InsnList resetMethod(int localFrame) {
      InsnList result = new InsnList();
      result.add(new VarInsnNode(ALOAD, localFrame));
      result.add(new InsnNode(ICONST_0));
      result.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
      return result;
   }

   //
   // Capture.
   //

   @Override
   public InsnList nextFrame(int localPreviousFrame) {
      InsnList result = new InsnList();

      LabelNode normal = new LabelNode();

      // frame = previousFrame.next
      result.add(new VarInsnNode(ALOAD, localPreviousFrame));
      result.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
      result.add(new InsnNode(DUP));
      result.add(new JumpInsnNode(IFNONNULL, normal));
      result.add(new InsnNode(POP));
      // frame = previousFrame.addFrame();
      result.add(new VarInsnNode(ALOAD, localPreviousFrame));
      result.add(new MethodInsnNode(INVOKEVIRTUAL, FRAME_IMPL_NAME, "addFrame", "()" + FRAME_IMPL_DESC, false));
      result.add(normal);

      return result;
   }

   @Override
   public InsnList pushOwner(int localPreviousFrame) {
      InsnList result = new InsnList();
      // previousFrame.owner = this;
      result.add(new VarInsnNode(ALOAD, localPreviousFrame));
      result.add(new VarInsnNode(ALOAD, 0));
      result.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
      return result;
   }

   @Override
   public InsnList pushMethod(int localFrame, int position) {
      InsnList result = new InsnList();
      // frame.method = position;
      result.add(new VarInsnNode(ALOAD, localFrame));
      result.add(push(position));
      result.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
      return result;
   }

   @Override
   public InsnList startSerializing(int localThread) {
      return setSerializing(localThread, true);
   }

   //
   // Restore.
   //

   @Override
   public InsnList stopDeserializing(int localThread) {
      return setSerializing(localThread, false);
   }

   /**
    * Set {@link Stack#serializing}.
    */
   private InsnList setSerializing(int localThread, boolean serializing) {
      InsnList instructions = new InsnList();
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(new InsnNode(serializing? ICONST_1 : ICONST_0));
      instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
      return instructions;
   }

   @Override
   public InsnList popOwner(int localFrame) {
      InsnList result = new InsnList();
      result.add(new VarInsnNode(ALOAD, localFrame));
      result.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
      return result;
   }

   @Override
   public InsnList popMethod(int localFrame) {
      InsnList result = new InsnList();
      result.add(new VarInsnNode(ALOAD, localFrame));
      result.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
      return result;
   }
}
