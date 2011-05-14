package org.serialthreads.compiler;

import org.serialthreads.Interruptible;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner6;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import java.util.List;
import java.util.Set;

/**
 * Checks correct usage of {@link org.serialthreads.Interruptible} annotations.
 */
@SupportedAnnotationTypes("*") // we need all compiled classes, because we are checking for missing annotations too
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class InterruptibleProcessor extends AbstractProcessor
{
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
  {
    Scanner scanner = new Scanner();
    for (Element root : roundEnv.getRootElements())
    {
      root.accept(scanner, null);
    }

    // Most annotations are not ours, so we do not claim them
    return false;
  }

  /**
   * Inner visitor class for scanning types.
   */
  private class Scanner extends ElementScanner6<Void, Void>
  {
    @Override
    public Void visitExecutable(ExecutableElement executableElement, Void dummy)
    {
      check(executableElement);
      return null;
    }
  }

  /**
   * Check that an interruptible method overrides another interruptible method, if it overrides anything.
   *
   * @param overrider Method to check, whether it overrides something
   */
  private void check(ExecutableElement overrider)
  {
    Types types = processingEnv.getTypeUtils();
    Elements elements = processingEnv.getElementUtils();

    boolean interruptible = overrider.getAnnotation(Interruptible.class) != null;

    TypeElement overriderType = (TypeElement) overrider.getEnclosingElement();
    for (TypeMirror superType : types.directSupertypes(overriderType.asType()))
    {
      TypeElement overriddenType = (TypeElement) types.asElement(superType);
      for (Element overridden : elements.getAllMembers(overriddenType))
      {
        if (overridden instanceof ExecutableElement && elements.overrides(overrider, (ExecutableElement) overridden, overriderType))
        {
          boolean overriddenInterruptible = overridden.getAnnotation(Interruptible.class) != null;

          if (interruptible && !overriddenInterruptible)
          {
            processingEnv.getMessager().printMessage(Kind.WARNING,
              "Method " + overrider + " may not be interruptible, because the overridden method in " +
              overriddenType.getQualifiedName() + " is not interruptible",
              overrider);
          }
          else if (!interruptible && overriddenInterruptible)
          {
            processingEnv.getMessager().printMessage(Kind.WARNING,
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
