package org.serialthreads.transformer.strategies.frequent2;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.LocalVariablesShifter;

import java.util.List;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.serialthreads.transformer.code.MethodCode.firstLocal;

/**
 * Method transformer for run methods.
 */
@SuppressWarnings({"UnusedDeclaration", "UnusedAssignment", "UnnecessaryLocalVariable"})
class RunMethodTransformer extends MethodTransformer
{
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected RunMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache)
  {
    super(clazz, method, classInfoCache);
  }

  /**
   * Transform method.
   *
   * @return Transformed method
   * @exception AnalyzerException In case of incorrect byte code of the original method
   */
  public MethodNode transform() throws AnalyzerException
  {
    LocalVariablesShifter.shift(firstLocal(method), 3, method);
    analyze();

    replaceReturns();
    List<InsnList> restoreCodes = insertCaptureCode(true);
    createRestoreHandlerRun(restoreCodes);
    fixMaxs();

    return method;
  }

  /**
   * Insert frame restoring code at the begin of the run() method.
   *
   * @param restoreCodes restore codes for all method calls in the method
   */
  private void createRestoreHandlerRun(List<InsnList> restoreCodes)
  {
    assert !restoreCodes.isEmpty() : "Precondition: !restoreCodes.isEmpty()";

    if (log.isDebugEnabled())
    {
      log.debug("    Creating restore handler for run");
    }

    int local = firstLocal(method);
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
    final int localFrame = local++;

    // dummy startup restore code to avoid to check thread.serializing.
    // empty frames are expected to have method = -1.
    InsnList startRestoreCode = new InsnList();
    // reset method to 0 for the case that there is just one normal restore code, because
    // if there is just one normal restore code, the method index will not be captured.
    // so we set the correct one (0) for this case.
    if (restoreCodes.size() <= 1)
    {
      startRestoreCode.add(new VarInsnNode(ALOAD, localFrame));
      startRestoreCode.add(new InsnNode(ICONST_0));
      startRestoreCode.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
    }

    // TODO 2009-11-26 mh: remove me?
    // thread.frame = frame;
    startRestoreCode.add(new VarInsnNode(ALOAD, localThread));
    startRestoreCode.add(new VarInsnNode(ALOAD, localFrame));
    startRestoreCode.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));

    // implicit goto to normal code, because this restore code will be put at the end of the restore code dispatcher
    restoreCodes.add(0, startRestoreCode);

    InsnList restore = new InsnList();

    // thread = this.$$thread$$;
    restore.add(new VarInsnNode(ALOAD, 0));
    restore.add(new FieldInsnNode(GETFIELD, clazz.name, THREAD, THREAD_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localThread));

    // frame = thread.first;
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "first", FRAME_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localFrame));

    // no previous frame needed in run, because there may not be a previous frame

    // restore code dispatcher
    InsnList getMethod = new InsnList();
    getMethod.add(new VarInsnNode(ALOAD, localFrame));
    getMethod.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
    restore.add(restoreCodeDispatcher(getMethod, restoreCodes, -1));

    method.instructions.insertBefore(method.instructions.getFirst(), restore);
  }
}
