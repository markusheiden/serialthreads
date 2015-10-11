package org.serialthreads.transformer.strategies;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.IntValueCode.push;
import static org.serialthreads.transformer.code.MethodCode.isNotStatic;
import static org.serialthreads.transformer.code.MethodCode.isNotVoid;
import static org.serialthreads.transformer.code.MethodCode.isSelfCall;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_TAIL_CALL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;
import org.serialthreads.transformer.analyzer.ExtendedFrame;
import org.serialthreads.transformer.analyzer.ExtendedValue;
import org.serialthreads.transformer.code.IValueCode;
import org.serialthreads.transformer.code.ValueCodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capture and restore of stack frames.
 */
public class StackFrameCapture {
   /**
    * Logger.
    */
   private static final Logger logger = LoggerFactory.getLogger(StackFrameCapture.class);

   private static final String OBJECT_DESC = Type.getType(Object.class).getDescriptor();
   private static final String THREAD_IMPL_NAME = Type.getType(Stack.class).getInternalName();
   private static final String FRAME_IMPL_NAME = Type.getType(StackFrame.class).getInternalName();
   private static final String FRAME_IMPL_DESC = Type.getType(StackFrame.class).getDescriptor();

   /**
    * Save current frameAfter after returning from a method call.
    *
    * @param method
    *           Method to capture.
    * @param methodCall
    *           method call to process.
    * @param metaInfo
    *           Meta information about method call.
    * @param localFrame
    *           number of local containing the frameAfter.
    * @return generated capture code.
    */
   public static InsnList pushToFrame(MethodNode method, MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame) {
      InsnList result = new InsnList();

      if (metaInfo.tags.contains(TAG_TAIL_CALL)) {
         return result;
      }

      ExtendedFrame frameAfter = metaInfo.frameAfter;
      final boolean isMethodNotStatic = isNotStatic(method);
      final boolean isCallNotVoid = isNotVoid(methodCall);

      // save stack
      // the topmost element is a dummy return value, if the called method returns one
      int[] stackIndexes = stackIndexes(frameAfter);
      for (int stack = isCallNotVoid ? frameAfter.getStackSize() - 2 : frameAfter.getStackSize() - 1; stack >= 0; stack--) {
         ExtendedValue value = (ExtendedValue) frameAfter.getStack(stack);
         if (value.isConstant() || value.isHoldInLocal()) {
            // just pop the value from stack, because the stack value is constant or stored in a local too.
            result.add(code(value).pop());
         } else {
            result.add(code(value).pushStack(stackIndexes[stack], localFrame));
         }
      }

      // save locals separated by type
      for (IValueCode code : ValueCodeFactory.CODES) {
         List<Integer> pushLocals = new ArrayList<>(frameAfter.getLocals());

         // do not store local 0 for non static methods, because it always contains "this"
         for (int local = isMethodNotStatic ? 1 : 0, end = frameAfter.getLocals() - 1; local <= end; local++) {
            BasicValue value = frameAfter.getLocal(local);
            if (code.isResponsibleFor(value.getType())) {
               if (frameAfter.neededLocals.contains(local) && !frameAfter.isHoldInLowerNeededLocal(local)) {
                  pushLocals.add(local);
               }
            }
         }

         Iterator<Integer> iter = pushLocals.iterator();

         // for first locals use fast stack
         for (int i = 0; iter.hasNext() && i < StackFrame.FAST_FRAME_SIZE; i++) {
            int local = iter.next();
            IValueCode localCode = code(frameAfter.getLocal(local));
            result.add(localCode.pushLocalVariableFast(local, i, localFrame));
         }

         // for too high locals use "slow" storage in (dynamic) array
         if (iter.hasNext()) {
            result.add(code.getLocals(localFrame));
            for (int i = 0; iter.hasNext(); i++) {
               int local = iter.next();
               IValueCode localCode = code(frameAfter.getLocal(local));
               if (iter.hasNext()) {
                  result.add(new InsnNode(DUP));
               }
               result.add(localCode.pushLocalVariable(local, i));
            }
         }
      }

      return result;
   }

