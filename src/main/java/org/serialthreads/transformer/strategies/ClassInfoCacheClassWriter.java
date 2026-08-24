package org.serialthreads.transformer.strategies;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.serialthreads.transformer.classcache.IClassInfoCache;

/**
 * {@link ClassWriter} which computes the common super classes for frames using a class info cache.
 */
class ClassInfoCacheClassWriter extends ClassWriter {
  private static final String OBJECT_NAME = Type.getType(Object.class).getInternalName();

  /**
   * Class info cache to look up the class hierarchy.
   */
  private final IClassInfoCache classInfoCache;

  /**
   * Constructor.
   *
   * @param classInfoCache class info cache to look up the class hierarchy
   */
  ClassInfoCacheClassWriter(IClassInfoCache classInfoCache) {
    super(COMPUTE_FRAMES);

    this.classInfoCache = classInfoCache;
  }

  @Override
  protected String getCommonSuperClass(String type1, String type2) {
    if (type1.equals(type2)) {
      return type1;
    }
    if (classInfoCache.hasSuperClass(type2, type1)) {
      return type1;
    }
    if (classInfoCache.hasSuperClass(type1, type2)) {
      return type2;
    }
    if (type1.startsWith("[") || type2.startsWith("[")) {
      // Arrays without inheritance relationship just have Object as common super class.
      return OBJECT_NAME;
    }
    if (classInfoCache.isInterface(type1) || classInfoCache.isInterface(type2)) {
      // Interfaces without inheritance relationship default to Object, like the default implementation does.
      return OBJECT_NAME;
    }

    // Search for the first class in the super class chain of type1 which type2 is derived from too.
    var commonSuperClass = type1;
    do {
      var superClass = classInfoCache.getSuperClass(commonSuperClass);
      if (superClass == null) {
        // The chain ended at Object.
        return OBJECT_NAME;
      }
      commonSuperClass = superClass.getInternalName();
    } while (!classInfoCache.hasSuperClass(type2, commonSuperClass));

    return commonSuperClass;
  }
}
