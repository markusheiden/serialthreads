package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.IntValueCode.push;

/**
 * Base code that is independent from stack frame storage algorithm.
 */
public abstract class AbstractStackCode implements ThreadCode {
   private static final String OBJECT_NAME = Type.getType(Object.class).getInternalName();
   private static final String OBJECT_DESC = Type.getType(Object.class).getDescriptor();
   private static final String CLASS_NAME = Type.getType(Class.class).getInternalName();
   private static final String CLASS_DESC = Type.getType(Class.class).getDescriptor();
   private static final String STRING_DESC = Type.getType(String.class).getDescriptor();
   private static final String THREAD_IMPL_NAME = Type.getType(Stack.class).getInternalName();
   private static final String THREAD_IMPL_DESC = Type.getType(Stack.class).getDescriptor();
   private static final String THREAD = "$$thread$$";
   private static final String FRAME_IMPL_NAME = Type.getType(StackFrame.class).getInternalName();
   private static final String FRAME_IMPL_DESC = Type.getType(StackFrame.class).getDescriptor();

   //
   // Constructors.
   //

   @Override
   public FieldNode threadField() {
      return new FieldNode(ACC_PRIVATE + ACC_FINAL + ACC_SYNTHETIC, THREAD, THREAD_IMPL_DESC, THREAD_IMPL_DESC, null);
   }

   @Override
   public InsnList pushNewStack(int defaultFrameSize) {
      InsnList instructions = new InsnList();
      // this.$$thread$$ = new Stack(getClass().getSimpleName(), defaultFrameSize);
      instructions.add(new TypeInsnNode(NEW, THREAD_IMPL_NAME));
      instructions.add(new InsnNode(DUP));
      instructions.add(new VarInsnNode(ALOAD, 0));
      instructions.add(new MethodInsnNode(INVOKEVIRTUAL, OBJECT_NAME, "getClass", "()" + CLASS_DESC, false));
      instructions.add(new MethodInsnNode(INVOKEVIRTUAL, CLASS_NAME, "getSimpleName", "()" + STRING_DESC, false));
      instructions.add(IntValueCode.push(defaultFrameSize));
      instructions.add(new MethodInsnNode(INVOKESPECIAL, THREAD_IMPL_NAME, "<init>", "(" + STRING_DESC + "I)V", false));
      return instructions;
   }

   @Override
   public InsnList setThread(String className) {
     InsnList instructions = new InsnList();
     instructions.add(new FieldInsnNode(PUTFIELD, className, THREAD, THREAD_IMPL_DESC));
     return instructions;
   }

   @Override
   public InsnList pushThread(String className) {
     InsnList instructions = new InsnList();
     instructions.add(new VarInsnNode(ALOAD, 0));
     instructions.add(new FieldInsnNode(GETFIELD, className, THREAD, THREAD_IMPL_DESC));
     return instructions;
   }

   //
   // run() methods.
   //

   @Override
   public InsnList getFirstFrame(int localThread, int localFrame) {
      InsnList instructions = new InsnList();
      // frame = thread.first;
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(getFirstFrame(localFrame));
      return instructions;
   }

   @Override
   public InsnList getFirstFrame(int localFrame) {
      InsnList instructions = new InsnList();
      // frame = thread.first;
      instructions.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "first", FRAME_IMPL_DESC));
      instructions.add(new VarInsnNode(ASTORE, localFrame));
      return instructions;
   }

   //
   // Capture.
   //

   @Override
   public InsnList getNextFrame(int localPreviousFrame, int localFrame, boolean addIfNotPresent) {
      InsnList instructions = new InsnList();

      // frame = previousFrame.next
      instructions.add(new VarInsnNode(ALOAD, localPreviousFrame));
      instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
      if (addIfNotPresent) {
         LabelNode normal = new LabelNode();

         instructions.add(new InsnNode(DUP));
         instructions.add(new JumpInsnNode(IFNONNULL, normal));
         instructions.add(new InsnNode(POP));
         // frame = previousFrame.addFrame();
         instructions.add(new VarInsnNode(ALOAD, localPreviousFrame));
         instructions.add(new MethodInsnNode(INVOKEVIRTUAL, FRAME_IMPL_NAME, "addFrame", "()" + FRAME_IMPL_DESC, false));
         instructions.add(normal);
      }
      instructions.add(new VarInsnNode(ASTORE, localFrame));

      return instructions;
   }

   @Override
   public InsnList setOwner(int localPreviousFrame) {
      InsnList instructions = new InsnList();
      // previousFrame.owner = this;
      instructions.add(new VarInsnNode(ALOAD, localPreviousFrame));
      instructions.add(new VarInsnNode(ALOAD, 0));
      instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
      return instructions;
   }

   @Override
   public InsnList setMethod(int localFrame, int position) {
      InsnList instructions = new InsnList();
      // frame.method = position;
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(push(position));
      instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
      return instructions;
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
      InsnList instructions = new InsnList();
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
      return instructions;
   }

   @Override
   public InsnList getPreviousFrame(int localThread, int localPreviousFrame) {
      InsnList instructions = new InsnList();
      // previousFrame = thread.frame;
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));
      instructions.add(new VarInsnNode(ASTORE, localPreviousFrame));
      return instructions;
   }

   @Override
   public InsnList setFrame(int localThread, int localFrame) {
      InsnList instructions = new InsnList();
      // thread.frame = frame;
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));
      return instructions;
   }

   @Override
   public InsnList pushOwner(int localFrame) {
      InsnList instructions = new InsnList();
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
      return instructions;
   }

   @Override
   public InsnList pushMethod(int localFrame) {
      InsnList instructions = new InsnList();
      instructions.add(new VarInsnNode(ALOAD, localFrame));
      instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
      return instructions;
   }
}
