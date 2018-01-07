package org.serialthreads.transformer.strategies.frequent;

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
    analyze();

    replaceRunReturns();
    List<LabelNode> restores = insertCaptureCode(true);
    createRestoreHandlerRun(restores);
    fixMaxs();

    return method;
  }

  /**
   * Insert frame restoring code at the begin of the run() method.
   *
   * @param restores Labels pointing to the generated restore codes for method calls.
   */
  private void createRestoreHandlerRun(List<LabelNode> restores) {
    assert !restores.isEmpty() : "Precondition: !restores.isEmpty()";

    logger.debug("    Creating restore handler for run");

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    // dummy startup restore code to avoid to check thread.serializing.
    // empty frames are expected to have method = -1.
    InsnList startRestoreCode = new InsnList();
    // reset method to 0 for the case that there is just one normal restore code, because
    // if there is just one normal restore code, the method index will not be captured.
    // so we set the correct one (0) for this case.
    if (restores.size() <= 1) {
      startRestoreCode.add(new VarInsnNode(ALOAD, localFrame));
      startRestoreCode.add(new InsnNode(ICONST_0));
      startRestoreCode.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
    }
    // implicit goto to normal code, because this restore code will be put at the end of the restore code dispatcher
    restores.add(0, startRestoreCode);

    InsnList restore = new InsnList();

    // thread = this.$$thread$$;
    restore.add(new VarInsnNode(ALOAD, 0));
    restore.add(new FieldInsnNode(GETFIELD, clazz.name, THREAD, THREAD_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localThread));

    // frame = thread.first;
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "first", FRAME_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localFrame));

    // TODO 2009-11-26 mh: remove me?
    // thread.frame = frame;
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new VarInsnNode(ALOAD, localFrame));
    restore.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));

    // no previous frame needed in run, because there may not be a previous frame

    // restore code dispatcher
    InsnList getMethod = new InsnList();
    getMethod.add(new VarInsnNode(ALOAD, localFrame));
    getMethod.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
    restore.add(restoreCodeDispatcher(getMethod, restores, -1));

    method.instructions.insertBefore(method.instructions.getFirst(), restore);
  }
}
