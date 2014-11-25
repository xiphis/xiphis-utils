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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiphis.utils.common.ConcurrentIdentityHashMap;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;

/**
 * @author atcurtis
 * @since 2014-11-20
 */
public class ModuleInfo<M extends Module>
{
  private final static Runnable NOP = () -> { };
  private final Logger LOG = LoggerFactory.getLogger(getClass());

  private final Class<M> _moduleClass;
  private final Class<? extends M> _implementationClass;
  private final AtomicReference<ModuleState> _moduleState;
  private final Set<ModuleInfo<? extends Module>> _dependants;
  private final Promise<M> _moduleInstance;
  private Future<M> _moduleInstanceFuture;

  ModuleInfo(Class<M> moduleClass, Registry<?> registry)
      throws ClassNotFoundException
  {
    LOG.info(format("ModuleInfo<%s>", moduleClass.getName()));
    _moduleClass = moduleClass;
    _implementationClass = Registry.getImplementationClass(moduleClass);
    _moduleInstance = registry.newPromise();
    _moduleInstanceFuture = _moduleInstance;
    _moduleState = new AtomicReference<>(ModuleState.UNINIT);
    _dependants = Collections.newSetFromMap(new ConcurrentIdentityHashMap<>());
  }

  private boolean compareAndSet(Registry<?> registry, ModuleState expect, ModuleState value, Runnable complete)
  {
    if (registry == null)
      throw new NullPointerException();

    if (expect.ordinal() != value.ordinal() &&
        _moduleState.compareAndSet(expect, value))
    {
      try
      {
        registry.stateChanged(_moduleClass, expect, value);
      }
      finally
      {
        switch (value)
        {
        case NEW:
          handleNew(registry, complete);
          break;
        case INIT:
          handleInit(registry, complete);
          break;
        case FLUSH:
          handleFlush(registry, complete);
          break;
        case PAUSE:
          handlePause(registry, complete);
          break;
        case STOPPING:
          handleStop(registry, complete);
          break;
        default:
          complete.run();
        }
      }
      return true;
    }
    return false;
  }

  private void handleNew(Registry<?> registry, Runnable complete)
  {
    registry.submit(() -> {
      LOG.info(format("[%s] Constructing class %s", getModuleName(), _implementationClass.getName()));
      try
      {
        Constructor<? extends M> constructor = _implementationClass.getConstructor(Registry.class);
        return registry.getConfig().configure(constructor.newInstance(registry));
      }
      catch (NoSuchMethodException ignored)
      {
        return registry.getConfig().configure(_implementationClass.newInstance());
      }
    }).addListener((Future<M> future) -> {
      if (future.isSuccess())
      {
        LOG.info(format("[%s] Class %s constructed", getModuleName(), future.getNow().getClass().getName()));
        registry.register(future.getNow(), ModuleInfo.this);
        _moduleInstanceFuture = registry.newSucceededFuture(future.getNow());
        _moduleInstance.setSuccess(future.getNow());
        expectAndSet(registry, ModuleState.NEW, ModuleState.IDLE, complete);
      }
      else
      {
        LOG.error(format("[%s] Error constructing instance", getModuleName()), future.cause());
        expectAndSet(registry, ModuleState.NEW, ModuleState.FAILED, NOP);
      }
    });
  }

