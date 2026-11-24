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
        var frame = new StackFrame(new Stack("", 8), null, 8);
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
     * Test that a resize does not shrink arrays which already are bigger than the needed size.
     */
    @Test
    void testResize_noShrink() {
        var frame = new StackFrame(new Stack("", 8), null, 8);
        frame.resize(20);
        frame.stackObjects[31] = "object";

        frame.resize(10);

        // The arrays have not been shrunk.
        assertThat(frame.stackObjects).hasSize(32);
        assertThat(frame.stackInts).hasSize(32);
        // The values are still there.
        assertThat(frame.stackObjects[31]).isEqualTo("object");
    }

    /**
     * Test that a resize grows all arrays to the needed size.
     */
    @Test
    void testResize() {
        var frame = new StackFrame(new Stack("", 8), null, 8);

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
