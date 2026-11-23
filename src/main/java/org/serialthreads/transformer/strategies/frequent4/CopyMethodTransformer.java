package org.serialthreads.transformer.strategies.frequent4;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.MethodNodeCopier;

import java.util.List;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.methodName;

/**
 * Method transformer for copied methods.
 */
class CopyMethodTransformer extends MethodTransformer {
    /**
     * Parameter holding the previous frame in copied methods.
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
        super(clazz, MethodNodeCopier.copy(method), classInfoCache);

        this.paramPreviousFrame = param(0);
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

            replaceReturns();
            var restores = insertCaptureAndRestoreCode();
            createRestoreHandlerCopy(restores);
            addFrame();
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
     * Insert frame restoring code at the beginning of a copied method.
     *
     * @param restores
     *         Labels pointing to the generated restore codes for method calls.
     */
    private void createRestoreHandlerCopy(List<LabelNode> restores) {
        requireThat(restores, "restores").isNotEmpty();

        logger.debug("    Creating restore handler for copied method");

        var instructions = new InsnList();

        if (needsFrame()) {
            // frame = previousFrame.next; // etc.
            instructions.add(threadCode.getNextFrame(paramPreviousFrame, localFrame, false));
        } else if (paramPreviousFrame != localFrame) {
            // Reuse previousFrame for return value.
            // frame = previousFrame;
            instructions.add(new VarInsnNode(ALOAD, paramPreviousFrame));
            instructions.add(new VarInsnNode(ASTORE, localFrame));
        }

        // Restore code dispatcher.
        instructions.add(restoreCodeDispatcher(localFrame, restores, 0));

        method.instructions.insertBefore(method.instructions.getFirst(), instructions);
    }
}
