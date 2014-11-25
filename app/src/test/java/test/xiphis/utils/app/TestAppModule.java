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
import org.xiphis.utils.app.Application;
import org.xiphis.utils.app.MainModule;

/**
 * @author atcurtis
 * @since 2014-11-25
 */
public class TestAppModule implements MainModule
{
  public static void main(String[] args)
  {
    System.exit(new Application<>(TestAppModule.class).main(args));
  }

  @Override
  public int main(Application<?> application, String[] args)
  {
    return 0;
  }

  /**
   * The first method called after the module is instantiated. All dependencies
   * will have been instantiated but init may not have been called upon them.
   *
   * @param promise callback upon completion.
   */
  @Override
  public void init(Promise<Void> promise)
  {
    promise.setSuccess(null);
  }

  /**
   * Called before {@link #flush(io.netty.util.concurrent.Promise)}. If the module is active,
   * it should suspend operations until after {@linkplain #flush(io.netty.util.concurrent.Promise)}
   * has completed.
   *
   * @param promise callback upon completion.
   */
  @Override
  public void pause(Promise<Void> promise)
  {
    promise.setSuccess(null);
  }

  /**
   * The module should flush config and reinitialize using current config. Upon completion of
   * this call, the module is expected to be in a running state. All dependencies should have
   * completed flushing before invocation.
   *
   * @param promise callback upon completion.
   */
  @Override
  public void flush(Promise<Void> promise)
  {
    promise.setSuccess(null);
  }

  /**
   * The module should initiate an orderly shutdown in preparation for termination. All modules
   * which depend on this module should have completed stopping.
   *
   * @param promise callback upon completion.
   */
  @Override
  public void stop(Promise<Void> promise)
  {
    promise.setSuccess(null);
  }
}
