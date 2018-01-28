package org.serialthreads.transformer.strategies.frequent4;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.MethodNodeCopier;

import java.util.List;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.methodName;

/**
 * Method transformer for copied methods.
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
    boolean concrete = !isAbstract(method);
    if (concrete) {
      shiftLocals();
      nameAddedLocals();
      analyze();

      replaceReturns();
      List<LabelNode> restores = insertCaptureAndRestoreCode(true);
      createRestoreHandlerCopy(restores);
      addFrame();
      fixMaxs();
    }

    method.name = changeCopyName(method.name, method.desc);
    method.desc = changeCopyDesc(method.desc);
    clazz.methods.add(method);

    logger.debug("      Copied {} method {}", concrete? "concrete" : "abstract", methodName(clazz, method));

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

    final int paramPreviousFrame = paramPreviousFrame();

    final int localFrame = localFrame();

    InsnList restoreCode = new InsnList();

    // frame = previousFrame.next;
    if (needsFrame()) {
      restoreCode.add(threadCode.getNextFrame(paramPreviousFrame, localFrame, false));
    } else {
      restoreCode.add(new VarInsnNode(ALOAD, paramPreviousFrame));
      restoreCode.add(new VarInsnNode(ASTORE, localFrame));
    }

    // restore code dispatcher
    restoreCode.add(restoreCodeDispatcher(pushMethod(), restores, 0));

    method.instructions.insertBefore(method.instructions.getFirst(), restoreCode);
  }
}
