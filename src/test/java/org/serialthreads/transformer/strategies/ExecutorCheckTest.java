package org.serialthreads.transformer.strategies;

import org.junit.jupiter.api.Test;
import org.serialthreads.Executor;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.LoadUntransformedException;
import org.serialthreads.transformer.NotTransformableException;
import org.serialthreads.transformer.classcache.ClassInfoCacheASM;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.serialthreads.transformer.Strategies.DEFAULT;

/**
 * Test for the check of executors in {@link AbstractTransformer}.
 */
class ExecutorCheckTest {
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

  //
  // Helper.
  //

  byte[] byteCode(Class<?> clazz) throws IOException {
    var classFile = clazz.getName().replace('.', '/') + ".class";
    try (var in = clazz.getClassLoader().getResourceAsStream(classFile)) {
      return in.readAllBytes();
    }
  }

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

  interface Worker {
    @Interruptible
    void work();
  }
}
