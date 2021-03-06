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

import io.netty.util.concurrent.Promise;
import org.xiphis.utils.common.ConcurrentIdentityHashMap;
import org.xiphis.utils.common.Logger;

import java.util.IdentityHashMap;
import java.util.NoSuchElementException;

/**
 * @author atcurtis
 * @since 2014-11-18
 */
public abstract class AbstractModule implements Module
{
  protected final Logger LOG = Logger.getInstance(getClass());
  private final Registry<?> _registry;
  private final ConcurrentIdentityHashMap<Class<? extends Module>, Module> _depends;

  /**
   *
   * @param registry registry
   */
  protected AbstractModule(Registry<?> registry)
  {
    if (registry == null)
      throw new NullPointerException();
    _registry = registry;
    _depends = new ConcurrentIdentityHashMap<>();
  }

  /**
   * Returns the registry which constructed the module.
   * @return registry
   */
  public final Registry<?> getRegistry()
  {
    return _registry;
  }

  /**
   * Returns the state of the module.
   * @return state
   */
  public final ModuleState getState()
  {
    return _registry.getModuleInfo(this).getModuleState();
  }

  /**
   * Returns an instance of the requested module.
   * @param moduleClass Class or interface of module
   * @param <M> Type of module
   * @return module instance
   * @throws java.lang.ClassNotFoundException thrown if the requested class is not a dependency of this module.
   */
  public final <M extends Module> M getModule(Class<M> moduleClass)
      throws ClassNotFoundException
  {
    M module;
    if ((module = moduleClass.cast(_depends.get(moduleClass))) != null)
      return module;
    for (Class<? extends Module> depend : Registry.getDependencies(moduleClass))
    {
      if (depend == moduleClass)
      {
        module = _registry.getModule(moduleClass).syncUninterruptibly().getNow();
        Module existing = _depends.putIfAbsent(moduleClass, module);
        assert existing == null || existing == module;
        return module;
      }
    }
    LOG.error("Module {} is not a dependency", moduleClass.getName());
    throw new NoSuchElementException();
  }
}
