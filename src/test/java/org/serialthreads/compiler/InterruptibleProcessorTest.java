package org.serialthreads.compiler;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.net.URI;
import java.util.List;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.JavaFileObject.Kind.SOURCE;
import static javax.tools.ToolProvider.getSystemJavaCompiler;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link InterruptibleProcessor}.
 */
class InterruptibleProcessorTest {
    // language=java
    static final String BASE = """
            import org.serialthreads.Interruptible;
            
            interface Base {
              @Interruptible
              void interruptible();
            
              void notInterruptible();
            }
            """;

    @Test
    void matchingStatus() {
        // language=java
        var diagnostics = compile("""
                import org.serialthreads.Interruptible;
                
                class Impl implements Base {
                  @Interruptible
                  public void interruptible() {}
                
                  public void notInterruptible() {}
                
                  @Interruptible
                  void notOverriding() {}
                }
                """);

        assertThat(diagnostics).isEmpty();
    }

    @Test
    void missingInterruptible() {
        // language=java
        var diagnostics = compile("""
                class Impl implements Base {
                  public void interruptible() {}
                
                  public void notInterruptible() {}
                }
                """);

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.getKind()).isEqualTo(ERROR);
            assertThat(diagnostic.getMessage(null))
                    .contains("interruptible() should be interruptible", "Base is interruptible");
        });
    }

    @Test
    void unexpectedInterruptible() {
        // language=java
        var diagnostics = compile("""
                import org.serialthreads.Interruptible;
                
                class Impl implements Base {
                  @Interruptible
                  public void interruptible() {}
                
                  @Interruptible
                  public void notInterruptible() {}
                }
                """);

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.getKind()).isEqualTo(ERROR);
            assertThat(diagnostic.getMessage(null))
                    .contains("notInterruptible() may not be interruptible", "Base is not interruptible");
        });
    }

    //
    // Helper.
    //

    /**
     * Check the given source of the class "Impl" together with {@link #BASE}.
     *
     * @param javaCode
     *         Source of the class "Impl".
     * @return Diagnostics of the compiler.
     */
    List<Diagnostic<? extends JavaFileObject>> compile(String javaCode) {
        var compiler = getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var options = List.of("-proc:only", "-classpath", System.getProperty("java.class.path"));
        var task = compiler.getTask(null, null, diagnostics, options, null, List.of(
                source("Base", BASE),
                source("Impl", javaCode)));
        task.setProcessors(List.of(new InterruptibleProcessor()));
        task.call();

        return diagnostics.getDiagnostics();
    }

    JavaFileObject source(String name, String content) {
        return new SimpleJavaFileObject(URI.create("string:///" + name + ".java"), SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return content;
            }
        };
    }
}
