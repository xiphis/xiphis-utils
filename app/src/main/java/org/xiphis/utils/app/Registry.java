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

import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import org.xiphis.utils.common.ConcurrentIdentityHashMap;
import org.xiphis.utils.common.Configure;
import org.xiphis.utils.common.Filter;
import org.xiphis.utils.common.Logger;
import org.xiphis.utils.common.Utils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author atcurtis
 * @since 2014-11-18
 */
public class Registry<Config extends Configure>
{
  private final Logger LOG = Logger.getInstance(getClass());

  /**
   * Wrapper around {@link #getDependencies(Class)}.
   */
  public final static Filter<List<Class<?>>, Class<?>> RECURSE = clazz -> {
    if (Module.class.isAssignableFrom(clazz))
    {
      try
      {
        return new ArrayList<>(Registry.getDependencies(clazz.asSubclass(Module.class)));
      }
      catch (ClassNotFoundException ex)
      {
        Logger.getInstance(Registry.class).error("Dependency failure", ex);
      }
    }
    return Collections.emptyList();
  };

  private final Config _configure;
  private final ConcurrentIdentityHashMap<Class<? extends Module>, ModuleInfo<? extends Module>> _modules;
  private final ConcurrentIdentityHashMap<Module, ModuleInfo<? extends Module>> _instanceMap;
  private final EventExecutorGroup _eventExecutor;
  private final AtomicBoolean _shutdown;
  private final Promise<Void> _shutdownPromise;
  final ConcurrentLinkedQueue<ModuleInfo<? extends Module>> _restartModules;
  private ModuleStateChangeListener _stateChangeListener;
  private long _shutdownGraceTime;
  private long _shutdownTimeout;
  private TimeUnit _shutdownTimeUnit;

  /**
   * Constructor.
   *
   * @param configure Instance configurator.
   * @param eventExecutor Event executor.
   */
  public Registry(Config configure, EventExecutorGroup eventExecutor)
  {
    if (configure == null)
      throw new NullPointerException();
    _eventExecutor = eventExecutor;
    _shutdownPromise = newPromise();
    _shutdown = new AtomicBoolean();
    _configure = configure;
    _modules = new ConcurrentIdentityHashMap<>();
    _instanceMap = new ConcurrentIdentityHashMap<>();
    _restartModules = new ConcurrentLinkedQueue<>();
    setShutdownGraceTimeout(100, 30000, TimeUnit.MILLISECONDS);
  }

  /**
   * Appends to the {@linkplain java.lang.StringBuilder} a representation
   * of the modules known to this registry.
   *
   * @param sb output buffer
   * @return output buffer
   */
  public StringBuilder printModuleState(StringBuilder sb)
  {
    Map<String, ModuleInfo<? extends Module>> modules = new TreeMap<>();
    for (ModuleInfo<? extends Module> mod : _modules.values())
    {
      modules.put(mod.getModuleName(), mod);
    }

    int maxModuleNameLength = 6;
    int maxModuleImplLength = 6;
    for (ModuleInfo<? extends Module> mod : modules.values())
    {
      String moduleName = mod.getModuleName();
      int pos = moduleName.lastIndexOf('.');
      if (pos > 0)
        moduleName = moduleName.substring(pos+1);
      maxModuleNameLength = Math.max(maxModuleNameLength, moduleName.length());
      maxModuleImplLength = Math.max(maxModuleImplLength, mod.getModuleImpl().length());
    }

    String format1 = "%-8s %-" + maxModuleNameLength + "s%n";
    String format2 = "%-8s %-" + maxModuleNameLength + "s -> %s%n";
    Formatter f = new Formatter(sb);
    f.format(format1, "STATE", "MODULE");
    char[] eq1 = new char[maxModuleNameLength];
    Arrays.fill(eq1, '=');
    f.format(format1, "========", String.valueOf(eq1));
    for (ModuleInfo<? extends Module> mod : modules.values())
    {
      String moduleName = mod.getModuleName();
      int pos = moduleName.lastIndexOf('.');
      if (pos > 0)
        moduleName = moduleName.substring(pos+1);

      if (mod.getModuleName().equals(mod.getModuleImpl()))
      {
        f.format(format1, mod.getModuleState(), moduleName);
      }
      else
      {
        f.format(format1, mod.getModuleState(), moduleName, mod.getModuleImpl());
      }
    }
    f.flush();
    return sb;
  }

