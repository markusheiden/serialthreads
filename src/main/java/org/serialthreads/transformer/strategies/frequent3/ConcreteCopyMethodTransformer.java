package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.LocalVariablesShifter;
import org.serialthreads.transformer.code.MethodNodeCopier;

import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.MethodCode.*;

/**
 * Method transformer for copies of concrete methods.
 */
@SuppressWarnings({"UnusedAssignment"})
class ConcreteCopyMethodTransformer extends MethodTransformer {
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected ConcreteCopyMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
    // create copy of method with shortened signature
    super(clazz, MethodNodeCopier.copy(method), classInfoCache);
  }

  /**
   * Transform method.
   *
   * @return Transformed method
   * @exception AnalyzerException In case of incorrect byte code of the original method
   */
  public MethodNode transform() throws AnalyzerException {
    LocalVariablesShifter.shift(firstLocal(method), 3, method);
    analyze();

    voidReturns();
    List<InsnList> restoreCodes = insertCaptureCode(true);
    createRestoreHandlerCopy(restoreCodes);
    addThreadAndFrame();
    fixMaxs();

    method.name = changeCopyName(method.name, method.desc);
    method.desc = changeCopyDesc(method.desc);
    clazz.methods.add(method);

    if (logger.isDebugEnabled()) {
      logger.debug("      Copied concrete method " + methodName(clazz, method));
    }

    return method;
  }

  /**
   * Insert frame restoring code at the begin of a copied method.
   *
   * @param restoreCodes restore codes for all method calls in the method
   */
  private void createRestoreHandlerCopy(List<InsnList> restoreCodes) {
    assert !restoreCodes.isEmpty() : "Precondition: !restoreCodes.isEmpty()";

    logger.debug("    Creating restore handler for copied method");

    int param = firstParam(method);
    final int paramThread = param++;
    final int paramPreviousFrame = param++;

    int local = firstLocal(method);
    final int localThread = local++;
    final int localPreviousFrame = local++;
    final int localFrame = local++;

    InsnList restore = new InsnList();

    // The (previous) frame is just needed for storing the return value of this method
    if (paramPreviousFrame != localPreviousFrame && isNotVoid(method)) {
      // TODO 2009-10-22 mh: How to avoid this copy???
      restore.add(new VarInsnNode(ALOAD, paramPreviousFrame));
      restore.add(new VarInsnNode(ASTORE, localPreviousFrame));
    }

    // frame = previousFrame.next
    restore.add(new VarInsnNode(ALOAD, paramPreviousFrame));
    restore.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localFrame));

    if (paramThread != localThread) {
      // thread = currentThread;
      // TODO 2009-10-22 mh: How to avoid this copy???
      restore.add(new VarInsnNode(ALOAD, paramThread));
      restore.add(new VarInsnNode(ASTORE, localThread));
    }

    // restore code dispatcher
    InsnList getMethod = new InsnList();
    getMethod.add(new VarInsnNode(ALOAD, localFrame));
    getMethod.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
    restore.add(restoreCodeDispatcher(getMethod, restoreCodes, 0));

    method.instructions.insertBefore(method.instructions.getFirst(), restore);
  }
}
