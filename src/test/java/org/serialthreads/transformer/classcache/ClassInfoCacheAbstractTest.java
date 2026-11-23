package org.serialthreads.transformer.classcache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.serialthreads.Interruptible;
import org.serialthreads.transformer.NotTransformableException;

import java.util.Map;
import java.util.SortedMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for ClassInfoCache.
 */
abstract class ClassInfoCacheAbstractTest {
    protected IClassInfoCache cache;

    @BeforeEach
    void setUp() {
        cache = createCache(getClass().getClassLoader());
    }

    /**
     * Create the cache to test.
     *
     * @param classLoader
     *         Class loader to load class files with.
     */
    protected abstract IClassInfoCache createCache(ClassLoader classLoader);

    @Test
    void testIsInterruptible() {
        var mapName = Type.getType(Map.class).getInternalName();
        var sortedMapName = Type.getType(SortedMap.class).getInternalName();
        var objectDesc = Type.getType(Object.class).getDescriptor();
        assertThat(cache.isInterruptible(sortedMapName, "put", "(" + objectDesc + objectDesc + ")" + objectDesc)).isFalse();
        assertThat(cache.isInterruptible(mapName, "put", "(" + objectDesc + objectDesc + ")" + objectDesc)).isFalse();
        assertThat(cache.isInterruptible(sortedMapName, "put", "(" + objectDesc + objectDesc + ")" + objectDesc)).isFalse();
    }

    /**
     * Private methods are not inherited, so a method with the same signature in a subclass is unrelated.
     */
    @Test
    void testIsInterruptible_private() {
        assertThat(cache.isInterruptible(name(Base.class), "privateMethod", "()V")).isTrue();
        assertThat(cache.isInterruptible(name(Sub.class), "privateMethod", "()V")).isFalse();
    }

    /**
     * Static methods are inherited, but a static method with the same signature in a subclass just hides them.
     */
    @Test
    void testIsInterruptible_static() {
        assertThat(cache.isInterruptible(name(Base.class), "staticMethod", "()V")).isTrue();
        assertThat(cache.isInterruptible(name(Sub.class), "staticMethod", "()V")).isFalse();
        assertThat(cache.isInterruptible(name(SubInheriting.class), "staticMethod", "()V")).isTrue();
    }

    /**
     * Overriding methods have to be interruptible, if the overridden method is interruptible.
     */
    @Test
    void testIsInterruptible_override() {
        assertThat(cache.isInterruptible(name(Base.class), "method", "()V")).isTrue();
        assertThat(cache.isInterruptible(name(SubInheriting.class), "method", "()V")).isTrue();
        assertThatThrownBy(() -> cache.isInterruptible(name(SubMismatching.class), "method", "()V"))
                .isInstanceOf(NotTransformableException.class);
    }

    private static String name(Class<?> clazz) {
        return Type.getType(clazz).getInternalName();
    }

    static class Base {
        @Interruptible
        private void privateMethod() {
        }

        @Interruptible
        static void staticMethod() {
        }

        @Interruptible
        void method() {
        }
    }

    static class Sub extends Base {
        private void privateMethod() {
        }

        static void staticMethod() {
        }
    }

    static class SubInheriting extends Base {
    }

    static class SubMismatching extends Base {
        @Override
        void method() {
        }
    }
}