  /**
   * The configurator instance
   * @return Configurator
   */
  public Config getConfig()
  {
    return _configure;
  }

  /**
   * The event executor
   * @return event executor
   */
  public EventExecutorGroup getEventExecutorGroup()
  {
    return _eventExecutor;
  }

  <T> Future<T> submit(Callable<T> paramCallable)
  {
    return _eventExecutor.schedule(paramCallable, 1, TimeUnit.MILLISECONDS);
  }

  <V> Promise<V> newPromise()
  {
    return _eventExecutor.next().newPromise();
  }

  <V> Future<V> newSucceededFuture(V paramV)
  {
    return _eventExecutor.next().newSucceededFuture(paramV);
  }

  <V> Future<V> newFailedFuture(Throwable paramThrowable)
  {
    return _eventExecutor.next().newFailedFuture(paramThrowable);
  }

  <V> Future<List<? extends V>> combineFutures(List<Future<? extends V>> futuresToCombine)
  {
    return Utils.combineFutures(_eventExecutor.next(), futuresToCombine);
  }

  @SuppressWarnings("unchecked")
  private <M extends Module> ModuleInfo<M> getModuleInfo0(Class<M> moduleClass)
  {
    return (ModuleInfo<M>) _modules.get(moduleClass);
  }

  @SuppressWarnings("unchecked")
  private <M extends Module> ModuleInfo<M> putIfAbsent(Class<M> moduleClass, ModuleInfo<M> info)
  {
    ModuleInfo<M> existing = (ModuleInfo<M>) _modules.putIfAbsent(moduleClass, info);
    return existing != null ? existing : info;
  }

  @SuppressWarnings("unchecked")
  private <M extends Module> ModuleInfo<M> getModuleInfo0(M module)
  {
    return (ModuleInfo<M>) _instanceMap.get(module);
  }

  <M extends Module> ModuleInfo<M> getModuleInfo(M module)
  {
    return getModuleInfo0(module);
  }

  <M extends Module> void register(M module, ModuleInfo<M> moduleInfo)
  {
    _instanceMap.put(module, moduleInfo);
  }

  <M extends Module> ModuleInfo<M> getModuleInfo(Class<M> moduleClass)
      throws ClassNotFoundException
  {
    ModuleInfo<M> info = getModuleInfo0(moduleClass);
    if (info == null)
    {
      if (isShutdown())
        throw new IllegalStateException("Registry is shutting down");
      info = putIfAbsent(moduleClass, new ModuleInfo<>(moduleClass, this));
      for (Class<? extends Module> depends : getDependencies(moduleClass))
      {
        if (getModuleInfo(depends).addDependent(info))
        {
          LOG.debug("{} depends upon {}", moduleClass.getName(), depends.getName());
        }
      }
    }
    return info;
  }

  /**
   * Resolves the modules for which the supplied module depends upon.
   *
   * @param moduleClass module
   * @return its dependencies
   */
  public static List<Class<? extends Module>> getDependencies(Class<? extends Module> moduleClass)
      throws ClassNotFoundException
  {
    Class<? extends Module> implClass = moduleClass;
    if (moduleClass.isAnnotationPresent(Implementation.class))
      implClass = moduleClass.getAnnotation(Implementation.class).value().asSubclass(moduleClass);
    Depends[] dependencies = implClass.getAnnotationsByType(Depends.class);
    if (dependencies == null || dependencies.length == 0)
      return Collections.emptyList();
    ArrayList<Class<? extends Module>> list = new ArrayList<>(dependencies.length);
    for (Depends depends : dependencies)
      list.add(getImplementationClass(depends.value()));
    return Collections.unmodifiableList(list);
  }

