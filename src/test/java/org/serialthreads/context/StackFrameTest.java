package org.serialthreads.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    assertNull(frame.owner);
    assertEquals(-1, frame.method);
    assertNull(frame.stackObject0);
    assertNull(frame.stackObject7);
    assertNull(frame.localObject0);
    assertNull(frame.localObject7);
    assertNull(frame.stackObjects[0]);
    assertNull(frame.localObjects[0]);
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
    assertEquals(32, frame.stackObjects.length);

    frame.resize(10);

    // The grown array has not been shrunk.
    assertEquals(32, frame.stackObjects.length);
    // The other arrays have been resized.
    assertEquals(16, frame.stackInts.length);
    // The pushed values are still there.
    for (int i = 16; i >= 0; i--) {
      assertEquals("object" + i, frame.popStackObject());
    }
  }

  /**
   * Test that a resize grows all arrays to the needed size.
   */
  @Test
  void testResize() {
    var frame = new StackFrame(null, null, 8);

    frame.resize(20);

    assertEquals(32, frame.stackObjects.length);
    assertEquals(32, frame.stackInts.length);
    assertEquals(32, frame.stackLongs.length);
    assertEquals(32, frame.stackFloats.length);
    assertEquals(32, frame.stackDoubles.length);
    assertEquals(32, frame.localObjects.length);
    assertEquals(32, frame.localInts.length);
    assertEquals(32, frame.localLongs.length);
    assertEquals(32, frame.localFloats.length);
    assertEquals(32, frame.localDoubles.length);
  }
}