  private void handleInit(Registry<?> registry, Runnable complete)
  {
    registry.submit(new Callable<Void>()
    {
      private final Callable<Void> self = this;
      @Override
      public Void call()
          throws Exception
      {
        List<Class<? extends Module>> dependencies = Registry.getDependencies(_moduleClass);
        Promise<Void> ready = registry.newPromise();
        AtomicInteger notReady = new AtomicInteger(dependencies.size());
        for (Class<? extends Module> clazz : dependencies)
        {
          ModuleInfo<? extends Module> mod = registry.getModuleInfo(clazz);
          ModuleState state = mod.getModuleState();
          switch (state)
          {
          case FAILED:
          case STOPPED:
          case STOPPING:
            LOG.error(format("[%s] Depends upon %s which is in %s state.",
                             getModuleName(), mod.getModuleName(), state));
            return null;

          case UNINIT:
            registry.submit(() -> mod.compareAndSet(registry, ModuleState.UNINIT, ModuleState.NEW, () -> {
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
            }));
            continue;

          case NEW:
            registry.submit(() -> {
              Thread.sleep(100);
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
              return null;
            });
            continue;

          default:
            if (notReady.decrementAndGet() == 0)
              ready.setSuccess(null);
          }
        }

        if (notReady.get() > 0)
        {
          LOG.info(format("[%s] Not ready. Waiting on Promise", getModuleName()));
          ready.addListener(future -> self.call());
          return null;
        }

        registry.submit(() -> {
          Promise<Void> promise = registry.newPromise();
          promise.addListener(future -> {
            if (future.isSuccess())
            {
              LOG.info(format("[%s] Init succeeded", getModuleName()));
              expectAndSet(registry, ModuleState.INIT, ModuleState.INITED, complete);
            }
            else
            {
              LOG.error(format("[%s] Init failed", getModuleName()), future.cause());
              expectAndSet(registry, ModuleState.INIT, ModuleState.FAILED, NOP);
            }
          });
          LOG.info(format("[%s] Executing init", getModuleName()));
          _moduleInstance.getNow().init(promise);
          return promise;
        });

        return null;
      }
    });
  }

  private void handleFlush(Registry<?> registry, Runnable complete)
  {
    registry.submit(new Callable<Void>()
    {
      private final Callable<Void> self = this;
      @Override
      public Void call()
          throws Exception
      {
        List<Class<? extends Module>> dependencies = Registry.getDependencies(_moduleClass);
        List<ModuleInfo<? extends Module>> dependents = new ArrayList<>(_dependants);
        Promise<Void> ready = registry.newPromise();
        AtomicInteger notReady = new AtomicInteger(dependencies.size() + dependents.size());
        for (ModuleInfo<? extends Module> mod : dependents)
        {
          ModuleState state = mod.getModuleState();
          switch (state)
          {
          case RUN:
            registry.submit(() -> mod.compareAndSet(registry, ModuleState.RUN, ModuleState.PAUSE, () -> {
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
            }));
            continue;

          case PAUSE:
          //case FLUSH:
            registry.submit(() -> {
              Thread.sleep(100);
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
              return null;
            });
            continue;

          default:
            if (notReady.decrementAndGet() == 0)
              ready.setSuccess(null);
          }

        }
        for (Class<? extends Module> clazz : dependencies)
        {
          ModuleInfo<? extends Module> mod = registry.getModuleInfo(clazz);
          ModuleState state = mod.getModuleState();
          switch (state)
          {
          case FAILED:
          case STOPPED:
          case STOPPING:
            LOG.error(format("[%s] Depends upon %s which is in %s state.",
                             getModuleName(), mod.getModuleName(), state));
            return null;

          case UNINIT:
            registry.submit(() -> mod.compareAndSet(registry, ModuleState.UNINIT, ModuleState.NEW, () -> {
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
            }));
            continue;

          case IDLE:
            registry.submit(() -> mod.compareAndSet(registry, ModuleState.IDLE, ModuleState.INIT, () -> {
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
            }));
            continue;

          case INITED:
            registry.submit(() -> mod.compareAndSet(registry, ModuleState.INITED, ModuleState.FLUSH, () -> {
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
            }));
            continue;

          case NEW:
          case INIT:
          case FLUSH:
            registry.submit(() -> {
              Thread.sleep(100);
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
              return null;
            });
            continue;

          default:
            if (notReady.decrementAndGet() == 0)
              ready.setSuccess(null);
          }
        }

        if (notReady.get() > 0)
        {
          LOG.info(format("[%s] Not ready. Waiting on Promise", getModuleName()));
          ready.addListener(future -> self.call());
          return null;
        }

        registry.submit(() -> {
          Promise<Void> promise = registry.newPromise();
          promise.addListener(future -> {
            if (future.isSuccess())
            {
              LOG.info(format("[%s] Flush succeeded", getModuleName()));
              expectAndSet(registry, ModuleState.FLUSH, ModuleState.RUN, complete);
            }
            else
            {
              LOG.error(format("[%s] Flush failed", getModuleName()), future.cause());
              expectAndSet(registry, ModuleState.FLUSH, ModuleState.FAILED, NOP);
            }
          });
          LOG.info(format("[%s] Executing Flush", getModuleName()));
          _moduleInstance.getNow().flush(promise);
          return promise;
        });

        return null;
      }
    });
  }

