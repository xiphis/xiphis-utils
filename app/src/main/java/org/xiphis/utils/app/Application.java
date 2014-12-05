/*
 Copyright (c) 2014, Xiphis
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 Neither the name of the Xiphis nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.xiphis.utils.app;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.xiphis.utils.cli.CLIArgName;
import org.xiphis.utils.cli.CLIDescription;
import org.xiphis.utils.cli.CLILongName;
import org.xiphis.utils.cli.CLIMessageFormat;
import org.xiphis.utils.cli.CLIParser;
import org.xiphis.utils.cli.CLIShortName;
import org.xiphis.utils.cli.HelpFormatter;
import org.xiphis.utils.common.Charsets;
import org.xiphis.utils.common.Logger;
import org.xiphis.utils.common.ParserException;
import org.xiphis.utils.common.SystemExit;
import org.xiphis.utils.common.Utils;
import org.xiphis.utils.var.VarBase;
import org.xiphis.utils.var.VarConst;
import org.xiphis.utils.var.VarFunc;
import org.xiphis.utils.var.VarFuncNumber;
import org.xiphis.utils.var.VarGroup;
import org.xiphis.utils.var.VarItem;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

/**
 * @author atcurtis
 * @since 2014-11-20
 * @param <M> Type of the application module.
 */
public class Application<M extends MainModule>
{
  private final Logger LOG = Logger.getInstance(getClass());
  private final Registry<CLIParser> _registry;
  private final Class<M> _applicationModule;

  private String[] _appArgs = {};
  private final long _startTime = System.currentTimeMillis();
  private final Properties buildProperties = getBuildProperties();

  public Application(Class<M> applicationModule)
  {
    this(applicationModule, new DefaultEventExecutorGroup(Utils.PROCESSORS));
  }

  /**
   * Constructor
   * @param applicationModule Application module
   * @param executorGroup Executor for module state machine
   */
  public Application(Class<M> applicationModule, EventExecutorGroup executorGroup)
  {
    CLIParser cliParser = new CLIParser();
    cliParser.register(getClass(), Registry.RECURSE);

    _applicationModule = cliParser.register(applicationModule, Registry.RECURSE);
    _registry = new Registry<>(cliParser, executorGroup);

    buildStandardVars();
  }

  /**
   * @throws org.xiphis.utils.common.SystemExit
   */
  @CLIShortName('?')
  @CLILongName("help")
  @CLIDescription("Print this message and exit.")
  public void handleCLIHelp()
  {
    StringBuilder sb = new StringBuilder();
    HelpFormatter helpFormatter = new HelpFormatter(79, sb);
    _registry.getConfig().printHelp(helpFormatter);
    System.out.print(sb);
    System.out.flush();
    throw new SystemExit(0);
  }

  private BufferedReader openSource(String source)
      throws IOException
  {
    try
    {
      return new BufferedReader(new InputStreamReader(new URL(source).openStream(), Charsets.UTF_8));
    }
    catch (MalformedURLException e)
    {
      return new BufferedReader(new InputStreamReader(new FileInputStream(source), Charsets.UTF_8));
    }
  }

  @CLILongName("config")
  @CLIArgName("URL/FILE")
  @CLIDescription("Load config arguments from an alternate source")
  public void loadConfig(String source)
      throws IOException
  {
    try (BufferedReader reader = openSource(source))
    {
      ArrayList<String> args = new ArrayList<>();
      String line;
      StringBuilder arg = new StringBuilder();
      char quote = 0;
      while ((line = reader.readLine()) != null)
      {
        if (arg.length() == 0 && line.startsWith("#")) continue;
        boolean contin = false;
        for (int i = 0; i < line.length(); i++)
        {
          if (quote != 0 && line.charAt(i) == quote)
          {
            args.add(arg.toString());
            arg.setLength(0);
            quote = 0;
            continue;
          }
          if (quote == 0 && line.charAt(i) == '\'' || line.charAt(i) == '"')
          {
            quote = line.charAt(i);
            continue;
          }
          if (line.charAt(i) == '\\')
          {
            if (++i < line.length()) arg.append(line.charAt(i));
            else
            {
              arg.append('\n');
              contin = true;
            }
            continue;
          }
          if (quote == 0 && Character.isWhitespace(line.charAt(i)))
          {
            if (arg.length() > 0)
              args.add(arg.toString());
            arg.setLength(0);
            continue;
          }
          arg.append(line.charAt(i));
        }
        if (quote != 0)
          arg.append('\n');
        else
        if (!contin && arg.length() > 0)
        {
          args.add(arg.toString());
          arg.setLength(0);
        }
      }
      if (quote != 0 || arg.length() > 0)
        throw new IllegalStateException("Unexpected end of stream");
      _registry.getConfig().parse(args.toArray(new String[args.size()]), foo -> {
        throw new IllegalStateException("Unknown argument: [" + foo + "]");
      });
    }
  }

  /**
   * @throws org.xiphis.utils.common.SystemExit
   */
  @CLILongName("version")
  @CLIDescription("Print the version number and exit.")
  public void handleCLIVersion()
  {
    System.out.print(String.format("%s version: %s built by %s on %s%n",
                                   _applicationModule.getName(),
                                   buildProperties.getProperty("build.version","unknown"),
                                   buildProperties.getProperty("build.user","unknown"),
                                   buildProperties.getProperty("build.time","unknown")));
    System.out.flush();
    throw new SystemExit(0);
  }

