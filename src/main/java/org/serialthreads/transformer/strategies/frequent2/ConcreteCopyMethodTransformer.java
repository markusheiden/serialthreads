package org.serialthreads.transformer.strategies.frequent2;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.List;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.serialthreads.transformer.code.MethodCode.firstLocal;
import static org.serialthreads.transformer.code.MethodCode.firstParam;
import static org.serialthreads.transformer.code.MethodCode.methodName;

/**
 * Method transformer for copies of concrete methods.
 */
@SuppressWarnings({"UnusedAssignment"})
class ConcreteCopyMethodTransformer extends MethodTransformer
{
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected ConcreteCopyMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache)
  {
    // create copy of method with shortened signature
    super(clazz, copyMethod(clazz, method), classInfoCache);
  }

  /**
   * Transform method.
   *
   * @return Transformed method
   * @exception AnalyzerException In case of incorrect byte code of the original method
   */
  public MethodNode transform() throws AnalyzerException
  {
    analyze();

    // create copy of method with shortened signature
    List<InsnList> restoreCodes = insertCaptureCode(true);
    createRestoreHandlerCopy(restoreCodes);
    fixMaxs();

    method.desc = changeCopyDesc(method.desc);

    if (log.isDebugEnabled())
    {
      log.debug("      Copied concrete method " + methodName(clazz, method));
    }

    return method;
  }

  /**
   * Insert frame restoring code at the begin of a copied method.
   *
   * @param restoreCodes restore codes for all method calls in the method
   */
  private void createRestoreHandlerCopy(List<InsnList> restoreCodes)
  {
    assert !restoreCodes.isEmpty() : "Precondition: !restoreCodes.isEmpty()";

    if (log.isDebugEnabled())
    {
      log.debug("    Creating restore handler for copied method");
    }

    int param = firstParam(method);
    final int paramThread = param++;
    final int paramPreviousFrame = param++;

    int local = firstLocal(method);
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
    final int localFrame = local++;

    InsnList restore = new InsnList();

    // previousFrame
    restore.add(new VarInsnNode(ALOAD, paramPreviousFrame));
    restore.add(new VarInsnNode(ASTORE, localPreviousFrame));

    // frame = previousFrame.next
    restore.add(new VarInsnNode(ALOAD, localPreviousFrame));
    restore.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localFrame));

    // thread = currentThread;
    // TODO 2009-10-22 mh: rename locals to avoid this copy?
    restore.add(new VarInsnNode(ALOAD, paramThread));
    restore.add(new VarInsnNode(ASTORE, localThread));

    // restore code dispatcher
    InsnList getMethod = new InsnList();
    getMethod.add(new VarInsnNode(ALOAD, localFrame));
    getMethod.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
    restore.add(restoreCodeDispatcher(getMethod, restoreCodes, 0));

    method.instructions.insertBefore(method.instructions.getFirst(), restore);
  }
}
