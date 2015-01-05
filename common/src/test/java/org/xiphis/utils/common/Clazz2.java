package org.xiphis.utils.common;

/**
 * @author atcurtis
 * @since 2015-01-02
 */
public class Clazz2
{
  final String _foo;
  private Clazz2(String foo)
  {
    _foo = foo;
  }

  public static Clazz2 parseClazz2(String foo)
  {
    return new Clazz2(foo);
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) return true;
    if (!(o instanceof Clazz2)) return false;

    Clazz2 clazz2 = (Clazz2) o;

    if (_foo != null ? !_foo.equals(clazz2._foo) : clazz2._foo != null) return false;

    return true;
  }

  @Override
  public int hashCode()
  {
    return _foo != null ? _foo.hashCode() : 0;
  }
}
