package org.serialthreads.context;

import org.serialthreads.Interruptible;

/**
 * Runnable interface for serial threads.
 * <p>
 * TODO 2010-02-03 mh: Move to org.serialthreads?
 */
public interface IRunnable {
    /**
     * Run serial thread.
     *
     * @throws ThreadFinishedException
     *         If thread terminates.
     */
    @Interruptible
    void run() throws ThreadFinishedException;
}
