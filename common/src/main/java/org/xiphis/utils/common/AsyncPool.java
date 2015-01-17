package org.xiphis.utils.common;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

/**
 * @author antony
 * @since 2015-01-12
 */
public interface AsyncPool<Resource> {

  /**
   * The number of resources in the pool.
   * @return number of resources.
   */
  int size();

  /**
   * The number of idle resources in the pool.
   * @return idle resources.
   */
  int idle();

  /**
   * Retrieve a resource from the pool.
   * @param executor Event executor.
   * @return future.
   */
  Future<Resource> acquire(EventExecutor executor);

  /**
   * Return a resource to the pool.
   * @param executor Event executor.
   * @param resource resource
   */
  void release(EventExecutor executor, Resource resource);

}