  /**
   * Retrieves the implementation class for the specified module which may be a subclass
   * of the module class. The resolution order is as follows:
   * <ul>
   *   <li>{@link org.xiphis.utils.app.Implementation} annotation</li>
   *   <li>{@link org.xiphis.utils.app.ImplementationClassName} annotation</li>
   *   <li>System property of the same name as the module class name</li>
   * </ul>
   * This method will not return an interface class, abstract class,
   * or a non-public class.
   *
   * @param moduleClass module class to resolve.
   * @param <M> Type of class.
   * @return implementation class
   * @throws ClassNotFoundException if suitable implementation class is not found.
   * @throws java.lang.ClassCastException if found implementation class is not cast compatible.
   */
  public static <M extends Module> Class<? extends M> getImplementationClass(Class<M> moduleClass)
      throws ClassNotFoundException
  {
    Class<? extends M> implClazz;
    if (moduleClass.isAnnotationPresent(Implementation.class))
      implClazz = moduleClass.getAnnotation(Implementation.class).value().asSubclass(moduleClass);
    else
    {
      String defaultClazzName = moduleClass.isAnnotationPresent(ImplementationClassName.class) ?
                                moduleClass.getAnnotation(ImplementationClassName.class).value() : null;
      String implClazzName = System.getProperty(moduleClass.getName(), defaultClazzName);
      implClazz = implClazzName != null ? Class.forName(implClazzName).asSubclass(moduleClass) : moduleClass;
    }

    if (implClazz.isInterface())
      throw new ClassNotFoundException("Implementation cannot be an interface");

    int modifiers = implClazz.getModifiers();
    if (Modifier.isAbstract(modifiers) || !Modifier.isPublic(modifiers))
      throw new ClassNotFoundException("Implementation cannot be abstract or non-public");

    return implClazz;
  }

  /**
   * Retrieve the future which will contain the instance of the provided module.
   * Modules are instantiated lazily as required.
   *
   * @param moduleClass module class
   * @param <M> Type of module class.
   * @return future which will contain module instance
   */
  public <M extends Module> Future<M> getModule(Class<M> moduleClass)
  {
    ModuleInfo<M> info;
    try
    {
      info = getModuleInfo(moduleClass);
    }
    catch (ClassNotFoundException e)
    {
      return newFailedFuture(e);
    }
    return info.getInstance(this);
  }

  /**
   * Initiates a stop of the specified module. This would implicitly
   * stop all modules which depend upon the specified module.
   *
   * @param moduleClass module to stop
   * @return Future which is asynchronously completed.
   */
  public Future<Void> stop(Class<? extends Module> moduleClass)
  {
    ModuleInfo<? extends Module> info = getModuleInfo0(moduleClass);
    if (info != null)
      return info.stop(this);
    else
      return newSucceededFuture(null);
  }

  /**
   * Initiates an init of the specified module. This would implicitly
   * instantiate all modules which are depend upon by the specified module.
   *
   * @param moduleClass module to init
   * @return Future which is asynchronously completed.
   */
  public Future<Void> init(Class<? extends Module> moduleClass)
  {
    ModuleInfo<? extends Module> module;
    try
    {
      module = getModuleInfo(moduleClass);
    }
    catch (ClassNotFoundException e)
    {
      return newFailedFuture(e);
    }
    return module.init(this);
  }

  /**
   * Initiates a flush of the specified module. This would implicitly
   * flush all modules which depend upon the specified module.
   *
   * @param moduleClass module to flush
   * @return Future which is asynchronously completed.
   */
  public Future<Void> flush(Class<? extends Module> moduleClass)
  {
    ModuleInfo<? extends Module> info = getModuleInfo0(moduleClass);
    if (info != null)
      return info.flush(this);
    else
      return newFailedFuture(new NoSuchElementException());
  }

  /**
   * Set the current state change listener or {@code null} to remove.
   * @param listener listener
   */
  public void setStateChangeListener(ModuleStateChangeListener listener)
  {
    _stateChangeListener = listener;
  }

