package org.serialthreads.transformer.strategies;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.tree.FieldInsnNode;
import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.transformer.IStrategy;
import org.serialthreads.transformer.Strategies;
import org.serialthreads.transformer.classcache.ClassInfoCacheASM;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.serialthreads.transformer.ByteCodes.byteCode;
import static org.serialthreads.transformer.ByteCodes.readClass;

/**
 * Test that the strategies passing the owner at method entry just do so, if the method may be restored.
 */
class OwnerCaptureTest {
    static Stream<IStrategy> strategies() {
        return Stream.of(Strategies.FREQUENT3, Strategies.FREQUENT4);
    }

    /**
     * A method without interruptible method calls never captures, so its caller never needs its owner.
     */
    @ParameterizedTest
    @MethodSource("strategies")
    void testLeaf(IStrategy strategy) throws Exception {
        assertThat(storesOwner(strategy, "leaf")).isFalse();
    }

    /**
     * A method with interruptible method calls has to pass its owner to its caller.
     */
    @ParameterizedTest
    @MethodSource("strategies")
    void testInterrupting(IStrategy strategy) throws Exception {
        assertThat(storesOwner(strategy, "interrupting")).isTrue();
    }

    /**
     * Does the transformed original method store the owner?
     */
    boolean storesOwner(IStrategy strategy, String methodName) throws IOException {
        var transformer = strategy.getTransformer(new ClassInfoCacheASM(getClass().getClassLoader()));
        var clazz = readClass(transformer.transform(byteCode(Worker.class)));

        var method = clazz.methods.stream().filter(m -> m.name.equals(methodName)).findFirst().orElseThrow();
        for (var instruction : method.instructions) {
            if (instruction.getOpcode() == PUTFIELD && ((FieldInsnNode) instruction).name.equals("owner")) {
                return true;
            }
        }
        return false;
    }

    //
    // Helper.
    //

    static class Worker {
        int value;

        @Interruptible
        void leaf() {
            value++;
        }

        @Interruptible
        void interrupting() {
            leaf();
            interrupt();
        }

        @Interrupt
        void interrupt() {
            // Method call will be redirected to interrupt code.
        }
    }
}
