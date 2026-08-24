package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

/**
 * Test object whose frames need the computation of a common super class:
 * The two subclasses merge at the ternary operator.
 */
public class TestTypeMerge implements IRunnable {
  public String value = "";

  // Deliberately not final, so that the ternary operator does not get folded by javac.
  private boolean flag = true;

  @Interruptible
  public void run() {
    // Force a frame merge of the two subclasses, so that their common super class gets computed.
    Base base = flag ? new SubA() : new SubB();

    interrupt();

    value = base.name();
  }

  @Interrupt
  void interrupt() {
    // Method call will be redirected to interrupt code.
  }

  /**
   * Common super class of the merged classes.
   */
  static abstract class Base {
    abstract String name();
  }

  static class SubA extends Base {
    @Override
    String name() {
      return "a";
    }
  }

  static class SubB extends Base {
    @Override
    String name() {
      return "b";
    }
  }
}
