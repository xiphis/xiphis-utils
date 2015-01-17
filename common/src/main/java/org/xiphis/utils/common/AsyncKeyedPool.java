package org.xiphis.utils.common;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

/**
 * @author antony
 * @since 2015-01-12
 */
public interface AsyncKeyedPool<Key, Resource> {
  /**
   *
   * @param executor
   * @param key
   * @return
   */
  Future<Resource> acquire(EventExecutor executor, Key key);

  /**
   *
   * @param executor
   * @param resource
   */
  void release(EventExecutor executor, Resource resource);

  /**
   *
   * @param executor
   * @param key
   * @return
   */
  Future<? extends AsyncPool<Resource>> getPool(EventExecutor executor, Key key);
}
