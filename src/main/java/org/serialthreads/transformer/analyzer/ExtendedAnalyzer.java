package org.serialthreads.transformer.analyzer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Extended analyzer.
 * Supports detection of "this" and method parameters in the locals and on the stack.
 * Always uses a verifier as interpreter.
 */
public class ExtendedAnalyzer extends Analyzer<BasicValue>
{
  /**
   * Analyze a method to compute its frames.
   *
   * @param clazz owner of method
   * @param method method
   * @param classInfoCache class info cache
   * @return frames
   * @exception AnalyzerException In case of incorrect byte code of the original method
   */
  public static Frame[] analyze(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) throws AnalyzerException
  {
    Type classType = Type.getObjectType(clazz.name);
    Type superClassType = Type.getObjectType(clazz.superName);
    List<Type> interfaceTypes = new ArrayList<>(clazz.interfaces.size());
    for (String interfaceName : clazz.interfaces)
    {
      interfaceTypes.add(Type.getObjectType(interfaceName));
    }
    boolean isInterface = (clazz.access & ACC_INTERFACE) != 0;

    return new ExtendedAnalyzer(classInfoCache, classType, superClassType, interfaceTypes, isInterface).analyze(clazz.name, method);
  }

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
    List<Type> currentClassInterfaces,
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
  protected ExtendedFrame newFrame(Frame<? extends BasicValue> src)
  {
    return new ExtendedFrame(src);
  }
}
