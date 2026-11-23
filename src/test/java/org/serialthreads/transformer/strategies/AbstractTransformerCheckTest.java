package org.serialthreads.transformer.strategies;

import org.junit.jupiter.api.Test;
import org.serialthreads.Executor;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.LoadUntransformedException;
import org.serialthreads.transformer.NotTransformableException;
import org.serialthreads.transformer.classcache.ClassInfoCacheASM;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.serialthreads.transformer.ByteCodes.byteCode;
import static org.serialthreads.transformer.Strategies.DEFAULT;

/**
 * Test for the check of not interruptible methods in {@link AbstractTransformer}.
 */
class AbstractTransformerCheckTest {
  private final ITransformer transformer = DEFAULT.getTransformer(new ClassInfoCacheASM(getClass().getClassLoader()));

  /**
   * An executor calling just run() needs no transformation.
   */
  @Test
  void testExecutor_run() throws Exception {
    var byteCode = byteCode(RunExecutor.class);
    assertThatThrownBy(() -> transformer.transform(byteCode))
            .isInstanceOf(LoadUntransformedException.class);
  }

  /**
   * An executor may not call other interruptible methods than run().
   */
  @Test
  void testExecutor_interruptible() throws Exception {
    var byteCode = byteCode(InterruptibleExecutor.class);
    assertThatThrownBy(() -> transformer.transform(byteCode))
      .isInstanceOf(NotTransformableException.class)
      .hasMessageContaining("may only call run");
  }

  /**
   * A not interruptible method may not call interruptible methods.
   */
  @Test
  void testMethod_interruptible() throws Exception {
    var byteCode = byteCode(InterruptibleMethod.class);
    assertThatThrownBy(() -> transformer.transform(byteCode))
      .isInstanceOf(NotTransformableException.class)
      .hasMessageContaining("Not interruptible method")
      .hasMessageContaining("#test(");
  }

  /**
   * A constructor may not call interruptible methods.
   */
  @Test
  void testConstructor_interruptible() throws Exception {
    var byteCode = byteCode(InterruptibleConstructor.class);
    assertThatThrownBy(() -> transformer.transform(byteCode))
      .isInstanceOf(NotTransformableException.class)
      .hasMessageContaining("Not interruptible method")
      .hasMessageContaining("#<init>(");
  }

  /**
   * An interruptible method may not call run(), just executors may do that.
   */
  @Test
  void testInterruptible_run() throws Exception {
    var byteCode = byteCode(InterruptibleRun.class);
    assertThatThrownBy(() -> transformer.transform(byteCode))
      .isInstanceOf(NotTransformableException.class)
      .hasMessageContaining("may not call run");
  }

  //
  // Helper.
  //

  static class RunExecutor {
    @Executor
    void execute(IRunnable runnable) {
      runnable.run();
    }
  }

  static class InterruptibleExecutor {
    @Executor
    void execute(Worker worker) {
      worker.work();
    }
  }

  static class InterruptibleMethod {
    void test(Worker worker) {
      worker.work();
    }
  }

  static class InterruptibleConstructor {
    InterruptibleConstructor(Worker worker) {
      worker.work();
    }
  }

  static class InterruptibleRun {
    @Interruptible
    void test(IRunnable runnable) {
      runnable.run();
    }
  }

  interface Worker {
    @Interruptible
    void work();
  }
}
