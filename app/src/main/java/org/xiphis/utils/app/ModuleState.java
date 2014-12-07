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

/**
 * @author atcurtis
 * @since 2014-11-19
 */
public enum ModuleState
{
  /**
   * Module is shutting down and will soon have its {@link org.xiphis.utils.app.Module#stop(io.netty.util.concurrent.Promise)}
   * method invoked.
   */
  STOPPING,

  /**
   * Module has completed shutting down.
   */
  STOPPED,

  /**
   * An error which has occurred during a state transition has moved the module into the FAILED state.
   */
  FAILED,

  /**
   * The module class has been registered but no instance has yet to be constructed.
   */
  UNINIT,

  /**
   * The module class implementation will soon be constructed.
   */
  NEW,

  /**
   * The module class implementation has been constructed successfully.
   */
  IDLE,

  /**
   * Module will soon have its {@link org.xiphis.utils.app.Module#init(io.netty.util.concurrent.Promise)} method
   * invoked where it will enter the {@code INITED} state when it has completed successfully.
   */
  INIT,

  /**
   * Module has completed its initialisation after the {@link org.xiphis.utils.app.Module#init(io.netty.util.concurrent.Promise)}
   * method has completed successfully.
   */
  INITED,

  /**
   * Module will soon have its {@link org.xiphis.utils.app.Module#flush(io.netty.util.concurrent.Promise)} method
   * invoked where it will enter the {@code RUN} state when it has completed successfully.
   */
  FLUSH,

  /**
   * Module is running after the {@link org.xiphis.utils.app.Module#flush(io.netty.util.concurrent.Promise)}
   * has completed successfully.
   */
  RUN,

  /**
   * Module is entering quiescence in preparation to be flushed again. The module will proceed into the {@code INITED}
   * state after the {@link org.xiphis.utils.app.Module#pause(io.netty.util.concurrent.Promise)} method completes.
   * }
   */
  PAUSE,
}
