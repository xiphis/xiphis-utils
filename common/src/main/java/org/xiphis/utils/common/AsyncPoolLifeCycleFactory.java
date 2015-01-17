package org.xiphis.utils.common;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

/**
 * Factory for pool lifecycle object.
 * @param <Key>
 * @param <Resource>
 * @author antony
 * @since 2015-01-12
 */
public interface AsyncPoolLifeCycleFactory<Key, Resource>
{
  /**
   *
   * @param executor Event executor.
   * @param key Pool key.
   * @return Lifecycle object.
   */
  Future<? extends AsyncPoolLifeCycle<Resource>> create(EventExecutor executor, Key key);

  /**
   * Destroy a pool lifecycle object.
   * @param resource lifecycle object.
   */
  void destroy(AsyncPoolLifeCycle<Resource> resource);
}
