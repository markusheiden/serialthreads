package org.serialthreads.compiler;

import org.serialthreads.Interruptible;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementScanner14;
import javax.tools.Diagnostic.Kind;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * Checks correct usage of {@link Interruptible} annotations.
 */
@SupportedAnnotationTypes("*") // We need all compiled classes, because we are checking for missing annotations too.
@SupportedSourceVersion(SourceVersion.RELEASE_27)
public class InterruptibleProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var scanner = new Scanner();
        for (var root : roundEnv.getRootElements()) {
            root.accept(scanner, null);
        }

        // Most annotations are not ours, so we do not claim them.
        return false;
    }

    /**
     * Inner visitor class for scanning types.
     */
    private class Scanner extends ElementScanner14<Void, Void> {
        @Override
        public Void visitExecutable(ExecutableElement element, Void dummy) {
            try {
                check(element);
            } catch (RuntimeException e) {
                processingEnv.getMessager().printMessage(Kind.WARNING, e.getMessage());
            }
            return super.visitExecutable(element, dummy);
        }
    }

    /**
     * Check that the interruptible status of a method matches the one of the methods it overrides.
     * A mismatch is an error, because the transformer rejects such classes.
     *
     * @param overrider
     *         Method to check, whether it overrides something.
     */
    private void check(ExecutableElement overrider) {
        var types = processingEnv.getTypeUtils();
        var elements = processingEnv.getElementUtils();

        var interruptible = overrider.getAnnotation(Interruptible.class) != null;

        var overriderType = (TypeElement) requireNonNull(overrider.getEnclosingElement());
        for (var superType : types.directSupertypes(overriderType.asType())) {
            var overriddenType = (TypeElement) types.asElement(superType);
            for (var overridden : elements.getAllMembers(overriddenType)) {
                if (overridden instanceof ExecutableElement o && elements.overrides(overrider, o, overriderType)) {
                    var overriddenInterruptible = overridden.getAnnotation(Interruptible.class) != null;

                    if (interruptible && !overriddenInterruptible) {
                        processingEnv.getMessager().printMessage(ERROR,
                                "Method " + overrider + " may not be interruptible, because the overridden method in " +
                                overriddenType.getQualifiedName() + " is not interruptible",
                                overrider);
                    } else if (!interruptible && overriddenInterruptible) {
                        processingEnv.getMessager().printMessage(ERROR,
                                "Method " + overrider + " should be interruptible, because the overridden method in " +
                                overriddenType.getQualifiedName() + " is interruptible",
                                overrider);
                    }
                    break;
                }
            }
        }
    }
}
