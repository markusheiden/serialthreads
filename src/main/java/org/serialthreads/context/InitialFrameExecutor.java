package org.serialthreads.context;

/**
 * Frame executor for initially starting a thread.
 */
public class InitialFrameExecutor implements IFrameExecutor
{
  @Override
  public void executeFrame(Stack thread, StackFrame frame)
  {
    ((IRunnable) frame.owner).run(); // TODO 2010-02-02 mh: .run(thread)!
  }
}
