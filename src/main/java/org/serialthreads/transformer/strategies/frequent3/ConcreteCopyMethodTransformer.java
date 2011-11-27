package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.MethodNeedsNoTransformationException;

import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.serialthreads.transformer.code.MethodCode.firstLocal;
import static org.serialthreads.transformer.code.MethodCode.firstParam;
import static org.serialthreads.transformer.code.MethodCode.methodName;

/**
 * Method transformer for copies of concrete methods.
 */
@SuppressWarnings({"UnusedDeclaration", "UnusedAssignment", "UnnecessaryLocalVariable"})
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
   * @exception MethodNeedsNoTransformationException In case the method needs no byte code transformation
   */
  public MethodNode transform() throws AnalyzerException, MethodNeedsNoTransformationException
  {
    Frame[] frames = analyze();

    voidReturns();
    Map<MethodInsnNode, Integer> copyMethodCalls = interruptibleMethodCalls();
    List<InsnList> restoreCodes = insertCaptureCode(frames, copyMethodCalls, true);
    createRestoreHandlerCopy(restoreCodes);
    addThreadAndFrame(copyMethodCalls.keySet());
    method.desc = changeCopyDesc(method.desc);
    fixMaxs();

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
    final int localThread = local++;
    final int localPreviousFrame = local++;
    final int localFrame = local++;

    InsnList restore = new InsnList();

    // TODO 2011-10-04 mh: Avoid this copy, it is needed just for capturing the return value. Fix order of copies?
    restore.add(new VarInsnNode(ALOAD, paramPreviousFrame));
    restore.add(new VarInsnNode(ASTORE, localPreviousFrame));

    // frame = previousFrame.next
    restore.add(new VarInsnNode(ALOAD, paramPreviousFrame));
    restore.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    restore.add(new VarInsnNode(ASTORE, localFrame));

    // thread = currentThread;
    // TODO 2009-10-22 mh: how to avoid this copy???
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
