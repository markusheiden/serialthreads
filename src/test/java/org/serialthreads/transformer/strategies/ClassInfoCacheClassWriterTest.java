package org.serialthreads.transformer.strategies;

import org.junit.jupiter.api.Test;
import org.serialthreads.transformer.classcache.ClassInfoCacheASM;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link ClassInfoCacheClassWriter}.
 */
class ClassInfoCacheClassWriterTest {
  ClassInfoCacheClassWriter writer = new ClassInfoCacheClassWriter(new ClassInfoCacheASM(getClass().getClassLoader()));

  /**
   * Test computation of the common super class.
   */
  @Test
  void testCommonSuperClass_classes() {
    // Same class.
    assertThat(writer.getCommonSuperClass("java/util/ArrayList", "java/util/ArrayList")).isEqualTo("java/util/ArrayList");
    // Direct super class, both directions.
    assertThat(writer.getCommonSuperClass("java/util/AbstractList", "java/util/ArrayList")).isEqualTo("java/util/AbstractList");
    assertThat(writer.getCommonSuperClass("java/util/ArrayList", "java/util/AbstractList")).isEqualTo("java/util/AbstractList");
    // Sibling classes with a common super class.
    assertThat(writer.getCommonSuperClass("java/util/ArrayList", "java/util/LinkedList")).isEqualTo("java/util/AbstractList");
    // Unrelated classes.
    assertThat(writer.getCommonSuperClass("java/lang/String", "java/lang/Integer")).isEqualTo("java/lang/Object");
  }

  /**
   * Test computation of the common super class for interfaces.
   */
  @Test
  void testCommonSuperClass_interfaces() {
    // Class and implemented interface, both directions.
    assertThat(writer.getCommonSuperClass("java/util/List", "java/util/ArrayList")).isEqualTo("java/util/List");
    assertThat(writer.getCommonSuperClass("java/util/ArrayList", "java/util/List")).isEqualTo("java/util/List");
    // Interface and super interface.
    assertThat(writer.getCommonSuperClass("java/util/List", "java/util/Collection")).isEqualTo("java/util/Collection");
    // Unrelated interfaces.
    assertThat(writer.getCommonSuperClass("java/util/List", "java/util/Map")).isEqualTo("java/lang/Object");
  }

  /**
   * Test computation of the common super class for arrays.
   */
  @Test
  void testCommonSuperClass_arrays() {
    // Array and array of super class, both directions.
    assertThat(writer.getCommonSuperClass("[Ljava/lang/Number;", "[Ljava/lang/Integer;")).isEqualTo("[Ljava/lang/Number;");
    assertThat(writer.getCommonSuperClass("[Ljava/lang/Integer;", "[Ljava/lang/Number;")).isEqualTo("[Ljava/lang/Number;");
    // Arrays of unrelated classes.
    assertThat(writer.getCommonSuperClass("[Ljava/lang/String;", "[Ljava/lang/Integer;")).isEqualTo("java/lang/Object");
    // Array and Object.
    assertThat(writer.getCommonSuperClass("java/lang/Object", "[Ljava/lang/String;")).isEqualTo("java/lang/Object");
    // Array and unrelated class.
    assertThat(writer.getCommonSuperClass("[Ljava/lang/String;", "java/lang/String")).isEqualTo("java/lang/Object");
  }
}
