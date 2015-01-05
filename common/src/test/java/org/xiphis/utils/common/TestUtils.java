package org.xiphis.utils.common;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * @author atcurtis
 * @since 2014-12-27
 */
public class TestUtils
{
  static class TestVector<T>
  {
    final Class<T> clazz;
    final String text;
    final T value;

    TestVector(Class<T> clazz, String text, T value)
    {
      this.clazz = clazz;
      this.text = text;
      this.value = value;
    }
  }

  static <T> TestVector<T> test(Class<T> clazz, String text, T value)
  {
    return new TestVector<>(clazz, text, value);
  }

  static <T> TestVector<T> test(String text, T value)
  {
    return new TestVector<>((Class<T>) value.getClass(), text, value);
  }

  enum Foo {
    TEST1, TEST2
  }

  enum Bar {
    TEST1, TEST2 {
      public String toString()
      {
        return "TEST3";
      }
    }
  }

  public static class Clazz1
  {
    final String _foo;
    public Clazz1(String foo)
    {
      _foo = foo;
    }

    @Override
    public boolean equals(Object o)
    {
      if (this == o) return true;
      if (!(o instanceof Clazz1)) return false;

      Clazz1 clazz1 = (Clazz1) o;

      if (_foo != null ? !_foo.equals(clazz1._foo) : clazz1._foo != null) return false;

      return true;
    }

    @Override
    public int hashCode()
    {
      return _foo != null ? _foo.hashCode() : 0;
    }
  }

  @Test
  public void testParseString()
  {

    ArrayList<TestVector<?>> testVector = new ArrayList<>();
    testVector.add(test(String.class, "1", "1"));
    testVector.add(test(Boolean.class, "true", Boolean.TRUE));
    testVector.add(test(Boolean.TYPE, "true", Boolean.TRUE));
    testVector.add(test(Byte.class, "1", (byte) 1));
    testVector.add(test(Byte.TYPE, "1", (byte) 1));
    testVector.add(test(Short.class, "1", (short) 1));
    testVector.add(test(Short.TYPE, "1", (short) 1));
    testVector.add(test(Integer.class, "1", 1));
    testVector.add(test(Integer.TYPE, "1", 1));
    testVector.add(test(Long.class, "1", 1L));
    testVector.add(test(Long.TYPE, "1", 1L));
    testVector.add(test(Character.TYPE, "1", '1'));
    testVector.add(test(Double.class, "1.0", 1.0));
    testVector.add(test(Double.TYPE, "1.0", 1.0));
    testVector.add(test(Float.class, "1.0", 1.0f));
    testVector.add(test(Float.TYPE, "1.0", 1.0f));
    testVector.add(test(Foo.class, "TEST1", Foo.TEST1));
    testVector.add(test(Foo.class, "TEST2", Foo.TEST2));

    testVector.add(test(Bar.class, "TEST1", Bar.TEST1));
    testVector.add(test(Bar.class, "TEST2", Bar.TEST2));

    testVector.add(test("1,2,3", new int[] { 1, 2, 3 }));
    testVector.add(test("1,2,3", new Integer[] { 1, 2, 3 }));
    testVector.add(test("TEST1,TEST2,TEST1", new Foo[] { Foo.TEST1, Foo.TEST2, Foo.TEST1 }));

    testVector.add(test("(1,2,3),{2,3,4},[3,4,5]", new int[][] {
        { 1, 2, 3 },
        { 2, 3, 4 },
        { 3, 4, 5 }
    }));

    testVector.add(test(Clazz1.class, "Hello World", new Clazz1("Hello World")));

    testVector.add(test(Clazz2.class, "Hello World", Clazz2.parseClazz2("Hello World")));

    for (TestVector<?> test : testVector)
    {
      Object value = Utils.parseString(test.clazz, test.text);
      Assert.assertTrue(test.clazz.isPrimitive() || test.clazz.isAssignableFrom(value.getClass()));
      if (value.getClass().isArray())
      {
        if (value.getClass().getComponentType().isPrimitive())
        {
          if (value.getClass().getComponentType() == Integer.TYPE)
          {
            Assert.assertArrayEquals((int[]) test.value, (int[]) value);
          }
          else
          {
            Assert.fail();
          }
        }
        else
        {
          Assert.assertArrayEquals((Object[]) test.value, (Object[]) value);
        }
      }
      else
      {
        Assert.assertEquals(test.value, value);
      }
    }
  }

  @Test
  public void testBuildVersion()
  {
    Assert.assertEquals("test_version", Utils.getBuildVersion());
  }

  @Test
  public void testBuildUser()
  {
    Assert.assertEquals("test_user", Utils.getBuildUser());
  }

  @Test
  public void testBuildTime()
  {
    Assert.assertEquals(LocalDateTime.of(2015,1,1,1,1,59), Utils.getBuildTime());
  }

  @Test
  public void testBuildGitCommit()
  {
    Assert.assertEquals("1c68ea370b40c06fcaf7f26c8b1dba9d9caf5dea", Utils.getBuildGitCommit());
  }
}
