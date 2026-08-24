package org.serialthreads.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link StackFrame}.
 */
class StackFrameTest {
  /**
   * Test that reset clears all object slots, so that no references leak.
   */
  @Test
  void testReset() {
    var frame = new StackFrame(null, null, 8);
    frame.owner = "owner";
    frame.method = 7;
    frame.stackObject0 = "stackFast0";
    frame.stackObject7 = "stackFast7";
    frame.localObject0 = "localFast0";
    frame.localObject7 = "localFast7";
    frame.stackObjects[0] = "stackSlow0";
    frame.localObjects[0] = "localSlow0";

    frame.reset();

    assertThat(frame.owner).isNull();
    assertThat(frame.method).isEqualTo(-1);
    assertThat(frame.stackObject0).isNull();
    assertThat(frame.stackObject7).isNull();
    assertThat(frame.localObject0).isNull();
    assertThat(frame.localObject7).isNull();
    assertThat(frame.stackObjects[0]).isNull();
    assertThat(frame.localObjects[0]).isNull();
  }

  /**
   * Test that a resize does not shrink arrays which already have grown beyond the frame size due to pushes.
   */
  @Test
  void testResize_afterGrow() {
    var frame = new StackFrame(null, null, 8);
    // Grow the object stack from 8 via 16 to 32 elements.
    for (int i = 0; i < 17; i++) {
      frame.pushStackObject("object" + i);
    }
    assertThat(frame.stackObjects).hasSize(32);

    frame.resize(10);

    // The grown array has not been shrunk.
    assertThat(frame.stackObjects).hasSize(32);
    // The other arrays have been resized.
    assertThat(frame.stackInts).hasSize(16);
    // The pushed values are still there.
    for (int i = 16; i >= 0; i--) {
      assertThat(frame.popStackObject()).isEqualTo("object" + i);
    }
  }

  /**
   * Test that a resize grows all arrays to the needed size.
   */
  @Test
  void testResize() {
    var frame = new StackFrame(null, null, 8);

    frame.resize(20);

    assertThat(frame.stackObjects).hasSize(32);
    assertThat(frame.stackInts).hasSize(32);
    assertThat(frame.stackLongs).hasSize(32);
    assertThat(frame.stackFloats).hasSize(32);
    assertThat(frame.stackDoubles).hasSize(32);
    assertThat(frame.localObjects).hasSize(32);
    assertThat(frame.localInts).hasSize(32);
    assertThat(frame.localLongs).hasSize(32);
    assertThat(frame.localFloats).hasSize(32);
    assertThat(frame.localDoubles).hasSize(32);
  }
}
