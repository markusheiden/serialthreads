package org.serialthreads.transformer.classcache;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.EmptyVisitor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Visitor which checks all methods for the presence of the @Interruptible annotation.
 */
public class ClassInfoVisitor extends EmptyVisitor
{
  private boolean isInterface;
  private String className;
  private String superClassName;
  private String[] interfaceNames;

  private String methodName;
  private String methodDesc;
  private Set<String> methodAnnotations = new HashSet<String>();

  private final Map<String, MethodInfo> methods;

  public ClassInfoVisitor()
  {
    this.methods = new TreeMap<String, MethodInfo>();
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
  {
    super.visit(version, access, name, signature, superName, interfaces);

    isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
    className = name;
    superClassName = superName;
    interfaceNames = interfaces;
  }

  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
  {
    methodName = name;
    methodDesc = desc;
    return this;
  }

  public AnnotationVisitor visitAnnotation(String desc, boolean visible)
  {
    methodAnnotations.add(desc);
    return null;
  }

  public void visitEnd()
  {
    // distinguish between method related visitEnd() and class related visitEnd()
    if (methodName != null)
    {
      // method related visitEnd()
      MethodInfo method = new MethodInfo(methodName, methodDesc, methodAnnotations);
      methods.put(method.getID(), method);

      methodName = null;
      methodDesc = null;
      methodAnnotations.clear();
    }
  }

  public Map<String, MethodInfo> getMethods()
  {
    return methods;
  }

  public boolean isInterface()
  {
    return isInterface;
  }

  public String getClassName()
  {
    return className;
  }

  public String getSuperClassName()
  {
    return superClassName;
  }

  public String[] getInterfaceNames()
  {
    return interfaceNames;
  }
}
