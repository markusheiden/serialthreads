package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

/**
 * Test object for returns which are shared by a tail call and another interruptible method call.
 */
public class TestMergedReturn implements IRunnable {
    public String valueFirst = "";
    public String valueSecond = "";

    @Interruptible
    public void run() {
        valueFirst = method(true);
        valueSecond = method(false);
        interrupt();
    }

    /**
     * Both method calls share the same return instruction.
     * Just the second one is directly followed by it.
     */
    @Interruptible
    private String method(boolean first) {
        return first ? first() : second();
    }

    @Interruptible
    private String first() {
        interrupt();
        return "first";
    }

    @Interruptible
    private String second() {
        interrupt();
        return "second";
    }

    @Interrupt
    private void interrupt() {
        throw new UnsupportedOperationException("Method calls should be replaced by interrupt code");
    }
}
