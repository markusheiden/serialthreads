package org.serialthreads.transformer.classcache;

import org.apache.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.serialthreads.transformer.NotTransformableException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import static org.objectweb.asm.ClassReader.SKIP_CODE;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;
import static org.serialthreads.transformer.code.MethodCode.methodName;

/**
 * Abstract implementation of a class info cache.
 */
public abstract class AbstractClassInfoCache implements IClassInfoCache
{
  protected final Logger logger = Logger.getLogger(getClass());

  private final Map<String, ClassInfo> classes;

  /**
   * Constructor.
   */
  public AbstractClassInfoCache()
  {
    this.classes = new TreeMap<String, ClassInfo>();
  }

  public boolean isInterface(String className)
  {
    assert className != null : "Precondition: className != null";

    return getClassInfo(className).isInterface();
  }

  public Type getSuperClass(String className)
  {
    assert className != null : "Precondition: className != null";

    String superClassName = getClassInfo(className).getSuperClassName();
    return superClassName == null ? null : Type.getObjectType(superClassName);
  }

  public boolean hasSuperClass(String className, String superClassName)
  {
    assert className != null : "Precondition: className != null";
    assert superClassName != null : "Precondition: superClassName != null";

    Type classType = Type.getObjectType(className);
    if (classType.getSort() == Type.ARRAY)
    {
      return hasSuperClassArray(className, superClassName);
    }

    return getClassInfo(className).hasSuperClass(superClassName);
  }

  private boolean hasSuperClassArray(String className, String superClassName)
  {
    Type classType = Type.getObjectType(className);
    Type superClassType = Type.getObjectType(superClassName);

    if (superClassType.getSort() != Type.ARRAY)
    {
      // only possible non array super class is Object
      return superClassType.equals(Type.getType(Object.class));
    }

    if (classType.getDimensions() != superClassType.getDimensions())
    {
      // arrays dimension mismatch
      return false;
    }

    if (classType.getElementType().getSort() != Type.OBJECT)
    {
      // primitive arrays -> no inheritance
      return classType.getElementType().equals(superClassType.getElementType());
    }

    // non primitive arrays with same dimension -> check inheritance relationship of element types
    return hasSuperClass(classType.getElementType().getInternalName(), superClassType.getElementType().getInternalName());
  }

  @Override
  public boolean isExecutor(ClassNode clazz, MethodNode method)
  {
    return isExecutor(clazz.name, method.name, method.desc);
  }

  protected boolean isExecutor(String owner, String name, String desc)
  {
    if (owner.startsWith("["))
    {
      // Arrays are no executors
      return false;
    }

    ClassInfo classInfo = getClassInfo(owner);
    boolean result = classInfo.isExecutor(name + desc);
    if (logger.isDebugEnabled())
    {
      logger.debug(methodName(owner, name, desc) + " is " + (result ? "an" : "no") + " executor");
    }

    return result;
  }

  public boolean isInterruptible(ClassNode owner, MethodNode method)
  {
    return isInterruptible(owner.name, method.name, method.desc);
  }

  public boolean isInterruptible(MethodInsnNode method)
  {
    return isInterruptible(method.owner, method.name, method.desc);
  }

  public boolean isInterruptible(String owner, String name, String desc)
  {
    if (owner.startsWith("["))
    {
      // Arrays are not interruptible
      return false;
    }

    ClassInfo classInfo = getClassInfo(owner);
    boolean result = classInfo.isInterruptible(name + desc);
    if (logger.isDebugEnabled())
    {
      logger.debug(methodName(owner, name, desc) + " is" + (result ? " " : " not ") + "interruptible");
    }

    return result;
  }

  public boolean isInterrupt(MethodInsnNode method)
  {
    return isInterrupt(method.owner, method.name, method.desc);
  }

  public boolean isInterrupt(String owner, String name, String desc)
  {
    if (owner.startsWith("["))
    {
      // Arrays are not interruptible
      return false;
    }

    ClassInfo classInfo = getClassInfo(owner);
    boolean result = classInfo.isInterrupt(name + desc);
    if (logger.isDebugEnabled())
    {
      logger.debug(methodName(owner, name, desc) + " is " + (result ? "an" : "no") + " interrupt");
    }

    return result;
  }

  /**
   * Get class info for a class
   *
   * @param className internal name of class
   * @return class info
   */
  protected ClassInfo getClassInfo(String className)
  {
    ClassInfo classInfo = classes.get(className);
    if (classInfo == null)
    {
      classInfo = process(className);
      classes.put(className, classInfo);
    }

    assert classInfo != null : "Postcondition: classInfo != null";
    return classInfo;
  }

  /**
   * Parse class and extract methods which are interruptible.
   *
   * @param owner class to parse
   */
  protected ClassInfo process(String owner)
  {
    assert owner != null : "Precondition: owner != null";
    assert !classes.containsKey(owner) : "Check: !classes.containsKey(owner)";

    if (logger.isDebugEnabled())
    {
      logger.debug("Computing interruptible status for class " + owner);
    }

    String className = null;
    try
    {
      Deque<String> toProcess = new LinkedList<String>();
      ClassInfo result = scan(owner, toProcess);

      while (!toProcess.isEmpty())
      {
        className = toProcess.pollFirst();
        if (!result.hasSuperClass(className))
        {
          result.addSuperClass(className);
          result.merge(getClassInfo(className));
        }
      }

      classes.put(owner, result);

      return result;
    }
    catch (IOException e)
    {
      throw new NotTransformableException("Referenced class " + className + " not found", e);
    }
  }

  /**
   * Scan a given class for interruptible methods.
   * Does NOT do a deep scan!
   *
   * @param className name of class to scan
   * @param toProcess further classes to scan, will be filled with super class and interfaces of scanned class
   */
  protected abstract ClassInfo scan(String className, Deque<String> toProcess) throws IOException;

  /**
   * Read class info.
   *
   * @param reader class reader with byte code
   * @return class info visitor
   */
  protected ClassInfoVisitor read(ClassReader reader)
  {
    ClassInfoVisitor classInfoVisitor = new ClassInfoVisitor();
    reader.accept(classInfoVisitor, SKIP_CODE + SKIP_DEBUG + SKIP_FRAMES);
    return classInfoVisitor;
  }

  /**
   * Scan class.
   * Does NOT do a deep scan!
   * Uses ASM.
   *
   * @param classInfoVisitor class info visitor with information about the class
   * @param toProcess classes to process
   * @return ClassInfo
   */
  protected ClassInfo scan(ClassInfoVisitor classInfoVisitor, Deque<String> toProcess)
  {
    ClassInfo result = new ClassInfo(classInfoVisitor.isInterface(), classInfoVisitor.getClassName(), classInfoVisitor.getSuperClassName(), classInfoVisitor.getMethods());
    if (classInfoVisitor.getSuperClassName() != null)
    {
      // check super classes always first
      toProcess.addFirst(classInfoVisitor.getSuperClassName());
    }
    toProcess.addAll(Arrays.asList(classInfoVisitor.getInterfaceNames()));

    return result;
  }
}
