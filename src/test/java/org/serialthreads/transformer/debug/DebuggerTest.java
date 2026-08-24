package org.serialthreads.transformer.debug;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link Debugger} and {@link DebugPrinter}.
 */
class DebuggerTest {
  /**
   * Test that invoke dynamic instructions are indexed like all other instructions,
   * so that the instruction indexes stay in sync with the analyzer frames.
   */
  @Test
  void testDebug_invokeDynamic() {
    var output = Debugger.debug(Indy.class.getName());

    var indexedInvokeDynamic = false;
    var expectedIndex = 0;
    for (var line : output.split("\n")) {
      if (line.startsWith("Method ")) {
        // Instruction indexes restart for each method.
        expectedIndex = 0;
        continue;
      }
      if (!line.matches("^\\d{4}.*")) {
        continue;
      }

      // All indexed lines of a method are numbered consecutively.
      var index = Integer.parseInt(line.substring(0, 4));
      assertThat(index).as("Consecutive instruction index in line: %s", line)
        .isIn(expectedIndex - 1, expectedIndex);
      expectedIndex = index + 1;

      if (line.contains("INVOKEDYNAMIC")) {
        indexedInvokeDynamic = true;
      }
    }

    assertThat(indexedInvokeDynamic).as("The invoke dynamic instruction has an instruction index").isTrue();
  }

  /**
   * Test fixture whose lambda compiles to an invoke dynamic instruction.
   */
  static class Indy {
    Runnable lambda() {
      return () -> {
        // Just for the invoke dynamic instruction.
      };
    }
  }
}
