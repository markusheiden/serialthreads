package org.serialthreads.transformer.classcache;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * Visitor which checks all methods for the presence of the @Interruptible annotation.
 */
public class MethodInfoVisitor extends MethodVisitor {
  private final String methodName;
  private final String methodDesc;
  private final Set<Type> methodAnnotations = new HashSet<>();

  private final Map<String, MethodInfo> methods;

  public MethodInfoVisitor(String name, String desc, Map<String, MethodInfo> methods) {
    super(ASM9);

    methodName = name;
    methodDesc = desc;
    this.methods = methods;
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    methodAnnotations.add(Type.getType(desc));
    return null;
  }

  @Override
  public void visitEnd() {
    MethodInfo method = new MethodInfo(methodName, methodDesc, methodAnnotations);
    methods.put(method.getId(), method);
  }
}
