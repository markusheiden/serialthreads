package org.serialthreads.transformer.strategies.frequent4;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * Method transformer for run methods.
 */
@SuppressWarnings({"UnusedAssignment", "UnusedDeclaration"})
class RunMethodTransformer extends MethodTransformer {
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected RunMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
    super(clazz, method, classInfoCache);
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

    replaceRunReturns();
    List<LabelNode> restores = insertCaptureAndRestoreCode(true);
    createRestoreHandlerRun(restores);
    addFrame();
    fixMaxs();

    return method;
  }

  /**
   * Insert frame restoring code at the begin of the run() method.
   *
   * @param restores Labels pointing to the generated restore codes for method calls.
   */
  private void createRestoreHandlerRun(List<LabelNode> restores) {
    assert !restores.isEmpty() : "Precondition: !restoreCodes.isEmpty()";

    logger.debug("    Creating restore handler for run");

    final int localThread = localThread();
    final int localFrame = localFrame();

    LabelNode startRestore = new LabelNode();
    restores.add(0, startRestore);
    // dummy startup restore code to avoid to check thread.serializing.
    // empty frames are expected to have method = -1.
    InsnList startRestoreCode = new InsnList();
    startRestoreCode.add(startRestore);
    // reset method to 0 for the case that there is just one normal restore code, because
    // if there is just one normal restore code, the method index will not be captured.
    // so we set the correct one (0) for this case.
    if (restores.size() <= 1) {
      startRestoreCode.add(new VarInsnNode(ALOAD, localFrame));
      startRestoreCode.add(new InsnNode(ICONST_0));
      startRestoreCode.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
    }
    // implicit goto to normal code, because this restore code will be put at the end of the restore code dispatcher

    // restore code dispatcher
    InsnList restoreCode = new InsnList();

    // thread = this.$$thread$$;
    restoreCode.add(new VarInsnNode(ALOAD, 0));
    restoreCode.add(new FieldInsnNode(GETFIELD, clazz.name, THREAD, THREAD_IMPL_DESC));
    restoreCode.add(new VarInsnNode(ASTORE, localThread));

    // frame = thread.first;
    restoreCode.add(new VarInsnNode(ALOAD, localThread));
    restoreCode.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "first", FRAME_IMPL_DESC));
    restoreCode.add(new VarInsnNode(ASTORE, localFrame));

    // no previous frame needed in run, because there may not be a previous frame

    // restore code dispatcher
    restoreCode.add(restoreCodeDispatcher(popMethodFromFrame(), restores, -1));
    restoreCode.add(startRestoreCode);

    method.instructions.insertBefore(method.instructions.getFirst(), restoreCode);
  }
}