   /**
    * Push method and owner onto frame.
    *
    * @param method
    *           Method to capture.
    * @param position
    *           position of method call.
    * @param containsMoreThanOneMethodCall
    *           contains the method more than one method call?.
    * @param suppressOwner
    *           suppress saving the owner?.
    * @param localPreviousFrame
    *           number of local containing the previous frame or -1 for retrieving it via current frame.
    * @param localFrame
    *           number of local containing the current frame.
    * @return generated capture code.
    */
   public static InsnList pushMethodToFrame(MethodNode method, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner, int localPreviousFrame, int localFrame) {
      InsnList result = new InsnList();

      // save method index of this method
      if (containsMoreThanOneMethodCall) {
         // frame.method = position;
         result.add(new VarInsnNode(ALOAD, localFrame));
         result.add(push(position));
         result.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
      }

      // save owner of method call one level above
      if (isNotStatic(method) && !suppressOwner) {
         // previousFrame.owner = this;
         if (localPreviousFrame < 0) {
            result.add(new VarInsnNode(ALOAD, localFrame));
            result.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "previous", FRAME_IMPL_DESC));
         } else {
            result.add(new VarInsnNode(ALOAD, localPreviousFrame));
         }
         result.add(new VarInsnNode(ALOAD, 0));
         result.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
      }

      return result;
   }

   /**
    * Push method and owner onto frame with a given method.
    *
    * Currently not used.
    * @see Stack#leaveMethod(Object, int) etc.
    *
    * @param method
    *           Method to capture.
    * @param position
    *           position of method call
    * @param containsMoreThanOneMethodCall
    *           contains the method more than one method call?
    * @param methodName
    *           name of method to store owner and method
    * @param localThread
    *           number of local containing the thread
    * @return generated capture code
    */
   public static InsnList pushMethodToFrame(MethodNode method, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner, String methodName, int localThread) {
      InsnList result = new InsnList();

      final boolean isMethodNotStatic = isNotStatic(method);

      // save method index of this method and owner of method call one level above
      boolean pushOwner = isMethodNotStatic && !suppressOwner;
      boolean pushMethod = containsMoreThanOneMethodCall;
      if (pushOwner && pushMethod) {
         // save owner of this method for calling method and index of interrupted method
         result.add(new VarInsnNode(ALOAD, localThread));
         result.add(new VarInsnNode(ALOAD, 0));
         result.add(push(position));
         result.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, methodName, "(" + OBJECT_DESC + "I)V", false));
      } else if (pushOwner) {
         // save owner of this method for calling method
         result.add(new VarInsnNode(ALOAD, localThread));
         result.add(new VarInsnNode(ALOAD, 0));
         result.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, methodName, "(" + OBJECT_DESC + ")V", false));
      } else if (pushMethod) {
         // save index of interrupted method
         result.add(new VarInsnNode(ALOAD, localThread));
         result.add(push(position));
         result.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, methodName, "(I)V", false));
      }

      return result;
   }

   /**
    * Restore owner.
    *
    * @param methodCall
    *           method call to process.
    * @param metaInfo
    *           Meta information about method call.
    * @param localFrame
    *           number of local containing the frame.
    * @return generated restore code.
    */
   public static InsnList popOwnerFromFrame(MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame) {
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

   /**
    * Restore current frame before resuming the method call
    *
    * @param method
    *           Method to restore.
    * @param methodCall
    *           method call to process.
    * @param metaInfo
    *           Meta information about method call.
    * @param localFrame
    *           number of local containing the frame.
    * @return generated restore code.
    */
   public static InsnList popFromFrame(MethodNode method, MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame) {
      InsnList result = new InsnList();

      if (metaInfo.tags.contains(TAG_TAIL_CALL)) {
         return result;
      }

      ExtendedFrame frameAfter = metaInfo.frameAfter;
      final boolean isMethodNotStatic = isNotStatic(method);
      final boolean isCallNotVoid = isNotVoid(methodCall);

      // restore locals by type
      for (IValueCode code : ValueCodeFactory.CODES) {
         List<Integer> popLocals = new ArrayList<>();
         InsnList copyLocals = new InsnList();

         // do not restore local 0 for non static methods, because it always contains "this"
         for (int local = isMethodNotStatic ? 1 : 0, end = frameAfter.getLocals() - 1; local <= end; local++) {
            BasicValue value = frameAfter.getLocal(local);
            if (code.isResponsibleFor(value.getType())) {
               ExtendedValue extendedValue = (ExtendedValue) value;
               // Ignore not needed locals
               if (frameAfter.neededLocals.contains(local)) {
                  if (frameAfter.isHoldInLowerNeededLocal(local)) {
                     // the value of the local is hold in a lower local too -> copy
                     logger.debug("        Detected codes with the same value: {}/{}", extendedValue.getLowestLocal(), local);
                     copyLocals.add(code(extendedValue).load(extendedValue.getLowestLocal()));
                     copyLocals.add(code(extendedValue).store(local));
                  } else {
                     // normal case -> pop local from frameAfter
                     popLocals.add(local);
                  }
               }
            }
         }

         // first restore not duplicated locals, if any
         Iterator<Integer> iter = popLocals.iterator();

         // for first locals use fast stack
         for (int i = 0; iter.hasNext() && i < StackFrame.FAST_FRAME_SIZE; i++) {
            int local = iter.next();
            IValueCode localCode = code(frameAfter.getLocal(local));
            result.add(localCode.popLocalVariableFast(local, i, localFrame));
         }

         // for too high locals use "slow" storage in (dynamic) array
         if (iter.hasNext()) {
            result.add(code.getLocals(localFrame));
            for (int i = 0; iter.hasNext(); i++) {
               int local = iter.next();
               IValueCode localCode = code(frameAfter.getLocal(local));
               if (iter.hasNext()) {
                  result.add(new InsnNode(DUP));
               }
               result.add(localCode.popLocalVariable(local, i));
            }
         }

         // then restore duplicated locals
         result.add(copyLocals);
      }

      // restore stack
      // the topmost element is a dummy return value, if the called method is not a void method
      int[] stackIndexes = stackIndexes(frameAfter);
      for (int stack = 0, end = isCallNotVoid ? frameAfter.getStackSize() - 1 : frameAfter.getStackSize(); stack < end; stack++) {
         ExtendedValue value = (ExtendedValue) frameAfter.getStack(stack);
         if (value.isConstant()) {
            // the stack value is constant -> push constant
            logger.debug("        Detected constant value on stack: {} / value {}", value, value.getConstant());
            result.add(code(value).push(value.getConstant()));
         } else if (value.isHoldInLocal()) {
            // the stack value was already stored in local variable -> load local
            logger.debug("        Detected value of local on stack: {} / local {}", value, value.getLowestLocal());
            result.add(code(value).load(value.getLowestLocal()));
         } else {
            // normal case -> pop stack from frameAfter
            result.add(code(value).popStack(stackIndexes[stack], localFrame));
         }
      }

      return result;
   }

   /**
    * Compute index of all stack elements in typed stack arrays.
    *
    * @param frame
    *           Frame.
    * @return array stack element -> stack element index.
    */
   private static int[] stackIndexes(Frame frame) {
      int[] result = new int[frame.getStackSize()];
      Arrays.fill(result, -1);
      for (IValueCode code : ValueCodeFactory.CODES) {
         for (int stack = 0, end = frame.getStackSize(), i = 0; stack < end; stack++) {
            BasicValue value = (BasicValue) frame.getStack(stack);
            if (code.isResponsibleFor(value.getType())) {
               result[stack] = i++;
            }
         }
      }

      return result;
   }
}
