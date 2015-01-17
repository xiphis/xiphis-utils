package org.xiphis.utils.common;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

/**
 * Pool lifecycle object
 * @param <Resource>
 * @author antony
 * @since 2015-01-12
 */
public interface AsyncPoolLifeCycle<Resource>
{
  /**
   *
   * @param executor
   * @return
   */
  Future<Resource> create(EventExecutor executor);

  /**
   *
   * @param resource
   */
  void destroy(Resource resource);

  /**
   *
   * @param executor
   * @param resource
   * @return
   */
  Future<Resource> reset(EventExecutor executor, Resource resource);

  /**
   *
   * @param executor
   * @param resource
   * @return
   */
  Future<Resource> check(EventExecutor executor, Resource resource);
}