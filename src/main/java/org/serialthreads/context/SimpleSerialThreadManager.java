package org.serialthreads.context;

import org.serialthreads.Executor;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Simple implementation of a thread manager.
 */
public class SimpleSerialThreadManager extends SerialThreadManager {
    private final ChainedRunnable[] chains;
    private ChainedRunnable lastExecuted;

    /**
     * Constructor.
     *
     * @param runnables
     *         Runnables.
     * @throws IllegalArgumentException
     *         If there are no runnables.
     */
    public SimpleSerialThreadManager(IRunnable... runnables) {
        chains = ChainedRunnable.chain(runnables);
        lastExecuted = chains[chains.length - 1];
    }

    @Override
    @Executor
    public void execute() {
        // Loop until a chain finishes.
        var chain = lastExecuted;
        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                chain = chain.next;
                chain.runnable.run();
            }
        } catch (ThreadFinishedException e) {
            // Expected: execution finished normally due to the end of a serial thread.
            // TODO 2009-12-09 mh: Avoid cast.
            ((Stack) chain.runnable.getThread()).reset();
            close();
        }

        lastExecuted = chain;
    }

    @Override
    @Executor
    public void execute(int interrupts) {
        requireThat(interrupts, "interrupts").isPositive();

        // long, because the product may overflow an int.
        var loops = (long) interrupts * chains.length;

        // Loop until a chain finishes.
        var chain = lastExecuted;
        try {
            do {
                chain = chain.next;
                chain.runnable.run();
            } while (--loops > 0);

            // Execution finished, because the given number of interrupts have been processed.
        } catch (ThreadFinishedException e) {
            // Expected: execution finished normally due to the end of a serial thread.
            // TODO 2009-12-09 mh: Avoid cast.
            ((Stack) chain.runnable.getThread()).reset();
            close();
        }

        lastExecuted = chain;
    }
}
