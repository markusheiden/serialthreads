package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.MethodNodeCopier;

import java.util.List;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.serialthreads.transformer.code.MethodCode.methodName;

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
   * @throws AnalyzerException In case of incorrect byte code of the original method
   */
  public MethodNode transform() throws AnalyzerException {
    shiftLocals();
    analyze();

    replaceReturns();
    List<LabelNode> restores = insertCaptureCode(true);
    createRestoreHandlerCopy(restores);
    addThreadAndFrame();
    fixMaxs();

    method.name = changeCopyName(method.name, method.desc);
    method.desc = changeCopyDesc(method.desc);
    clazz.methods.add(method);

    logger.debug("      Copied concrete method {}", methodName(clazz, method));

    return method;
  }

  /**
   * Insert frame restoring code at the begin of a copied method.
   *
   * @param restores Labels pointing to the generated restore codes for method calls.
   */
  private void createRestoreHandlerCopy(List<LabelNode> restores) {
    assert !restores.isEmpty() : "Precondition: !restoreCodes.isEmpty()";

    logger.debug("    Creating restore handler for copied method");

    final int paramThread = paramThread();
    final int paramPreviousFrame = paramPreviousFrame();

    final int localThread = localThread();
    final int localFrame = localFrame();

    InsnList restore = new InsnList();

    // frame = previousFrame.next
    restore.add(new VarInsnNode(ALOAD, paramPreviousFrame));
    if (needsFrame()) {
      restore.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    }
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
    restore.add(restoreCodeDispatcher(getMethod, restores, 0));

    method.instructions.insertBefore(method.instructions.getFirst(), restore);
  }
}
