package org.serialthreads.transformer.strategies.frequent2;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.serialthreads.transformer.code.MethodCode.isStatic;

/**
 * Method transformer for concrete methods.
 */
@SuppressWarnings({"UnusedAssignment"})
class OriginalMethodTransformer extends MethodTransformer {
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected OriginalMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
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

    insertCaptureCode();
    createRestoreHandlerMethod();
    fixMaxs();
    nameLocals();

    return method;
  }

  /**
   * Insert frame restoring code at the begin of an interruptible method.
   */
  private void createRestoreHandlerMethod() {
    logger.debug("    Creating restore handler for method");

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    InsnList instructions = new InsnList();

    if (isStatic(method)) {
      // thread = SerialThreadManager.getThread();
      instructions.add(threadCode.getThread(localThread));
    } else {
      LabelNode exist = new LabelNode();
      // thread = this.$$thread$$;
      instructions.add(threadCode.getRunThread(clazz.name, localThread));
      instructions.add(new VarInsnNode(ALOAD, localThread));
      instructions.add(new JumpInsnNode(IFNONNULL, exist));
      // thread = SerialThreadManager.getThread();
      instructions.add(threadCode.getThread(localThread));
      // this.$$thread$$ = thread;
      instructions.add(threadCode.initThread(clazz.name, localThread));
      instructions.add(exist);
    }
    // previousFrame = thread.frame;
    instructions.add(threadCode.getPreviousFrame(localThread, localPreviousFrame));
    // frame = previousFrame.next; // etc.
    instructions.add(threadCode.getNextFrame(localPreviousFrame, localFrame, true));

    // Store current frame, so next method can fetch is as previous frame.
    // thread.frame = frame;
    instructions.add(threadCode.setFrame(localThread, localFrame));

    // insert generated byte code
    method.instructions.insertBefore(method.instructions.getFirst(), instructions);
  }
}
