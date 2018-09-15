package org.serialthreads.transformer.classcache;

import org.objectweb.asm.Type;

import java.util.Set;

/**
 * Method info for methods of scanned classes.
 */
public class MethodInfo {
  private final String id;
  private final String name;
  private final String desc;
  private final Set<Type> annotations;

  /**
   * Constructor.
   *
   * @param name name of method
   * @param desc method descriptor
   * @param annotations descriptors of the type of the annotations of this method
   */
  public MethodInfo(String name, String desc, Set<Type> annotations) {
    assert name != null : "Precondition: name != null";
    assert desc != null : "Precondition: desc != null";
    assert annotations != null : "Precondition: annotations != null";

    this.id = name + desc;
    this.name = name;
    this.desc = desc;
    this.annotations = Set.copyOf(annotations);
  }

  /**
   * Id of method: name + desc.
   */
  public String getId() {
    return id;
  }

  /**
   * Name of method.
   */
  public String getName() {
    return name;
  }

  /**
   * Method descriptor.
   */
  public String getDesc() {
    return desc;
  }

  /**
   * Type of all annotations of this method.
   */
  public Set<Type> getAnnotations() {
    return annotations;
  }

  /**
   * Is this method annotated with a given type of annotation?
   *
   * @param annotationType type of annotation
   */
  public boolean hasAnnotation(Type annotationType) {
    assert annotationType != null : "Precondition: annotationType != null";

    return annotations.contains(annotationType);
  }

  /**
   * Copy this method info.
   */
  public MethodInfo copy() {
    MethodInfo result = new MethodInfo(name, desc, annotations);

    assert result != null : "Postcondition: result != null";
    assert getId().equals(result.getId()) : "Postcondition: getID().equals(result.getID())";
    return result;
  }

  @Override
  public String toString() {
    return "Method info " + getId();
  }
}
