package org.serialthreads.transformer.strategies;

import org.junit.jupiter.api.Test;
import org.serialthreads.transformer.classcache.ClassInfoCacheASM;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    assertEquals("java/util/ArrayList", writer.getCommonSuperClass("java/util/ArrayList", "java/util/ArrayList"));
    // Direct super class, both directions.
    assertEquals("java/util/AbstractList", writer.getCommonSuperClass("java/util/AbstractList", "java/util/ArrayList"));
    assertEquals("java/util/AbstractList", writer.getCommonSuperClass("java/util/ArrayList", "java/util/AbstractList"));
    // Sibling classes with a common super class.
    assertEquals("java/util/AbstractList", writer.getCommonSuperClass("java/util/ArrayList", "java/util/LinkedList"));
    // Unrelated classes.
    assertEquals("java/lang/Object", writer.getCommonSuperClass("java/lang/String", "java/lang/Integer"));
  }

  /**
   * Test computation of the common super class for interfaces.
   */
  @Test
  void testCommonSuperClass_interfaces() {
    // Class and implemented interface, both directions.
    assertEquals("java/util/List", writer.getCommonSuperClass("java/util/List", "java/util/ArrayList"));
    assertEquals("java/util/List", writer.getCommonSuperClass("java/util/ArrayList", "java/util/List"));
    // Interface and super interface.
    assertEquals("java/util/Collection", writer.getCommonSuperClass("java/util/List", "java/util/Collection"));
    // Unrelated interfaces.
    assertEquals("java/lang/Object", writer.getCommonSuperClass("java/util/List", "java/util/Map"));
  }

  /**
   * Test computation of the common super class for arrays.
   */
  @Test
  void testCommonSuperClass_arrays() {
    // Array and array of super class, both directions.
    assertEquals("[Ljava/lang/Number;", writer.getCommonSuperClass("[Ljava/lang/Number;", "[Ljava/lang/Integer;"));
    assertEquals("[Ljava/lang/Number;", writer.getCommonSuperClass("[Ljava/lang/Integer;", "[Ljava/lang/Number;"));
    // Arrays of unrelated classes.
    assertEquals("java/lang/Object", writer.getCommonSuperClass("[Ljava/lang/String;", "[Ljava/lang/Integer;"));
    // Array and Object.
    assertEquals("java/lang/Object", writer.getCommonSuperClass("java/lang/Object", "[Ljava/lang/String;"));
    // Array and unrelated class.
    assertEquals("java/lang/Object", writer.getCommonSuperClass("[Ljava/lang/String;", "java/lang/String"));
  }
}
