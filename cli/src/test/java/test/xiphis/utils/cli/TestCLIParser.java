/*
 Copyright (c) 2014, Xiphis
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 Neither the name of the Xiphis nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package test.xiphis.utils.cli;

import org.junit.Assert;
import org.junit.Test;
import org.xiphis.utils.common.BadArgumentException;
import org.xiphis.utils.cli.CLIConfigure;
import org.xiphis.utils.cli.CLIDefault;
import org.xiphis.utils.cli.CLIDescription;
import org.xiphis.utils.cli.CLIFooter;
import org.xiphis.utils.cli.CLIHeader;
import org.xiphis.utils.cli.CLILongName;
import org.xiphis.utils.cli.CLIMessageFormat;
import org.xiphis.utils.cli.CLINoParameter;
import org.xiphis.utils.cli.CLIParser;
import org.xiphis.utils.cli.CLIShortName;
import org.xiphis.utils.cli.HelpFormatter;
import org.xiphis.utils.common.Setable;

import java.io.File;

/**
 * @author atcurtis
 * @since 2014-11-16
 */
public class TestCLIParser
{
  @Test
  public void testBasic()
  {
    CLIParser parser = new CLIParser();
    String[] args = { "hello", "world"};
    String[] res = parser.parse(args);
    Assert.assertArrayEquals(args, res);
  }

  @Test(expected = BadArgumentException.class)
  public void testBasicFail()
  {
    try
    {
      CLIParser parser = new CLIParser();
      String[] args = { "hello", "world", "--foo"};
      String[] res = parser.parse(args);
      Assert.fail();
    }
    catch (BadArgumentException e)
    {
      Assert.assertEquals("Unknown option: foo", e.getMessage());
      throw e;
    }
  }

  @CLIHeader("Simple header.")
  @CLIFooter("Simple footer.")
  public class Foo1Class
  {
    @CLIShortName('a')
    @CLILongName("foo")
    @CLIDescription("This is a simple description.")
    public String arg1;

    @CLILongName("bar")
    @CLIShortName('b')
    @CLIDescription("Not much to see here.")
    public String arg2;

    @CLILongName("ite")
    @CLIShortName('c')
    @CLIDescription("Blah!")
    public int arg3;
  }