  void stateChanged(Class<? extends Module> clazz, ModuleState fromState, ModuleState toState)
  {
    LOG.debug("[{}] {} -> {}", clazz.getName(), fromState, toState);
    if (_stateChangeListener != null)
    {
      _stateChangeListener.onChange(clazz, fromState, toState);
    }
  }

  public void setShutdownGraceTimeout(long graceTime, long timeoutTime, TimeUnit unit)
  {
    if (unit == null)
      throw new NullPointerException();
    if (graceTime < 0 || timeoutTime < 0)
      throw new IllegalArgumentException();
    _shutdownGraceTime = graceTime;
    _shutdownTimeout = timeoutTime;
    _shutdownTimeUnit = unit;
  }

  /**
   * Initiates an orderly shutdown in which registered modules are
   * transitioned into the STOPPED state, but no new modules will
   * be registered.
   * Invocation has no additional effect if already shut down.
   */
  @SuppressWarnings("unchecked")
  public void shutdown()
  {
    if (!_shutdown.getAndSet(true))
    {
      LOG.info("Starting shutdown");

      List<ModuleInfo<? extends Module>> infos = new ArrayList<>(_modules.values());
      List<Future<?>> futures = new ArrayList<>(infos.size());
      for (ModuleInfo<? extends Module> info : infos)
        futures.add(info.stop(this));

      combineFutures(futures).addListener((Future<List<?>> listFuture) -> {
        if (listFuture.isSuccess()) {
          if (_eventExecutor != GlobalEventExecutor.INSTANCE) {
            LOG.debug("Shutting down EventExecutor");
            Future shutdownFuture = _eventExecutor.shutdownGracefully(_shutdownGraceTime, _shutdownTimeout, _shutdownTimeUnit);
            GenericFutureListener<Future<?>> shutdownListener = future -> {
              if (future.isSuccess())
                _shutdownPromise.setSuccess(null);
              else {
                LOG.error("Error shutting down EventExecutor", future.cause());
                _shutdownPromise.setFailure(future.cause());
              }
            };
            shutdownFuture.addListener(shutdownListener);
          }
          else
          {
            _shutdownPromise.setSuccess(null);
          }
        }
        else {
          LOG.error("Shutdown encountered error", listFuture.cause());
          if (_eventExecutor != GlobalEventExecutor.INSTANCE)
            _eventExecutor.shutdownGracefully(0, _shutdownTimeout, _shutdownTimeUnit);
          _shutdownPromise.setFailure(listFuture.cause());
        }
      });
    }
  }

  /**
   * Returns {@code true} if this registry has been shut down.
   *
   * @return {@code true} if this registry has been shut down
   */
  public boolean isShutdown()
  {
    return _shutdown.get();
  }

  /**
   * Returns {@code true} if all tasks have completed following shut down.
   * Note that {@code isTerminated} is never {@code true} unless
   * either {@code shutdown} or {@code shutdownNow} was called first.
   *
   * @return {@code true} if all tasks have completed following shut down
   */
  public boolean isTerminated()
  {
    return _shutdownPromise.isDone();
  }

  /**
   * Blocks until all tasks have completed execution after a shutdown
   * request or the current thread is ninterrupted, whichever
   * happens first.
   *
   * @throws InterruptedException if interrupted while waiting
   */
  public void awaitTermination()
      throws InterruptedException
  {
    _shutdownPromise.sync();
  }

  /**
   * Blocks until all tasks have completed execution after a shutdown
   * request, or the timeout occurs, or the current thread is
   * interrupted, whichever happens first.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the timeout argument
   * @return {@code true} if this registry terminated and
   *         {@code false} if the timeout elapsed before termination
   * @throws InterruptedException if interrupted while waiting
   */
  public boolean awaitTermination(long timeout, TimeUnit unit)
      throws InterruptedException
  {
    if(_shutdownPromise.await(timeout, unit))
    {
      _shutdownPromise.syncUninterruptibly();
      return true;
    }
    return false;
  }
}
