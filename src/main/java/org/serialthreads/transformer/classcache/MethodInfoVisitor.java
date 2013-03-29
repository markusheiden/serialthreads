package org.serialthreads.transformer.classcache;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Visitor which checks all methods for the presence of the @Interruptible annotation.
 */
public class MethodInfoVisitor extends MethodVisitor {
  private final String methodName;
  private final String methodDesc;
  private final Set<String> methodAnnotations = new HashSet<>();

  private final Map<String, MethodInfo> methods;

  public MethodInfoVisitor(String name, String desc, Map<String, MethodInfo> methods) {
    super(Opcodes.ASM4);

    methodName = name;
    methodDesc = desc;
    this.methods = methods;
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    methodAnnotations.add(desc);
    return null;
  }

  @Override
  public void visitEnd() {
    MethodInfo method = new MethodInfo(methodName, methodDesc, methodAnnotations);
    methods.put(method.getID(), method);
  }
}
