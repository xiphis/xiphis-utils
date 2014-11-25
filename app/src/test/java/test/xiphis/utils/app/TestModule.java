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

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.xiphis.utils.app.Depends;
import org.xiphis.utils.app.Module;
import org.xiphis.utils.app.Registry;
import org.xiphis.utils.cli.CLIParser;
import org.xiphis.utils.cli.CLIShortName;

/**
 * @author atcurtis
 * @since 2014-11-20
 */
public class TestModule
{
  public class Retry implements TestRule
  {
    private int retryCount;

    public Retry(int retryCount) {
      this.retryCount = retryCount;
    }

    public Statement apply(Statement base, Description description) {
      return statement(base, description);
    }

    private Statement statement(final Statement base, final Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          Throwable caughtThrowable = null;

          // implement retry logic here
          for (int i = 0; i < retryCount; i++) {
            try {
              base.evaluate();
              return;
            } catch (Throwable t) {
              caughtThrowable = t;
              System.err.println(description.getDisplayName() + ": run " + (i+1) + " failed");
            }
          }
          System.err.println(description.getDisplayName() + ": giving up after " + retryCount + " failures");
          throw caughtThrowable;
        }
      };
    }
  }

  @Rule
  public Retry retry = new Retry(3);

  public static class ModFoo implements Module
  {
    @CLIShortName('v')
    public boolean verbose;

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

  @Depends(ModFoo.class)
  public static class ModFoo2 implements Module
  {
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


  @Depends(ModFoo2.class)
  public static class ModFoo3 implements Module
  {
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
  public void testModule1()
      throws InterruptedException
  {
    String[] args = {};
    Registry<CLIParser> registry = new Registry<>(new CLIParser(), new DefaultEventExecutorGroup(2));
    StringBuffer stateChanges = new StringBuffer();
    registry.setStateChangeListener((clazz, fromState, toState) -> {
      stateChanges.append(String.format("[%s] %s -> %s%n", clazz.getName(), fromState, toState));
    });
    registry.getConfig().register(ModFoo.class);
    args = registry.getConfig().parse(args);
    Assert.assertEquals("STATE    MODULE\n" + "======== ======\n",
                        registry.printModuleState(new StringBuilder()).toString());
    Future<?> initFuture = registry.init(ModFoo.class);
    Assert.assertEquals("STATE    MODULE           \n" +
                        "======== =================\n" +
                        "NEW      TestModule$ModFoo\n",
                        registry.printModuleState(new StringBuilder()).toString());
    initFuture.sync();
    Assert.assertEquals("STATE    MODULE           \n" +
                        "======== =================\n" +
                        "INITED   TestModule$ModFoo\n",
                        registry.printModuleState(new StringBuilder()).toString());
    Future<?> flushFuture = registry.flush(ModFoo.class);
    Assert.assertEquals("STATE    MODULE           \n" +
                        "======== =================\n" +
                        "FLUSH    TestModule$ModFoo\n",
                        registry.printModuleState(new StringBuilder()).toString());
    flushFuture.sync();
    Assert.assertEquals("STATE    MODULE           \n" +
                        "======== =================\n" +
                        "RUN      TestModule$ModFoo\n",
                        registry.printModuleState(new StringBuilder()).toString());
    Assert.assertEquals("[test.xiphis.utils.app.TestModule$ModFoo] UNINIT -> NEW\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] NEW -> IDLE\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] IDLE -> INIT\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] INIT -> INITED\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] INITED -> FLUSH\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] FLUSH -> RUN\n",
                        stateChanges.toString());

    registry.shutdown();
    registry.awaitTermination();
  }

  @Test
  public void testModule2()
      throws InterruptedException, ClassNotFoundException
  {
    String[] args = {};
    Registry<CLIParser> registry = new Registry<>(new CLIParser(), new DefaultEventExecutorGroup(2));
    StringBuffer stateChanges = new StringBuffer();
    registry.setStateChangeListener((clazz, fromState, toState) -> {
      stateChanges.append(String.format("[%s] %s -> %s%n", clazz.getName(), fromState, toState));
    });
    registry.getConfig().register(ModFoo2.class, Registry.RECURSE);
    Assert.assertEquals(Registry.getDependencies(ModFoo2.class).size(), 1);
    args = registry.getConfig().parse(args);
    Assert.assertEquals("STATE    MODULE\n" + "======== ======\n",
                        registry.printModuleState(new StringBuilder()).toString());
    Future<?> initFuture = registry.init(ModFoo2.class);
    Assert.assertEquals("STATE    MODULE            \n" +
                        "======== ==================\n" +
                        "UNINIT   TestModule$ModFoo \n" +
                        "NEW      TestModule$ModFoo2\n",
                        registry.printModuleState(new StringBuilder()).toString());
    initFuture.sync();
    Assert.assertEquals("STATE    MODULE            \n" +
                        "======== ==================\n" +
                        "IDLE     TestModule$ModFoo \n" +
                        "INITED   TestModule$ModFoo2\n",
                        registry.printModuleState(new StringBuilder()).toString());
    Future<?> flushFuture = registry.flush(ModFoo2.class);
    Assert.assertEquals("STATE    MODULE            \n" +
                        "======== ==================\n" +
                        "IDLE     TestModule$ModFoo \n" +
                        "FLUSH    TestModule$ModFoo2\n",
                        registry.printModuleState(new StringBuilder()).toString());
    flushFuture.sync();
    Assert.assertEquals("STATE    MODULE            \n" +
                        "======== ==================\n" +
                        "RUN      TestModule$ModFoo \n" +
                        "RUN      TestModule$ModFoo2\n",
                        registry.printModuleState(new StringBuilder()).toString());
    Assert.assertEquals("[test.xiphis.utils.app.TestModule$ModFoo2] UNINIT -> NEW\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] NEW -> IDLE\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] IDLE -> INIT\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] UNINIT -> NEW\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] NEW -> IDLE\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] INIT -> INITED\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] INITED -> FLUSH\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] IDLE -> INIT\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] INIT -> INITED\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] INITED -> FLUSH\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] FLUSH -> RUN\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] FLUSH -> RUN\n",
                        stateChanges.toString());
    registry.shutdown();
    registry.awaitTermination();
  }

  @Test
  public void testModule3()
      throws InterruptedException, ClassNotFoundException
  {
    String[] args = {"-v"};
    Registry<CLIParser> registry = new Registry<>(new CLIParser(), new DefaultEventExecutorGroup(2));
    StringBuffer stateChanges = new StringBuffer();
    registry.setStateChangeListener((clazz, fromState, toState) -> {
      stateChanges.append(String.format("[%s] %s -> %s%n", clazz.getName(), fromState, toState));
    });
    registry.getConfig().register(ModFoo3.class, Registry.RECURSE);
    Assert.assertEquals(Registry.getDependencies(ModFoo3.class).size(), 1);
    args = registry.getConfig().parse(args);
    Assert.assertEquals(0, args.length);
    Assert.assertEquals("STATE    MODULE\n" +
                        "======== ======\n",
                        registry.printModuleState(new StringBuilder()).toString());
    Future<?> initFuture = registry.init(ModFoo3.class);
    Assert.assertEquals("STATE    MODULE            \n" +
                        "======== ==================\n" +
                        "UNINIT   TestModule$ModFoo \n" +
                        "UNINIT   TestModule$ModFoo2\n" +
                        "NEW      TestModule$ModFoo3\n",
                        registry.printModuleState(new StringBuilder()).toString());
    initFuture.sync();
    Assert.assertEquals("STATE    MODULE            \n" +
                        "======== ==================\n" +
                        "UNINIT   TestModule$ModFoo \n" +
                        "IDLE     TestModule$ModFoo2\n" +
                        "INITED   TestModule$ModFoo3\n",
                        registry.printModuleState(new StringBuilder()).toString());
    Future<?> flushFuture = registry.flush(ModFoo3.class);
    Assert.assertEquals("STATE    MODULE            \n" +
                        "======== ==================\n" +
                        "UNINIT   TestModule$ModFoo \n" +
                        "IDLE     TestModule$ModFoo2\n" +
                        "FLUSH    TestModule$ModFoo3\n",
                        registry.printModuleState(new StringBuilder()).toString());
    flushFuture.sync();
    Assert.assertEquals("STATE    MODULE            \n" +
                        "======== ==================\n" +
                        "RUN      TestModule$ModFoo \n" +
                        "RUN      TestModule$ModFoo2\n" +
                        "RUN      TestModule$ModFoo3\n",
                        registry.printModuleState(new StringBuilder()).toString());
    Assert.assertTrue(registry.getModule(ModFoo.class).getNow().verbose);
    Future<?> stopFuture = registry.stop(ModFoo.class);
    Assert.assertEquals("STATE    MODULE            \n" +
                        "======== ==================\n" +
                        "STOPPING TestModule$ModFoo \n" +
                        "RUN      TestModule$ModFoo2\n" +
                        "RUN      TestModule$ModFoo3\n",
                        registry.printModuleState(new StringBuilder()).toString());
    stopFuture.sync();
    Assert.assertEquals("STATE    MODULE            \n" +
                        "======== ==================\n" +
                        "STOPPED  TestModule$ModFoo \n" +
                        "STOPPED  TestModule$ModFoo2\n" +
                        "STOPPED  TestModule$ModFoo3\n",
                        registry.printModuleState(new StringBuilder()).toString());

    Assert.assertEquals("[test.xiphis.utils.app.TestModule$ModFoo3] UNINIT -> NEW\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo3] NEW -> IDLE\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo3] IDLE -> INIT\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] UNINIT -> NEW\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] NEW -> IDLE\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo3] INIT -> INITED\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo3] INITED -> FLUSH\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] IDLE -> INIT\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] UNINIT -> NEW\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] NEW -> IDLE\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] INIT -> INITED\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] INITED -> FLUSH\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] IDLE -> INIT\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] INIT -> INITED\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] INITED -> FLUSH\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] FLUSH -> RUN\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] FLUSH -> RUN\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo3] FLUSH -> RUN\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] RUN -> STOPPING\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] RUN -> STOPPING\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo3] RUN -> STOPPING\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo3] STOPPING -> STOPPED\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo2] STOPPING -> STOPPED\n" +
                        "[test.xiphis.utils.app.TestModule$ModFoo] STOPPING -> STOPPED\n",
                        stateChanges.toString());
    registry.shutdown();
    registry.awaitTermination();
  }

}
