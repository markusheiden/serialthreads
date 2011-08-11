package org.serialthreads.transformer.classcache;

import org.ow2.asm.Type;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Method info for methods of scanned classes.
 */
public class MethodInfo
{
  private final String name;
  private final String desc;
  private final Set<String> annotations;

  /**
   * Constructor.
   *
   * @param name name of method
   * @param desc method descriptor
   * @param annotations descriptors of the type of the annotations of this method
   */
  public MethodInfo(String name, String desc, Set<String> annotations)
  {
    assert name != null : "Precondition: name != null";
    assert desc != null : "Precondition: desc != null";
    assert annotations != null : "Precondition: annotations != null";

    this.name = name;
    this.desc = desc;
    this.annotations = Collections.unmodifiableSet(new HashSet<String>(annotations));
  }

  /**
   * ID of method: name + desc.
   */
  public String getID()
  {
    return name + desc;
  }

  /**
   * Name of method.
   */
  public String getName()
  {
    return name;
  }

  /**
   * Method descriptor.
   */
  public String getDesc()
  {
    return desc;
  }

  /**
   * Type of all annotations of this method.
   */
  public Set<String> getAnnotations()
  {
    return annotations;
  }

  /**
   * Is this method annotated with a given type of annotation?
   *
   * @param annotationType type of annotation
   */
  public boolean hasAnnotation(Type annotationType)
  {
    assert annotationType != null : "Precondition: annotationType != null";

    return annotations.contains(annotationType.getDescriptor());
  }

  /**
   * Copy this method info.
   */
  public MethodInfo copy()
  {
    MethodInfo result = new MethodInfo(name, desc, annotations);

    assert result != null : "Postcondition: result != null";
    assert getID().equals(result.getID()) : "Postcondition: getID().equals(result.getID())";
    return result;
  }

  @Override
  public String toString()
  {
    return "Method info " + getID();
  }
}
