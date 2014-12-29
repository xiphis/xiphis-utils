package org.xiphis.utils.common;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

/**
 * @author atcurtis
 * @since 2014-12-16
 */
public interface AsyncPool<PoolKey, Resource>
{
  /**
   *
   * @param executor
   * @param key
   * @return
   */
  Future<Resource> acquire(EventExecutor executor, PoolKey key);

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
  Future<? extends Pool<Resource>> getPool(EventExecutor executor, PoolKey key);

  /**
   *
   * @param <Resource>
   */
  public interface Pool<Resource>
  {
    /**
     * The number of resources in the pool.
     * @return number of resources.
     */
    int size();

    /**
     * Retrieve a resource from the pool.
     * @param executor Event executor.
     * @return future.
     */
    Future<Resource> allocate(EventExecutor executor);

    /**
     * Return a resource to the pool.
     * @param executor Event executor.
     * @param resource resource
     */
    void release(EventExecutor executor, Resource resource);
  }

  /**
   * Pool lifecycle object
   * @param <Resource>
   */
  public interface LifeCycle<Resource>
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

  /**
   * Factory for pool lifecycle object.
   * @param <Key>
   * @param <Resource>
   */
  public interface Factory<Key, Resource>
  {
    /**
     *
     * @param executor Event executor.
     * @param key Pool key.
     * @return Lifecycle object.
     */
    Future<? extends LifeCycle<Resource>> create(EventExecutor executor, Key key);

    /**
     * Destroy a pool lifecycle object.
     * @param resource lifecycle object.
     */
    void destroy(LifeCycle<Resource> resource);
  }
}