  private void handlePause(Registry<?> registry, Runnable complete)
  {
    registry.submit(new Callable<Void>()
    {
      private final Callable<Void> self = this;
      private final List<ModuleInfo<? extends Module>> dependents = new ArrayList<>(_dependants);
      @Override
      public Void call()
          throws Exception
      {
        Promise<Void> ready = registry.newPromise();
        AtomicInteger notReady = new AtomicInteger(dependents.size());
        for (ModuleInfo<? extends Module> mod : dependents)
        {
          ModuleState state = mod.getModuleState();
          switch (state)
          {
          case RUN:
            registry.submit(() -> mod.compareAndSet(registry, ModuleState.RUN, ModuleState.PAUSE, () -> {
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
            }));
            continue;

          case PAUSE:
          case FLUSH:
            registry.submit(() -> {
              Thread.sleep(100);
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
              return null;
            });
            continue;

          default:
            if (notReady.decrementAndGet() == 0)
              ready.setSuccess(null);
          }

        }

        if (notReady.get() > 0)
        {
          LOG.info(format("[%s] Not ready. Waiting on Promise", getModuleName()));
          ready.addListener(future -> self.call());
          return null;
        }

        registry.submit(() -> {
          Promise<Void> promise = registry.newPromise();
          promise.addListener(future -> {
            if (future.isSuccess())
            {
              LOG.info(format("[%s] Pause succeeded", getModuleName()));
              expectAndSet(registry, ModuleState.PAUSE, ModuleState.INITED, complete);
            }
            else
            {
              LOG.error(format("[%s] Pause failed", getModuleName()), future.cause());
              expectAndSet(registry, ModuleState.PAUSE, ModuleState.FAILED, NOP);
            }
          });
          LOG.info(format("[%s] Executing pause", getModuleName()));
          _moduleInstance.getNow().pause(promise);
          return promise;
        });

        return null;
      }
    });
  }

  private void handleStop(Registry<?> registry, Runnable complete)
  {
    registry.submit(new Callable<Void>()
    {
      private final Callable<Void> self = this;
      private final List<ModuleInfo<? extends Module>> dependents = new ArrayList<>(_dependants);
      @Override
      public Void call()
          throws Exception
      {
        Promise<Void> ready = registry.newPromise();
        AtomicInteger notReady = new AtomicInteger(dependents.size());
        for (ModuleInfo<? extends Module> mod : dependents)
        {
          ModuleState state = mod.getModuleState();
          switch (state)
          {
          case RUN:
          case INITED:
            registry.submit(() -> mod.compareAndSet(registry, state, ModuleState.STOPPING, () -> {
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
            }));
            continue;

          case UNINIT:
          case IDLE:
            registry.submit(() -> mod.compareAndSet(registry, state, ModuleState.STOPPED, () -> {
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
            }));

          case NEW:
          case INIT:
          case PAUSE:
          case FLUSH:
          case STOPPING:
            registry.submit(() -> {
              Thread.sleep(100);
              if (notReady.decrementAndGet() == 0)
                ready.setSuccess(null);
              return null;
            });
            continue;

          default:
            if (notReady.decrementAndGet() == 0)
              ready.setSuccess(null);
          }

        }

        if (notReady.get() > 0)
        {
          LOG.info(format("[%s] Not ready. Waiting on Promise", getModuleName()));
          ready.addListener(future -> self.call());
          return null;
        }

        registry.submit(() -> {
          Promise<Void> promise = registry.newPromise();
          promise.addListener(future -> {
            if (future.isSuccess())
            {
              LOG.info(format("[%s] Stop succeeded", getModuleName()));
              expectAndSet(registry, ModuleState.STOPPING, ModuleState.STOPPED, complete);
            }
            else
            {
              LOG.error(format("[%s] Stop failed", getModuleName()), future.cause());
              expectAndSet(registry, ModuleState.STOPPING, ModuleState.FAILED, NOP);
            }
          });
          LOG.info(format("[%s] Executing stop", getModuleName()));
          _moduleInstance.getNow().stop(promise);
          return promise;
        });

        return null;
      }
    });
  }


