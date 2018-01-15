package org.serialthreads.transformer.strategies;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.IntValueCode.push;
import static org.serialthreads.transformer.code.MethodCode.isNotStatic;
import static org.serialthreads.transformer.code.MethodCode.isSelfCall;

/**
 * {@link StackFrameCode} using compact storage of stack frames.
 * Locals are grouped per type and get "renumbered".
 */
public abstract class AbstractStackFrameCode implements StackFrameCode {
   private static final String OBJECT_DESC = Type.getType(Object.class).getDescriptor();
   private static final String THREAD_IMPL_NAME = Type.getType(Stack.class).getInternalName();
   private static final String FRAME_IMPL_NAME = Type.getType(StackFrame.class).getInternalName();
   private static final String FRAME_IMPL_DESC = Type.getType(StackFrame.class).getDescriptor();

   @Override
   public InsnList pushMethodToFrame(int position, boolean containsMoreThanOneMethodCall, int localFrame) {
      InsnList result = new InsnList();

      // save method index of this method
      if (containsMoreThanOneMethodCall) {
         // frame.method = position;
         result.add(new VarInsnNode(ALOAD, localFrame));
         result.add(push(position));
         result.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
      }

      return result;
   }

   @Override
   public InsnList pushOwnerToFrame(MethodNode method, MethodInsnNode methodCall, MetaInfo metaInfo, int localPreviousFrame, int localFrame) {
      InsnList result = new InsnList();

      // Save owner of method call one level above.
      if (!isSelfCall(methodCall, metaInfo) && isNotStatic(method)) {
         // previousFrame.owner = this;
         result.add(new VarInsnNode(ALOAD, localPreviousFrame));
         result.add(new VarInsnNode(ALOAD, 0));
         result.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
      }

      return result;
   }

   @Override
   public InsnList startSerializing(int localThread) {
      return setSerializing(localThread, true);
   }

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
   public InsnList popOwnerFromFrame(MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame) {
      InsnList result = new InsnList();

      if (isSelfCall(methodCall, metaInfo)) {
         // self call: owner == this
         result.add(new VarInsnNode(ALOAD, 0));
      } else if (isNotStatic(methodCall)) {
         // get owner
         result.add(new VarInsnNode(ALOAD, localFrame));
         result.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
         result.add(new TypeInsnNode(CHECKCAST, methodCall.owner));
      }

      return result;
   }

   @Override
   public InsnList popMethodFromFrame(int localFrame) {
      InsnList result = new InsnList();
      result.add(new VarInsnNode(ALOAD, localFrame));
      result.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
      return result;
   }
}
