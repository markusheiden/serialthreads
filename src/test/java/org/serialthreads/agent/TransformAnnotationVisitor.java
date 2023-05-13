package org.serialthreads.agent;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.serialthreads.transformer.classcache.MethodInfo;
import org.serialthreads.transformer.classcache.MethodInfoVisitor;

import java.util.Map;
import java.util.TreeMap;

import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ASM9;

/**
 * Visitor which checks all methods for the presence of the @Interruptible annotation.
 */
public class TransformAnnotationVisitor extends ClassVisitor {
  private Transform transform;

  public TransformAnnotationVisitor() {
    super(ASM9);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    return super.visitAnnotation(descriptor, visible);
  }

  public Transform getTransform() {
    return transform;
  }
}