  @Test
  public void testFieldArgs1()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo1Class.class);
    String[] args = { "-a", "hello", "-b", "world", "-c", "42"};
    String[] res = parser.parse(args);
    Assert.assertEquals(0, res.length);

    Foo1Class foo = new Foo1Class();

    Assert.assertNull(foo.arg1);
    Assert.assertNull(foo.arg2);
    Assert.assertEquals(0, foo.arg3);

    parser.configure(foo);

    Assert.assertEquals("hello", foo.arg1);
    Assert.assertEquals("world", foo.arg2);
    Assert.assertEquals(42, foo.arg3);
  }

  @Test
  public void testFieldArgs1b()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo1Class.class);
    String[] args = { "-abc", "hello", "world", "42"};
    String[] res = parser.parse(args);
    Assert.assertEquals(0, res.length);

    Foo1Class foo = new Foo1Class();

    Assert.assertNull(foo.arg1);
    Assert.assertNull(foo.arg2);
    Assert.assertEquals(0, foo.arg3);

    parser.configure(foo);

    Assert.assertEquals("hello", foo.arg1);
    Assert.assertEquals("world", foo.arg2);
    Assert.assertEquals(42, foo.arg3);
  }

  @Test
  public void testFieldArgs2()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo1Class.class);
    String[] args = { "--foo=hello", "--bar=world", "--ite=42"};
    String[] res = parser.parse(args);
    Assert.assertEquals(0, res.length);

    Foo1Class foo = parser.configure(new Foo1Class());

    Assert.assertEquals("hello", foo.arg1);
    Assert.assertEquals("world", foo.arg2);
    Assert.assertEquals(42, foo.arg3);
  }

  @Test
  public void testFieldArgs2a()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo1Class.class);
    String[] args = { "--foo", "hello", "--bar", "world"};
    String[] res = parser.parse(args);
    Assert.assertEquals(0, res.length);

    Foo1Class foo = parser.configure(new Foo1Class());

    Assert.assertEquals("hello", foo.arg1);
    Assert.assertEquals("world", foo.arg2);
  }

  @Test
  public void testFieldArgs2b()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo1Class.class);
    String[] args = { "--foo", "--bar", "hello", "world"};
    String[] res = parser.parse(args);
    Assert.assertEquals(0, res.length);

    Foo1Class foo = parser.configure(new Foo1Class());

    Assert.assertEquals("hello", foo.arg1);
    Assert.assertEquals("world", foo.arg2);
  }

  @Test
  public void testHelpFoo1Class()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo1Class.class);
    StringBuilder builder = new StringBuilder();
    parser.printHelp(new HelpFormatter(80, builder));
    Assert.assertEquals("Simple header.\n" +
                        "Options:\n" +
                        "  -a STRING, --foo=STRING     This is a simple description.\n" +
                        "  -b STRING, --bar=STRING     Not much to see here.\n" +
                        "  -c INT, --ite=INT           Blah!\n" +
                        "Simple footer.\n",
                        builder.toString());
  }

  @CLIHeader("Simple header.")
  @CLIFooter("Simple footer.")
  public class Foo2Class
  {
    @CLIShortName('a')
    @CLILongName("foo")
    @CLIDescription("This is a simple description.")
    public File arg1;

    @CLILongName("bar")
    @CLIShortName('b')
    @CLIDescription("Not much to see here.")
    public String arg2;
  }

  @Test
  public void testHelpFoo2Class()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo2Class.class);
    StringBuilder builder = new StringBuilder();
    parser.printHelp(new HelpFormatter(80, builder));
    Assert.assertEquals("Simple header.\n" +
                        "Options:\n" +
                        "  -a FILE, --foo=FILE         This is a simple description.\n" +
                        "  -b STRING, --bar=STRING     Not much to see here.\n" +
                        "Simple footer.\n",
                        builder.toString());
  }

  @Test
  public void testFieldArgs3()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo2Class.class);
    String[] args = { "--foo=/bin/sh", "--bar=world"};
    String[] res = parser.parse(args);
    Assert.assertEquals(0, res.length);

    Foo2Class foo = parser.configure(new Foo2Class());

    Assert.assertEquals("/bin/sh", foo.arg1.getAbsolutePath());
    Assert.assertEquals("world", foo.arg2);
  }

  @CLIHeader("Simple header.")
  @CLIFooter("Simple footer.")
  public class Foo3Class
  {
    private File arg1;
    private String arg2;
    private boolean setArg1Called;
    private boolean setArg2Called;

    @CLIShortName('a')
    @CLILongName("foo")
    @CLIDescription("This is a simple description.")
    public void setArg1(File arg1)
    {
      this.arg1 = arg1;
      setArg1Called = true;
    }

    @CLILongName("bar")
    @CLIShortName('b')
    @CLIDescription("Not much to see here.")
    public void setArg2(String arg2)
    {
      this.arg2 = arg2;
      setArg2Called = true;
    }
  }

  @Test
  public void testHelpFoo3Class()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo3Class.class);
    StringBuilder builder = new StringBuilder();
    parser.printHelp(new HelpFormatter(80, builder));
    Assert.assertEquals("Simple header.\n" +
                        "Options:\n" +
                        "  -a FILE, --foo=FILE         This is a simple description.\n" +
                        "  -b STRING, --bar=STRING     Not much to see here.\n" +
                        "Simple footer.\n",
                        builder.toString());
  }

  @Test
  public void testMethodArgs1()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo3Class.class);
    String[] args = { "--foo=/bin/sh", "--bar=world"};
    String[] res = parser.parse(args);
    Assert.assertEquals(0, res.length);

    Foo3Class foo = parser.configure(new Foo3Class());

    Assert.assertEquals("/bin/sh", foo.arg1.getAbsolutePath());
    Assert.assertEquals("world", foo.arg2);
    Assert.assertTrue(foo.setArg1Called);
    Assert.assertTrue(foo.setArg2Called);
  }

  public class Foo4Class
  {
    private String arg1;
    private String arg2;
    private long arg3;

    @CLIShortName('a')
    @CLILongName("foo")
    @CLIDescription("Arg in format s1:s2:n3.")
    @CLIMessageFormat("{0}:{1}:{2,number}")
    public void setArg(String arg1, String arg2, long arg3)
    {
      this.arg1 = arg1;
      this.arg2 = arg2;
      this.arg3 = arg3;
    }
  }

  @Test
  public void testMethodFormatArgs1()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo4Class.class);
    String[] args = { "--foo=hello:world:123" };
    String[] res = parser.parse(args);
    Assert.assertEquals(0, res.length);

    Foo4Class foo = parser.configure(new Foo4Class());

    Assert.assertEquals("hello", foo.arg1);
    Assert.assertEquals("world", foo.arg2);
    Assert.assertEquals(123L, foo.arg3);
  }

  public enum TestEnum
  {
    FOO,
    BAR,
    ITE
  }

  public class Foo5Class
  {
    @CLIShortName('a')
    @CLILongName("foo")
    @CLIDescription("This is a simple description.")
    public TestEnum arg1;
  }

  @Test
  public void testFieldArgs4()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo5Class.class);

    for (TestEnum t : TestEnum.values())
    {
      parser.reset();

      String[] args = { "--foo=" + t.name() };
      String[] res = parser.parse(args);
      Assert.assertEquals(0, res.length);

      Foo5Class foo = parser.configure(new Foo5Class());

      Assert.assertSame(t, foo.arg1);
    }
  }

  public class Foo6Class
  {
    @CLIConfigure
    public Foo5Class foo5Class = new Foo5Class();
  }

  @Test
  public void testFieldArgs5()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo6Class.class);

    for (TestEnum t : TestEnum.values())
    {
      parser.reset();

      String[] args = { "--foo=" + t.name() };
      String[] res = parser.parse(args);
      Assert.assertEquals(0, res.length);

      Foo6Class foo = parser.configure(new Foo6Class());

      Assert.assertSame(t, foo.foo5Class.arg1);
    }
  }

  public class Foo7Class
  {
    @CLIShortName('a')
    @CLILongName("foo")
    @CLIDescription("This is a simple description.")
    public String[] arg1;
  }

  @Test
  public void testFieldArgs6()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo7Class.class);

    String[] args = { "--foo=Hello,World" };
    String[] res = parser.parse(args);
    Assert.assertEquals(0, res.length);

    Foo7Class foo = parser.configure(new Foo7Class());

    Assert.assertSame(2, foo.arg1.length);
    Assert.assertEquals("Hello", foo.arg1[0]);
    Assert.assertEquals("World", foo.arg1[1]);
  }


  @Test
  public void testHelpFoo7Class()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo7Class.class);
    StringBuilder builder = new StringBuilder();
    parser.printHelp(new HelpFormatter(80, builder));
    Assert.assertEquals("Options:\n" +
                        "  -a STRING[], --foo=STRING[]\n" +
                        "                              This is a simple description.\n",
                        builder.toString());
  }

  public class Bar1Class implements Setable<Integer>
  {
    int value;

    @Override
    public void setValue(Integer value)
    {
      this.value = value;
    }
  }

  public class Foo8Class
  {
    @CLIShortName('a')
    @CLILongName("foo")
    @CLIDescription("This is a simple description.")
    public final Bar1Class arg1 = new Bar1Class();
  }

  @Test
  public void testFieldArgs7()
  {
    CLIParser parser = new CLIParser();
    parser.register(Foo8Class.class);

    String[] args = { "--foo=42" };
    String[] res = parser.parse(args);
    Assert.assertEquals(0, res.length);

    Foo8Class foo = parser.configure(new Foo8Class());

    Assert.assertEquals(42, foo.arg1.value);
  }

  public class FooBoolean1
  {
    @CLIShortName('v')
    public boolean verbose;
  }

  @Test
  public void testBooleanField()
  {
    CLIParser parser = new CLIParser();
    parser.register(FooBoolean1.class);

    String[] args = { "-v", "moo" };
    String[] res = parser.parse(args);
    Assert.assertEquals(1, res.length);

    FooBoolean1 foo = parser.configure(new FooBoolean1());
    Assert.assertTrue(foo.verbose);
  }

  public class FooBoolean2
  {
    @CLIShortName('v')
    @CLINoParameter
    public int verbose;
  }

  @Test
  public void testBooleanField2()
  {
    CLIParser parser = new CLIParser();
    parser.register(FooBoolean2.class);

    String[] args = { "-vvv", "moo" };
    String[] res = parser.parse(args);
    Assert.assertEquals(1, res.length);

    FooBoolean2 foo = parser.configure(new FooBoolean2());
    Assert.assertEquals(foo.verbose, 3);
  }

  public class FooBoolean3
  {
    @CLIShortName('v')
    @CLINoParameter
    @CLIDefault("Verbose")
    public String verbose;
  }

  @Test
  public void testBooleanField3()
  {
    CLIParser parser = new CLIParser();
    parser.register(FooBoolean3.class);

    String[] args = { "-vvv", "moo" };
    String[] res = parser.parse(args);
    Assert.assertEquals(1, res.length);

    FooBoolean3 foo = parser.configure(new FooBoolean3());
    Assert.assertEquals(foo.verbose, "Verbose");
  }

  public class FooBoolean4
  {
    int verbose;

    @CLIShortName('v')
    public void setVerbose()
    {
      verbose++;
    }
  }

  public class FooBoolean5
  {
    int verbose;

    @CLIShortName('v')
    @CLINoParameter
    @CLIDefault("Yay!")
    public void setVerbose(String test)
    {
      if (test.equals("Yay!"))
        verbose++;
    }
  }

  @Test
  public void testBooleanField5()
  {
    CLIParser parser = new CLIParser();
    parser.register(FooBoolean5.class);

    String[] args = { "-vvv", "moo" };
    String[] res = parser.parse(args);
    Assert.assertEquals(1, res.length);

    FooBoolean5 foo = parser.configure(new FooBoolean5());
    Assert.assertEquals(foo.verbose, 3);
  }
}

