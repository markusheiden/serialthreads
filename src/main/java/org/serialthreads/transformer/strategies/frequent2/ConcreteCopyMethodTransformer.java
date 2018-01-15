package org.serialthreads.transformer.strategies.frequent2;

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
    nameAddedLocals();
    analyze();

    // create copy of method with shortened signature
    List<LabelNode> restores = insertCaptureAndRestoreCode(true);
    createRestoreHandlerCopy(restores);
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
    assert !restores.isEmpty() : "Precondition: !restores.isEmpty()";

    logger.debug("    Creating restore handler for copied method");

    final int paramThread = paramThread();
    final int paramPreviousFrame = paramPreviousFrame();

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    InsnList restoreCode = new InsnList();

    // previousFrame
    restoreCode.add(new VarInsnNode(ALOAD, paramPreviousFrame));
    restoreCode.add(new VarInsnNode(ASTORE, localPreviousFrame));

    // frame = previousFrame.next
    restoreCode.add(new VarInsnNode(ALOAD, localPreviousFrame));
    restoreCode.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    restoreCode.add(new VarInsnNode(ASTORE, localFrame));

    // thread = currentThread;
    // TODO 2009-10-22 mh: rename locals to avoid this copy?
    restoreCode.add(new VarInsnNode(ALOAD, paramThread));
    restoreCode.add(new VarInsnNode(ASTORE, localThread));

    // restore code dispatcher
    restoreCode.add(restoreCodeDispatcher(popMethodFromFrame(), restores, 0));

    method.instructions.insertBefore(method.instructions.getFirst(), restoreCode);
  }
}