  /**
   *
   * @param varName
   * @param value
   */
  @CLIShortName('D')
  @CLILongName("set-var")
  @CLIMessageFormat("{0}:{1}")
  @CLIArgName("VAR:ARG")
  @CLIDescription("Sets a variable")
  public void define(String varName, String value)
  {
    VarGroup.setStringValue(varName, value);
  }

  /**
   * Module registry for this application
   * @return registry
   */
  public Registry getRegistry()
  {
    return _registry;
  }

  /**
   * Declare a few common application vars.
   */
  protected void buildStandardVars()
  {
    VarConst.builder("build.version", buildProperties.getProperty("build.version","unknown")).build();
    VarConst.builder("build.time",    buildProperties.getProperty("build.time", "unknown")).build();
    VarConst.builder("build.user",    buildProperties.getProperty("build.user", "unknown")).build();

    VarFunc.builder("app.args", () -> Arrays.deepToString(_appArgs)).build();
    VarFuncNumber.builder("app.uptimeInMillis", () -> System.currentTimeMillis() - _startTime).build();

    VarFunc.builder("os.arch", () -> System.getProperty("os.arch")).build();
    VarFunc.builder("os.name", () -> System.getProperty("os.name")).build();
    VarFunc.builder("os.version", () -> System.getProperty("os.version")).build();

    VarFunc.builder("java.vendor", () -> System.getProperty("java.vendor")).build();
    VarFunc.builder("java.version", () -> System.getProperty("java.version")).build();

    VarFunc.builder("java.class.path", () -> System.getProperty("java.class.path")).build();
  }

  /**
   * Retrieves and parses a found {@code /build.properties} resource, if found.
   * @return properties
   */
  private static Properties getBuildProperties()
  {
    Properties prop = new Properties();
    try
    {
      InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("build.properties");
      prop.load(is);
      is.close();
    }
    catch (Throwable ignore) { }
    return prop;
  }

  /**
   * Parse command line arguments
   * @param args args to parse
   * @return remaining args
   */
  public static String[] parseVarArgs(String[] args)
  {
    ArrayList<String> mainArgs = new ArrayList<>(args.length);
    for (String arg : args)
    {
      int eqPos;
      if (arg.startsWith("+") && (eqPos = arg.indexOf('=')) > 0)
      {
        VarItem item = VarGroup.ROOT.findItem(arg.substring(1, eqPos));
        if (item != null)
        {
          VarBase<?, ?> var = item.asVarBase();
          var.setStringValue(arg.substring(eqPos + 1));
          continue;
        }
        else
        {
          throw new IllegalArgumentException(String.format("Invalid argument: %s", arg.substring(1, eqPos)));
        }
      }
      mainArgs.add(arg);
    }
    return mainArgs.toArray(new String[mainArgs.size()]);
  }

  /**
   * Return the application module instance
   * @return application module instance
   */
  protected M getApplicationModule()
  {
    return _registry.getModule(_applicationModule).syncUninterruptibly().getNow();
  }

  /**
   *
   */
  protected void initApplicationModule()
  {
    LOG.info("Initializing application module");
    _registry.init(_applicationModule).syncUninterruptibly();
  }

  /**
   * Main method implementation.
   *
   * <p>Typical usage:
   * <pre>
   *   public static void main(String[] args)
   *   {
   *     System.exit(new Application&lt;&gt;(MyApplicationModule.class).main(args));
   *   }
   * </pre>
   * @param args
   * @return
   */
  public int main(String[] args)
  {
    int rc;
    try
    {

      LOG.info(String.format("main(%s)", Arrays.deepToString(_appArgs = args)));

      M application;
      try
      {
        args = _registry.getConfig().parse(args);

        _registry.getConfig().configure(this);

        application = getApplicationModule();
      }
      catch (ParserException ex)
      {
        if (ex.getCause() instanceof SystemExit)
          return ((SystemExit) ex.getCause()).getExitCode();
        if (ex.getCause() instanceof RuntimeException)
          throw (RuntimeException) ex.getCause();
        if (ex.getCause() instanceof Error)
          throw (Error) ex.getCause();
        throw ex;
      }

      initApplicationModule();

      // all the Modules should now be instantiated, therefore, all the Var instances
      // should also be instantiated. We can then process all the Var arg settings
      // from the command line and apply them.

      args = parseVarArgs(args);

      flush();

      LOG.info("Executing application main");
      rc = application.main(this, args);
      LOG.info(String.format("Application main returned rc=%d", rc));
    }
    finally
    {
      _registry.shutdown();
    }

    try
    {
      LOG.info("Waiting for shutdown to complete");
      _registry.awaitTermination();
    }
    catch (InterruptedException e)
    {
      LOG.warn("Shutdown interrupted", e);
    }
    return rc;
  }

  /**
   * Initiates a flush of the application module and waits for it to complete.
   */
  public void flush()
  {
    LOG.info("Flushing application module");
    _registry.flush(_applicationModule).syncUninterruptibly();
  }
}
