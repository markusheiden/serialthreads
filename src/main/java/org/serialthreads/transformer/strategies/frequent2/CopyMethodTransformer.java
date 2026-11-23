package org.serialthreads.transformer.strategies.frequent2;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.MethodNodeCopier;

import java.util.List;

import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.methodName;

/**
 * Method transformer for copies of concrete methods.
 */
@SuppressWarnings({ "UnusedAssignment" })
class CopyMethodTransformer extends MethodTransformer {
    /**
     * Parameter holding the thread.
     */
    private final int paramThread;

    /**
     * Parameter holding the previous frame.
     */
    private final int paramPreviousFrame;

    /**
     * Constructor.
     *
     * @param clazz
     *         Class to transform.
     * @param method
     *         Method to transform.
     * @param classInfoCache
     *         Class cache to use.
     */
    protected CopyMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
        // Create copy of method with shortened signature.
        super(clazz, MethodNodeCopier.copy(method), classInfoCache);

        this.paramThread = param(0);
        this.paramPreviousFrame = param(1);
    }

    @Override
    protected boolean suppressOwner() {
        return true;
    }

    /**
     * Transform method.
     *
     * @return Transformed method.
     * @throws AnalyzerException
     *         In case of incorrect byte code of the original method.
     */
    public MethodNode transform() throws AnalyzerException {
        boolean concrete = !isAbstract(method);
        if (concrete) {
            shiftLocals();
            analyze();

            // Create copy of method with shortened signature.
            var restores = insertCaptureAndRestoreCode();
            createRestoreHandlerCopy(restores);
            fixMaxs();
            nameLocals();
        }

        method.access |= ACC_SYNTHETIC;
        method.name = changeCopyName(method.name, method.desc);
        method.desc = changeCopyDesc(method.desc);
        clazz.methods.add(method);

        logger.debug("      Copied {} method {}", concrete ? "concrete" : "abstract", methodName(clazz, method));

        return method;
    }

    /**
     * Insert frame restoring code at the begin of a copied method.
     *
     * @param restores
     *         Labels pointing to the generated restore codes for method calls.
     */
    private void createRestoreHandlerCopy(List<LabelNode> restores) {
        assert !restores.isEmpty() : "Precondition: !restores.isEmpty()";

        logger.debug("    Creating restore handler for copied method");

        var instructions = new InsnList();

        // Move previousFrame to the correct local.
        instructions.add(new VarInsnNode(ALOAD, paramPreviousFrame));
        instructions.add(new VarInsnNode(ASTORE, localPreviousFrame));

        // frame = previousFrame.next; // etc.
        instructions.add(threadCode.getNextFrame(localPreviousFrame, localFrame, false));

        // Move thread to the correct local.
        instructions.add(new VarInsnNode(ALOAD, paramThread));
        instructions.add(new VarInsnNode(ASTORE, localThread));

        // Restore code dispatcher.
        instructions.add(restoreCodeDispatcher(localFrame, restores, 0));

        method.instructions.insertBefore(method.instructions.getFirst(), instructions);
    }
}
