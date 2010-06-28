package org.serialthreads.context;

/**
 * Interface for executing (single) frames.
 */
public interface IFrameExecutor
{
  public void executeFrame(Stack thread, StackFrame frame);
}
