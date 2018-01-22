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
   public InsnList getFirstFrame(int localThread, int localFrame) {
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
   public InsnList getNextFrame(int localPreviousFrame, int localFrame, boolean addIfNotPresent) {
      InsnList result = new InsnList();


      // frame = previousFrame.next
      result.add(new VarInsnNode(ALOAD, localPreviousFrame));
      result.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
      if (addIfNotPresent) {
         LabelNode normal = new LabelNode();

         result.add(new InsnNode(DUP));
         result.add(new JumpInsnNode(IFNONNULL, normal));
         result.add(new InsnNode(POP));
         // frame = previousFrame.addFrame();
         result.add(new VarInsnNode(ALOAD, localPreviousFrame));
         result.add(new MethodInsnNode(INVOKEVIRTUAL, FRAME_IMPL_NAME, "addFrame", "()" + FRAME_IMPL_DESC, false));
         result.add(normal);
      }
      result.add(new VarInsnNode(ASTORE, localFrame));

      return result;
   }

   @Override
   public InsnList setOwner(int localPreviousFrame) {
      InsnList result = new InsnList();
      // previousFrame.owner = this;
      result.add(new VarInsnNode(ALOAD, localPreviousFrame));
      result.add(new VarInsnNode(ALOAD, 0));
      result.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
      return result;
   }

   @Override
   public InsnList setMethod(int localFrame, int position) {
      InsnList result = new InsnList();
      // frame.method = position;
      result.add(new VarInsnNode(ALOAD, localFrame));
      result.add(push(position));
      result.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
      return result;
   }

   @Override
   public InsnList setSerializing(int localThread, boolean serializing) {
      InsnList instructions = new InsnList();
      // thread.serializing = serializing;
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(new InsnNode(serializing? ICONST_1 : ICONST_0));
      instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
      return instructions;
   }

   //
   // Restore.
   //

   @Override
   public InsnList pushSerializing(int localThread) {
      InsnList result = new InsnList();
      result.add(new VarInsnNode(ALOAD, localThread));
      result.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
      return result;
   }

   @Override
   public InsnList getPreviousFrame(int localThread, int localPreviousFrame) {
      InsnList result = new InsnList();
      // localPreviousFrame = thread.frame;
      result.add(new VarInsnNode(ALOAD, localThread));
      result.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));
      result.add(new VarInsnNode(ASTORE, localPreviousFrame));
      return result;
   }

   @Override
   public InsnList setFrame(int localThread, int localFrame) {
      InsnList result = new InsnList();
      // thread.frame = frame;
      result.add(new VarInsnNode(ALOAD, localThread));
      result.add(new VarInsnNode(ALOAD, localFrame));
      result.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));
      return result;
   }

   @Override
   public InsnList pushOwner(int localFrame) {
      InsnList result = new InsnList();
      result.add(new VarInsnNode(ALOAD, localFrame));
      result.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
      return result;
   }

   @Override
   public InsnList pushMethod(int localFrame) {
      InsnList result = new InsnList();
      result.add(new VarInsnNode(ALOAD, localFrame));
      result.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
      return result;
   }
}