  private void expectAndSet(Registry registry, ModuleState expect, ModuleState value, Runnable complete)
  {
    if (!compareAndSet(registry, expect, value, complete))
      throw new IllegalStateException();
  }

  final boolean addDependent(ModuleInfo<? extends Module> dependant)
  {
    return dependant != this && _dependants.add(dependant);
  }

  public String getModuleName()
  {
    return _moduleClass.getName();
  }

  public String getModuleImpl()
  {
    return _implementationClass.getName();
  }

  public ModuleState getModuleState()
  {
    return _moduleState.get();
  }

  public Future<M> getInstance()
  {
    return _moduleInstanceFuture;
  }

  final Future<M> getInstance(Registry registry)
  {
    if (getModuleState() == ModuleState.UNINIT)
    {
      compareAndSet(registry, ModuleState.UNINIT, ModuleState.NEW, NOP);
    }
    return getInstance();
  }

  Future<Void> init(Registry<?> registry)
  {
    return init0(registry, registry.newPromise());
  }

  private Future<Void> init0(Registry<?> registry, Promise<Void> promise)
  {
    for (;;)
    {
      ModuleState state = getModuleState();
      switch (state)
      {
      case UNINIT:
        if (compareAndSet(registry, ModuleState.UNINIT, ModuleState.NEW, () -> init0(registry, promise))) return promise;
        break;
      case IDLE:
        if (compareAndSet(registry, ModuleState.IDLE, ModuleState.INIT, () -> init0(registry, promise))) return promise;
        break;
      case NEW:
      case INIT:
        try
        {
          Thread.sleep(100);
          continue;
        }
        catch (InterruptedException e)
        {
          promise.setFailure(e);
        }
      default:
        return promise.setSuccess(null);
      }
    }
  }

  Future<Void> flush(Registry<?> registry)
  {
    return flush0(registry, registry.newPromise());
  }
  private Future<Void> flush0(Registry<?> registry, Promise<Void> promise)
  {
    for (;;)
    {
      ModuleState state = getModuleState();
      switch (state)
      {
      case UNINIT:
        if (compareAndSet(registry, ModuleState.UNINIT, ModuleState.NEW, () -> flush0(registry, promise))) return promise;
        break;
      case IDLE:
        if (compareAndSet(registry, ModuleState.IDLE, ModuleState.INIT, () -> flush0(registry, promise))) return promise;
        break;
      case INITED:
        if (compareAndSet(registry, ModuleState.INITED, ModuleState.FLUSH, () -> promise.setSuccess(null))) return promise;
        break;
      case RUN:
        if (compareAndSet(registry, ModuleState.RUN, ModuleState.PAUSE, () -> flush0(registry, promise))) return promise;
        break;
      case NEW:
      case INIT:
      case FLUSH:
      case PAUSE:
        try
        {
          Thread.sleep(100);
          continue;
        }
        catch (InterruptedException e)
        {
          promise.setFailure(e);
        }
      default:
        return promise.setSuccess(null);
      }
    }
  }

  Future<Void> stop(Registry<?> registry)
  {
    return stop0(registry, registry.newPromise());
  }
  Future<Void> stop0(Registry<?> registry, Promise<Void> promise)
  {
    for (;;)
    {
      ModuleState state = getModuleState();
      switch (state)
      {
      case UNINIT:
        if (compareAndSet(registry, ModuleState.UNINIT, ModuleState.STOPPED, NOP)) return promise.setSuccess(null);
        break;
      case IDLE:
        if (compareAndSet(registry, ModuleState.IDLE, ModuleState.STOPPED, NOP)) return promise.setSuccess(null);
        break;
      case INITED:
        if (compareAndSet(registry, ModuleState.INITED, ModuleState.STOPPING, () -> stop0(registry, promise))) return promise;
        break;
      case RUN:
        if (compareAndSet(registry, ModuleState.RUN, ModuleState.STOPPING, () -> stop0(registry, promise))) return promise;
        break;
      case NEW:
      case INIT:
      case FLUSH:
      case PAUSE:
      case STOPPING:
        try
        {
          Thread.sleep(100);
          continue;
        }
        catch (InterruptedException e)
        {
          promise.setFailure(e);
        }
      default:
        return promise.setSuccess(null);
      }
    }
  }

}
