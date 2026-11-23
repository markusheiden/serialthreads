package org.serialthreads.context;

import java.util.Collection;
import java.util.List;

/**
 * Linked list element for runnables.
 */
public final class ChainedRunnable {
    public final ITransformedRunnable runnable;
    public ChainedRunnable next;

    /**
     * Create linked chain array.
     *
     * @param runnables
     *         Runnables
     */
    public static ChainedRunnable[] chain(IRunnable... runnables) {
        return chain(List.of(runnables));
    }

    /**
     * Create linked chain array.
     *
     * @param runnables
     *         Runnables
     * @throws IllegalArgumentException
     *         if there are no runnables
     */
    public static ChainedRunnable[] chain(Collection<? extends IRunnable> runnables) {
        int count = runnables.size();
        if (count == 0) {
            throw new IllegalArgumentException("At least one runnable is needed");
        }

        var result = new ChainedRunnable[count];
        var iterator = runnables.iterator();
        result[0] = new ChainedRunnable(iterator.next());
        for (int i = 1; i < count; i++) {
            var chain = new ChainedRunnable(iterator.next());
            result[i] = chain;
            result[i - 1].next = chain;
        }
        result[count - 1].next = result[0];

        return result;
    }

    /**
     * Constructor.
     *
     * @param runnable
     *         task to execute
     */
    private ChainedRunnable(IRunnable runnable) {
        this.runnable = (ITransformedRunnable) runnable;
    }
}
