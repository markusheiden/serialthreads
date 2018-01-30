package org.serialthreads.transformer.strategies.frequent2;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.List;

import static org.objectweb.asm.Opcodes.ASTORE;

/**
 * Method transformer for run methods.
 */
@SuppressWarnings({"UnusedDeclaration", "UnusedAssignment", "UnnecessaryLocalVariable"})
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
    fixMaxs();

    return method;
  }

  /**
   * Insert frame restoring code at the begin of the run() method.
   *
   * @param restores Labels pointing to the generated restore codes for method calls.
   */
  private void createRestoreHandlerRun(List<LabelNode> restores) {
    if (restores.isEmpty()) {
      logger.debug("    No restore handler needed for run");
      return;
    }

    logger.debug("    Creating restore handler for run");

    final int localThread = localThread();
    final int localFrame = localFrame();

    InsnList instructions = new InsnList();

    // thread = this.$$thread$$;
    instructions.add(threadCode.pushThread(clazz.name));
    instructions.add(new VarInsnNode(ASTORE, localThread));

    // frame = thread.first;
    instructions.add(threadCode.getFirstFrame(localThread, localFrame));

    // TODO 2009-11-26 mh: remove me?
    // thread.frame = frame;
    instructions.add(threadCode.setFrame(localThread, localFrame));

    // No previous frame needed in run, because there may not be a previous frame.

    // Add label for first call of run() at index -1, see "startIndex" below.
    // Empty frames are expected to have method == -1.
    LabelNode startRun = new LabelNode();
    restores.add(0, startRun);

    instructions.add(restoreCodeDispatcher(pushMethod(), restores, -1));

    // Dummy startup code to avoid check of thread.serializing.
    instructions.add(startRun);

    // Reset method to 0 for the case that there is just one normal restore code (except startRun),
    // because if there is just one normal restore code, the method index will not be captured.
    // So we set the correct one (0) for this case.
    if (restores.size() == 2) {
      instructions.add(threadCode.setMethod(localFrame, 0));
    }
    // Continue with normal code.

    method.instructions.insertBefore(method.instructions.getFirst(), instructions);
  }
}
