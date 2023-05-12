package org.serialthreads.transformer.code;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.transformer.analyzer.ExtendedFrame;
import org.serialthreads.transformer.analyzer.ExtendedValue;
import org.serialthreads.transformer.strategies.MetaInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.serialthreads.transformer.code.MethodCode.isNotStatic;
import static org.serialthreads.transformer.code.MethodCode.isNotVoid;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;
import static org.serialthreads.transformer.strategies.MetaInfo.TAG_TAIL_CALL;

/**
 * {@link ThreadCode} using compact storage of stack frames.
 * Locals are grouped per type and get "renumbered".
 */
public class CompactingStackCode extends AbstractStackCode {
   /**
    * Logger.
    */
   private static final Logger logger = LoggerFactory.getLogger(CompactingStackCode.class);

   @Override
   public InsnList captureFrame(MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame) {
      var instructions = new InsnList();

      if (metaInfo.tags.contains(TAG_TAIL_CALL)) {
         return instructions;
      }

      var frameAfter = metaInfo.frameAfter;
      final boolean isMethodNotStatic = isNotStatic(methodCall);
      final boolean isCallNotVoid = isNotVoid(methodCall);

      // save stack
      // the topmost element is a dummy return value, if the called method returns one
      var stackIndexes = stackIndexes(frameAfter);
      for (int stack = isCallNotVoid ? frameAfter.getStackSize() - 2 : frameAfter.getStackSize() - 1; stack >= 0; stack--) {
         var value = (ExtendedValue) frameAfter.getStack(stack);
         int lowestLocal = frameAfter.getLowestNeededLocal(value);
         if (value.isConstant() || lowestLocal >= 0) {
            // just pop the value from stack, because the stack value is constant or stored in a local too.
            instructions.add(code(value).pop());
         } else {
            instructions.add(code(value).pushStack(stackIndexes[stack], localFrame));
         }
      }

      // save locals separated by type
      for (var code : ValueCodeFactory.CODES) {
         var pushLocals = new ArrayList<Integer>(frameAfter.getLocals());

         // Do not store local 0 for non-static methods, because it always contains "this".
         for (int local = isMethodNotStatic ? 1 : 0, end = frameAfter.getLocals() - 1; local <= end; local++) {
            var value = frameAfter.getLocal(local);
            if (code.isResponsibleFor(value.getType())) {
               var extendedValue = (ExtendedValue) value;
               int lowestLocal = frameAfter.getLowestNeededLocal(extendedValue);
               if (local == lowestLocal) {
                  // Only store value, if it is not stored in a lower needed local.
                  pushLocals.add(local);
               }
            }
         }

         var iter = pushLocals.iterator();
         for (int i = 0; iter.hasNext(); i++) {
            int local = iter.next();
            var localCode = code(frameAfter.getLocal(local));
            instructions.add(localCode.pushLocal(local, i, iter.hasNext(), localFrame));
         }
      }

      return instructions;
   }

   @Override
   public InsnList restoreFrame(MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame) {
      var instructions = new InsnList();

      if (metaInfo.tags.contains(TAG_TAIL_CALL)) {
         return instructions;
      }

      var frameAfter = metaInfo.frameAfter;
      final boolean isMethodNotStatic = isNotStatic(methodCall);
      final boolean isCallNotVoid = isNotVoid(methodCall);

      // Restore locals by type.
      for (var code : ValueCodeFactory.CODES) {
         var popLocals = new ArrayList<Integer>();
         var copyLocals = new InsnList();

         // Do not restore local 0 for non-static methods, because it always contains "this".
         for (int local = isMethodNotStatic ? 1 : 0, end = frameAfter.getLocals() - 1; local <= end; local++) {
            var value = frameAfter.getLocal(local);
            if (code.isResponsibleFor(value.getType())) {
               var extendedValue = (ExtendedValue) value;
               // Ignore not needed locals.
               int lowestLocal = frameAfter.getLowestNeededLocal(extendedValue);
               if (local == lowestLocal) {
                  // Normal case -> Pop local from frameAfter.
                  popLocals.add(local);
               } else if (lowestLocal >= 0) {
                  // The value of the local is hold in a lower local too -> copy.
                  logger.debug("        Detected codes with the same value: {}/{}", lowestLocal, local);
                  copyLocals.add(code(extendedValue).load(lowestLocal));
                  copyLocals.add(code(extendedValue).store(local));
               }
               // else: The local is not needed -> No restore needed.
            }
         }

         // first restore not duplicated locals, if any
         var iter = popLocals.iterator();
         for (int i = 0; iter.hasNext(); i++) {
            int local = iter.next();
            var localCode = code(frameAfter.getLocal(local));
            instructions.add(localCode.popLocal(local, i, iter.hasNext(), localFrame));
         }

         // then restore duplicated locals
         instructions.add(copyLocals);
      }

      // restore stack
      // the topmost element is a dummy return value, if the called method is not a void method
      var stackIndexes = stackIndexes(frameAfter);
      for (int stack = 0, end = isCallNotVoid ? frameAfter.getStackSize() - 1 : frameAfter.getStackSize(); stack < end; stack++) {
         var value = (ExtendedValue) frameAfter.getStack(stack);
         var lowestLocal = frameAfter.getLowestNeededLocal(value);
         if (value.isConstant()) {
            // the stack value is constant -> push constant
            logger.debug("        Detected constant value on stack: {} / value {}", value, value.getConstant());
            instructions.add(code(value).push(value.getConstant()));
         } else if (lowestLocal >= 0) {
            // the stack value was already stored in local variable -> load local
            logger.debug("        Detected value of local on stack: {} / local {}", value, lowestLocal);
            instructions.add(code(value).load(lowestLocal));
         } else {
            // normal case -> pop stack from frameAfter
            instructions.add(code(value).popStack(stackIndexes[stack], localFrame));
         }
      }

      return instructions;
   }

   /**
    * Compute index of all stack elements in typed stack arrays.
    *
    * @param frame
    *           Frame.
    * @return array stack element -> stack element index.
    */
   private int[] stackIndexes(Frame frame) {
      var result = new int[frame.getStackSize()];
      Arrays.fill(result, -1);
      for (var code : ValueCodeFactory.CODES) {
         for (int stack = 0, end = frame.getStackSize(), i = 0; stack < end; stack++) {
            var value = (BasicValue) frame.getStack(stack);
            if (code.isResponsibleFor(value.getType())) {
               result[stack] = i++;
            }
         }
      }

      return result;
   }
}
