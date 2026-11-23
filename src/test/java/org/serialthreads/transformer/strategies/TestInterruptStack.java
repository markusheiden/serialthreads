package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

/**
 * Test object for interrupts of non-static interrupt methods with a non-empty operand stack.
 */
public class TestInterruptStack implements IRunnable {
    public int value = -1;

    @Interruptible
    public void run() {
        value = method(40);
        interrupt();
    }

    /**
     * At the interrupt the first summand is still on the operand stack, below the owner of the interrupt method.
     */
    @Interruptible
    private int method(int i) {
        return i + switch (i) {
            case 40 -> {
                interrupt();
                yield 2;
            }
            default -> 0;
        };
    }

    @Interrupt
    private void interrupt() {
        throw new UnsupportedOperationException("Method calls should be replaced by interrupt code");
    }
}
