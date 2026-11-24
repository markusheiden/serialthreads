package org.serialthreads.transformer.classcache;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.Map;
import java.util.TreeMap;

import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ASM9;

/**
 * Visitor which checks all methods for the presence of the @Interruptible annotation.
 */
public class ClassInfoVisitor extends ClassVisitor {
    private boolean isInterface;
    // Are set by visit(), which is called first.
    @SuppressWarnings("NullAway.Init")
    private String className;
    private @Nullable String superClassName;
    private String @Nullable [] interfaceNames;

    private final Map<String, MethodInfo> methods;

    public ClassInfoVisitor() {
        super(ASM9);

        this.methods = new TreeMap<>();
    }

    @Override
    public void visit(int version, int access, String name, @Nullable String signature, @Nullable String superName, String @Nullable [] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);

        isInterface = (access & ACC_INTERFACE) != 0;
        className = name;
        superClassName = superName;
        interfaceNames = interfaces;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        return new MethodInfoVisitor(access, name, desc, methods);
    }

    public boolean isInterface() {
        return isInterface;
    }

    public String getClassName() {
        return className;
    }

    public @Nullable String getSuperClassName() {
        return superClassName;
    }

    public String @Nullable [] getInterfaceNames() {
        return interfaceNames;
    }

    public Map<String, MethodInfo> getMethods() {
        return methods;
    }
}
