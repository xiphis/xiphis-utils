/*
 Copyright (c) 2014, Xiphis
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 Neither the name of the Xiphis nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package test.xiphis.utils.app;

import io.netty.util.concurrent.Promise;
import org.junit.Assert;
import org.junit.Test;
import org.xiphis.utils.app.Application;
import org.xiphis.utils.app.MainModule;
import org.xiphis.utils.cli.CLIDescription;
import org.xiphis.utils.cli.CLILongName;
import org.xiphis.utils.cli.CLIShortName;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * @author atcurtis
 * @since 2014-11-25
 */
public class TestApplication
{
  public static class AppModule0 implements MainModule
  {
    @CLIShortName('v')
    @CLILongName("verbose")
    @CLIDescription("Increase verbosity")
    public boolean verbose;

    @Override
    public int main(Application<?> application, String[] args)
    {
      return -1;
    }

    @Override
    public void init(Promise<Void> promise)
    {
      promise.setFailure(new Exception());
    }

    @Override
    public void pause(Promise<Void> promise)
    {
      promise.setFailure(new Exception());
    }

    @Override
    public void flush(Promise<Void> promise)
    {
      promise.setFailure(new Exception());
    }

    @Override
    public void stop(Promise<Void> promise)
    {
      promise.setFailure(new Exception());
    }
  }

  @Test
  public void testHelp() throws InterruptedException
  {
    PrintStream savedOut = System.out;
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bos));
    try
    {
      String[] args = {"--help"};
      int rc = new Application<>(AppModule0.class).main(args);
      Assert.assertEquals(0, rc);
    }
    finally
    {
      System.setOut(savedOut);
    }
    Assert.assertEquals("Options:\n" +
                        "  -?, --help                  Print this message and exit.\n" +
                        "  -D VAR:ARG, --set-var=VAR:ARG\n" +
                        "                              Sets a variable\n" +
                        "  --config=URL/FILE           Load config arguments from an alternate source\n" +
                        "  -v, --verbose               Increase verbosity\n" +
                        "  --version                   Print the version number and exit.\n",
                        new String(bos.toByteArray(), Charset.defaultCharset()));
  }

  @Test
  public void testVersion()
  {
    PrintStream savedOut = System.out;
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bos));
    try
    {
      String[] args = {"--version"};
      int rc = new Application<>(AppModule0.class).main(args);
      Assert.assertEquals(0, rc);
    }
    finally
    {
      System.setOut(savedOut);
    }
    Assert.assertEquals("test.xiphis.utils.app.TestApplication$AppModule0 version: 1.0-SNAPSHOT built by someone on 2014-01-01T00:00:00+00:00\n",
                        new String(bos.toByteArray(), Charset.defaultCharset()));
  }

  public static class AppModule1 implements MainModule
  {
    @CLILongName("hello")
    public String hello;

    @CLILongName("this")
    public String string;


    @Override
    public int main(Application<?> application, String[] args)
    {
      Assert.assertEquals("world",hello);
      Assert.assertEquals("is a\n" + "test",string);
      return 42;
    }

    @Override
    public void init(Promise<Void> promise)
    {
      promise.setSuccess(null);
    }

    @Override
    public void pause(Promise<Void> promise)
    {
      promise.setSuccess(null);
    }

    @Override
    public void flush(Promise<Void> promise)
    {
      promise.setSuccess(null);
    }

    @Override
    public void stop(Promise<Void> promise)
    {
      promise.setSuccess(null);
    }
  }

  @Test
  public void testConfig()
  {
    URL config = Thread.currentThread().getContextClassLoader().getResource("extra.config");
    String[] args = {"--config", config.toString()};
    int rc = new Application<>(AppModule1.class).main(args);
    Assert.assertEquals(42, rc);
  }


}
