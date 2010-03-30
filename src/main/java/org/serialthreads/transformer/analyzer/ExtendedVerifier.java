package org.serialthreads.transformer.analyzer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Value;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.tree.analysis.BasicValue.UNINITIALIZED_VALUE;
import static org.serialthreads.transformer.analyzer.ExtendedValue.constantInLocals;
import static org.serialthreads.transformer.analyzer.ExtendedValue.valueInLocals;

/**
 * Verifier which can merge extended values.
 */
public class ExtendedVerifier extends FixedVerifier
{
  /**
   * The class that is verified.
   */
  private final Type currentClass;

  /**
   * The super class of the class that is verified.
   */
  private final Type currentSuperClass;

  /**
   * The interfaces implemented by the class that is verified.
   */
  private final List currentClassInterfaces;

  /**
   * If the class that is verified is an interface.
   */
  private final boolean isInterface;

  /**
   * Class info cache to look up references.
   */
  private final IClassInfoCache _classInfoCache;

  /**
   * Constructor.
   *
   * TODO 2010-01-20 mh: use other constructor?
   *
   * @deprecated for tests only!
   */
  @Deprecated
  protected ExtendedVerifier()
  {
    this(null, null, null, null, false);
  }

  /**
   * Constructs a new {@link ExtendedVerifier} to verify a specific class. This
   * class will not be loaded into the JVM since it may be incorrect.
   *
   * @param classInfoCache class info cache.
   * @param currentClass the class that is verified.
   * @param currentSuperClass the super class of the class that is verified.
   * @param currentClassInterfaces the interfaces implemented by the class that is verified.
   * @param isInterface if the class that is verified is an interface.
   */
  public ExtendedVerifier(
    IClassInfoCache classInfoCache,
    Type currentClass,
    Type currentSuperClass,
    List currentClassInterfaces,
    boolean isInterface)
  {
    super(currentClass, currentSuperClass, currentClassInterfaces, isInterface);

    this.currentClass = currentClass;
    this.currentSuperClass = currentSuperClass;
    this.currentClassInterfaces = currentClassInterfaces;
    this.isInterface = isInterface;
    _classInfoCache = classInfoCache;
  }

  public BasicValue merge(Value v, Value w)
  {
    assert v != null : "Precondition: v != null";
    assert w != null : "Precondition: w != null";

    BasicValue result = (BasicValue) super.merge(v, w);
    if (result.equals(UNINITIALIZED_VALUE))
    {
      // return uninitialized value
      return result;
    }

    assert !v.equals(UNINITIALIZED_VALUE) : "Check: !v.equals(UNINITIALIZED_VALUE)";
    assert !w.equals(UNINITIALIZED_VALUE) : "Check: !w.equals(UNINITIALIZED_VALUE)";

    final ExtendedValue ev = (ExtendedValue) v;
    final ExtendedValue ew = (ExtendedValue) w;
    if (ev != result || !ev.equalsValue(ew))
    {
      // the type has been changed -> create new value with merged constant and locals or
      // the type has not been changed, but the constant or the locals have to be merged

      boolean isConstant = ev.isConstant() && ew.isConstant() &&
        (ev.getConstant() == ew.getConstant() || ev.getConstant() != null && ev.getConstant().equals(ew.getConstant()));

      Set<Integer> mergedLocals = new HashSet<Integer>(ev.getLocals());
      mergedLocals.retainAll(ew.getLocals());

      return isConstant ?
        constantInLocals(result.getType(), ev.getConstant(), mergedLocals) :
        valueInLocals(result.getType(), mergedLocals);
    }

    // the value and its locals have not been changed
    return result;
  }

  protected boolean isInterface(final Type t)
  {
    if (currentClass != null && t.equals(currentClass))
    {
      return isInterface;
    }
    return _classInfoCache.isInterface(t.getInternalName());
  }

  @Override
  protected Type getSuperClass(Type t)
  {
    if (currentClass != null && t.equals(currentClass))
    {
      return currentSuperClass;
    }

    return _classInfoCache.getSuperClass(t.getInternalName());
  }

  protected boolean isAssignableFrom(final Type t, final Type u)
  {
    if (t.equals(u))
    {
      return true;
    }
    if (currentClass != null && t.equals(currentClass))
    {
      if (getSuperClass(u) == null)
      {
        return false;
      }
      else
      {
        return isAssignableFrom(t, getSuperClass(u));
      }
    }
    if (currentClass != null && u.equals(currentClass))
    {
      if (isAssignableFrom(t, currentSuperClass))
      {
        return true;
      }
      if (currentClassInterfaces != null)
      {
        for (int i = 0; i < currentClassInterfaces.size(); ++i)
        {
          Type v = (Type) currentClassInterfaces.get(i);
          if (isAssignableFrom(t, v))
          {
            return true;
          }
        }
      }
      return false;
    }
    return _classInfoCache.hasSuperClass(u.getInternalName(), t.getInternalName());
  }

  @Override
  protected Class getClass(Type t)
  {
    throw new UnsupportedOperationException("Classes should be loaded via the class info cache");
  }
}
