package org.serialthreads.transformer.analyzer;

import org.ow2.asm.Type;
import org.ow2.asm.tree.analysis.Analyzer;
import org.ow2.asm.tree.analysis.Frame;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.List;

/**
 * Extended analyzer.
 * Supports detection of "this" and method parameters in the locals and on the stack.
 * Always uses a verifier as interpreter.
 */
public class ExtendedAnalyzer extends Analyzer
{
  /**
   * Constructs a new {@link ExtendedAnalyzer} to analyze a specific class.
   * This class will not be loaded into the JVM since it may be incorrect.
   *
   * @param classInfoCache class info cache
   * @param currentClass the class that is analyzed.
   * @param currentSuperClass the super class of the class that is analyzed.
   * @param currentClassInterfaces the interfaces implemented by the class that is analyzed.
   * @param isInterface if the class that is analyzed is an interface.
   */
  public ExtendedAnalyzer(
    IClassInfoCache classInfoCache,
    Type currentClass,
    Type currentSuperClass,
    List currentClassInterfaces,
    boolean isInterface)
  {
    super(new ExtendedVerifier(classInfoCache, currentClass, currentSuperClass, currentClassInterfaces, isInterface));
  }

  @Override
  protected ExtendedFrame newFrame(int nLocals, int nStack)
  {
    return new ExtendedFrame(nLocals, nStack);
  }

  @Override
  protected ExtendedFrame newFrame(Frame src)
  {
    return new ExtendedFrame(src);
  }
}
