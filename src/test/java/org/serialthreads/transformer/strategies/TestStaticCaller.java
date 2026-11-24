package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

/**
 * Test object for static interruptible methods calling interruptible instance methods.
 */
public class TestStaticCaller implements IRunnable {
    public int valueLocal = -1;
    public int valueOwner = -1;

    @Interruptible
    public void run() {
        valueLocal = staticLocal(42, this);
        valueOwner = staticOwner(this, 7);
        interrupt();
    }

    /**
     * The first parameter is in local 0, because the method is static.
     * It has to survive the interrupt, though the called method is not static.
     */
    @Interruptible
    private static int staticLocal(int i, TestStaticCaller target) {
        var result = target.method(1);
        return result + i;
    }

    /**
     * The owner of the called method is in local 0, but it is not "this", because the method is static.
     */
    @Interruptible
    private static int staticOwner(TestStaticCaller target, int i) {
        var result = target.method(i);
        return result + i;
    }

    @Interruptible
    private int method(int i) {
        interrupt();
        return i + 1;
    }

    @Interrupt
    private void interrupt() {
        throw new UnsupportedOperationException("Method calls should be replaced by interrupt code");
    }
}
