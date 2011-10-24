package org.serialthreads.transformer.classcache;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Map;
import java.util.TreeMap;

/**
 * Visitor which checks all methods for the presence of the @Interruptible annotation.
 */
public class ClassInfoVisitor extends ClassVisitor
{
  private boolean isInterface;
  private String className;
  private String superClassName;
  private String[] interfaceNames;

  private final Map<String, MethodInfo> methods;

  public ClassInfoVisitor()
  {
    super(Opcodes.ASM4);

    this.methods = new TreeMap<>();
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

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
  {
    return new MethodInfoVisitor(name, desc, methods);
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

  public Map<String, MethodInfo> getMethods()
  {
    return methods;
  }
}
