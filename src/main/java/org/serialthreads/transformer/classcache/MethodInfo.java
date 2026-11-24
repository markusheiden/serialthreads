package org.serialthreads.transformer.classcache;

import org.objectweb.asm.Type;

import java.util.Set;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;

/**
 * Method info for methods of scanned classes.
 */
public class MethodInfo {
    private final int access;
    private final String id;
    private final String name;
    private final String desc;
    private final Set<Type> annotations;

    /**
     * Constructor.
     *
     * @param access
     *         Access flags of method.
     * @param name
     *         Name of method.
     * @param desc
     *         Method descriptor.
     * @param annotations
     *         Descriptors of the type of the annotations of this method.
     */
    public MethodInfo(int access, String name, String desc, Set<Type> annotations) {
        this.access = access;
        this.id = name + desc;
        this.name = name;
        this.desc = desc;
        this.annotations = Set.copyOf(annotations);
    }

    /**
     * ID of method: name + desc.
     */
    public String getId() {
        return id;
    }

    /**
     * Is this method private?
     */
    public boolean isPrivate() {
        return (access & ACC_PRIVATE) != 0;
    }

    /**
     * Is this method static?
     */
    public boolean isStatic() {
        return (access & ACC_STATIC) != 0;
    }

    /**
     * Name of method.
     */
    public String getName() {
        return name;
    }

    /**
     * Is this method a constructor or a class initializer?
     */
    public boolean isInitializer() {
        return name.startsWith("<");
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
     * @param annotationType
     *         Type of annotation.
     */
    public boolean hasAnnotation(Type annotationType) {
        return annotations.contains(annotationType);
    }

    /**
     * Copy this method info.
     */
    public MethodInfo copy() {
        var result = new MethodInfo(access, name, desc, annotations);

        requireThat(result.getId(), "result.getId()").isEqualTo(getId(), "getId()");
        return result;
    }

    @Override
    public String toString() {
        return "Method info " + getId();
    }
}
