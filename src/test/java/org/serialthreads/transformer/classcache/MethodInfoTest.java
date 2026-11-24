package org.serialthreads.transformer.classcache;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Type.INT_TYPE;

/**
 * Test for {@link MethodInfo}.
 */
class MethodInfoTest {
    @Test
    void testDefault() {
        var info = new MethodInfo(ACC_PRIVATE, "name", "desc", Set.of(INT_TYPE));

        assertThat(info.getId()).isEqualTo("namedesc");
        assertThat(info.getName()).isEqualTo("name");
        assertThat(info.getDesc()).isEqualTo("desc");
        assertThat(info.getAnnotations()).containsOnly(INT_TYPE);
        assertThat(info.isPrivate()).isTrue();
        assertThat(info.isStatic()).isFalse();
        assertThat(info.isInitializer()).isFalse();
    }

    @Test
    void testToString() {
        var info = new MethodInfo(ACC_PRIVATE, "name", "desc", Set.of(INT_TYPE));

        assertThat(info).hasToString("Method info namedesc");
    }

    @Test
    void testHasAnnotation() {
        var info = new MethodInfo(ACC_PRIVATE, "name", "desc", Set.of(INT_TYPE));

        assertThat(info.hasAnnotation(INT_TYPE)).isTrue();
    }

    @Test
    void testGetAnnotations_immutable() {
        var info = new MethodInfo(ACC_PRIVATE, "name", "desc", new HashSet<>(Set.of(INT_TYPE)));

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> info.getAnnotations().add(Type.getType(getClass())));
    }

    @Test
    void testCopy() {
        var info = new MethodInfo(ACC_PRIVATE, "name", "desc", Set.of(INT_TYPE));
        var copy = info.copy();

        assertThat(copy.getId()).isEqualTo(info.getId());
        assertThat(copy.getName()).isEqualTo(info.getName());
        assertThat(copy.getDesc()).isEqualTo(info.getDesc());
        assertThat(copy.getAnnotations()).isEqualTo(info.getAnnotations());
        assertThat(copy.isPrivate()).isTrue();
    }
}
