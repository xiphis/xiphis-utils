package org.xiphis.utils.common;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author atcurtis
 * @since 2014-12-17
 */
public class AsyncKeyedPoolImpl<PoolKey, Resource> implements AsyncKeyedPool<PoolKey, Resource>
{
  private final Logger LOG = Logger.getInstance(getClass());
  private final ConcurrentHashMap<PoolKey, Future<PoolImpl>> _poolMap;
  private final ConcurrentIdentityHashMap<Resource, AsyncPoolImpl.Element<Resource>> _elementMap;
  private final AsyncPoolLifeCycleFactory<PoolKey, Resource> _factory;
  private int _maxPoolSize;
  private int _maxCreateInProgress;
  private ImmutablePair<Long, TimeUnit> _checkTime;
  private ImmutablePair<Long, TimeUnit> _expireTime;
  private final Random _rnd = new Random(System.nanoTime());

  private final Consumer<Future<PoolImpl>> _propagateMaxPoolSize =
      pool -> pool.getNow().setMaxPoolSize(_maxPoolSize);

  private final Consumer<Future<PoolImpl>> _propagateMaxPoolCreateInProgress =
      pool -> pool.getNow().setMaxCreateInProgress(_maxCreateInProgress);

  private final Consumer<Future<PoolImpl>> _propagateCheckTime =
      pool -> pool.getNow().setCheckTime(_checkTime);

  /**
   *
   * @param factory
   * @param maxPoolSize
   * @param maxCreateInProgress
   */
  public AsyncKeyedPoolImpl(AsyncPoolLifeCycleFactory<PoolKey, Resource> factory,
                            int maxPoolSize, int maxCreateInProgress)
  {
    if (factory == null)
      throw new NullPointerException();
    _poolMap = new ConcurrentHashMap<>();
    _elementMap = new ConcurrentIdentityHashMap<>();
    _factory = factory;
    setMaxPoolSize(maxPoolSize);
    setMaxPoolCreateInProgress(maxCreateInProgress);
  }

  /**
   *
   * @return
   */
  public final int getMaxPoolSize()
  {
    return _maxPoolSize;
  }

  /**
   *
   * @param size
   */
  public void setMaxPoolSize(int size)
  {
    _maxPoolSize = size;

    _poolMap.values().stream().filter(Future::isSuccess).forEach(_propagateMaxPoolSize);
  }

  /**
   *
   * @return
   */
  public final int getMaxPoolCreateInProgress()
  {
    return _maxCreateInProgress;
  }

  /**
   *
   * @param createInProgress
   */
  public void setMaxPoolCreateInProgress(int createInProgress)
  {
    if (createInProgress < 1)
      throw new IllegalArgumentException();
    _maxCreateInProgress = createInProgress;

    _poolMap.values().stream().filter(Future::isSuccess).forEach(_propagateMaxPoolCreateInProgress);
  }

  /**
   *
   * @param time
   * @param unit
   */
  public void setCheckTime(long time, TimeUnit unit)
  {

    if (unit == null)
      throw new NullPointerException();
    if (time < 1)
      throw new IllegalArgumentException();
    _checkTime = ImmutablePair.make(time, unit);

    _poolMap.values().stream().filter(Future::isSuccess).forEach(_propagateCheckTime);
  }

  /**
   *
   * @param time
   * @param unit
   */
  public void setExpireTime(long time, TimeUnit unit)
  {
    if (unit == null)
      throw new NullPointerException();
    if (time < 1)
      throw new IllegalArgumentException();
    _expireTime = ImmutablePair.make(time, unit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<Resource> acquire(EventExecutor executor, PoolKey key)
  {
    Future<? extends AsyncPool<Resource>> poolFuture = getPool(executor, key);

    if (poolFuture.isDone())
    {
      return poolFuture.isSuccess() ? poolFuture.getNow().acquire(executor)
                                    : executor.newFailedFuture(poolFuture.cause());
    }

    Promise<Resource> promise = executor.newPromise();
    poolFuture.addListener((Future<AsyncPool<Resource>> future) -> {
      if (future.isSuccess())
      {
        if (promise.isCancelled())
          return;
        future.getNow().acquire(executor).addListener((Future<Resource> resource) -> {
          if (resource.isSuccess())
          {
            if (!promise.trySuccess(resource.getNow()))
            {
              future.getNow().release(executor, resource.getNow());
            }
          }
          else
          {
            promise.setFailure(resource.cause());
          }
        });
      }
      else
      {
        promise.setFailure(future.cause());
      }
    });

    return promise;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void release(EventExecutor executor, Resource resource)
  {
    if (resource == null)
      return;
    AsyncPoolImpl.Element<Resource> element = _elementMap.get(resource);
    if (element == null)
      throw new IllegalStateException();
    element.pool.release(executor, resource);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<? extends AsyncPool<Resource>> getPool(EventExecutor executor, PoolKey key)
  {
    if (executor == null || key == null)
      throw new NullPointerException();
    Future<? extends AsyncPool<Resource>> poolFuture = _poolMap.get(key);
    if (poolFuture == null)
    {
      Promise<PoolImpl> promise = executor.newPromise();
      if ((poolFuture = _poolMap.putIfAbsent(key, promise)) == null)
      {
        promise.addListener((Future<AsyncPool<Resource>> future) -> {
          if (!future.isSuccess())
          {
            _poolMap.remove(key, promise);
          }
        });

        _factory.create(executor, key).addListener((Future<AsyncPoolLifeCycle<Resource>> future) -> {
          if (future.isSuccess())
          {
            PoolImpl pool = new PoolImpl(key, future.getNow());
            if (promise.trySuccess(pool))
            {
              _poolMap.replace(key, promise, executor.newSucceededFuture(pool));
            }
            else
            {
              _factory.destroy(future.getNow());
            }
          }
        });
        poolFuture = promise;
      }
    }
    return poolFuture;
  }

  /**
   *
   * @param key
   * @param timeInMillis
   * @param success
   */
  protected void notifyAcquireTime(PoolKey key, long timeInMillis, boolean success)
  {
    assert key != null;
    LOG.trace("notifyAcquireTime(\"{}\",{},{}", key, timeInMillis, success);
  }

  /**
   *
   * @param key
   * @param timeInMillis
   * @param success
   */
  protected void notifyCreateTime(PoolKey key, long timeInMillis, boolean success)
  {
    assert key != null;
    LOG.trace("notifyCreateTime(\"{}\",{},{}", key, timeInMillis, success);
  }

  /**
   *
   * @param key
   * @param timeInMillis
   * @param success
   */
  protected void notifyResetTime(PoolKey key, long timeInMillis, boolean success)
  {
    assert key != null;
    LOG.trace("notifyResetTime(\"{}\",{},{}", key, timeInMillis, success);
  }

  /**
   *
   * @param key
   * @param lifetimeInMillis
   */
  protected void notifyLifetime(PoolKey key, long lifetimeInMillis)
  {
    assert key != null;
    LOG.trace("notifyLifetime(\"{}\",{})", key, lifetimeInMillis);
  }

  /**
   * Constructs an {@link org.xiphis.utils.common.AsyncQueue} implementation to be used by
   * the {@link org.xiphis.utils.common.AsyncPoolImpl} implementation.
   * @param <T> Element type
   * @return new instance
   */
  protected <T> AsyncQueue<T> newAsyncQueue()
  {
    return new AsyncLinkedQueue<>();
  }

  private class PoolImpl extends AsyncPoolImpl<Resource>
  {
    private final PoolKey _poolKey;

    private PoolImpl(PoolKey poolKey, AsyncPoolLifeCycle<Resource> lifeCycle)
    {
      super(lifeCycle, newAsyncQueue(), _elementMap,
          AsyncKeyedPoolImpl.this.getMaxPoolSize(),
          AsyncKeyedPoolImpl.this.getMaxPoolCreateInProgress(),
          AsyncKeyedPoolImpl.this.getExpireMilliseconds());
      _poolKey = poolKey;
      setCheckTime(_checkTime);
    }

    @Override
    protected void notifyAcquireTime(long timeInMillis, boolean success)
    {
      AsyncKeyedPoolImpl.this.notifyAcquireTime(_poolKey, timeInMillis, success);
    }

    @Override
    protected void notifyCreateTime(long timeInMillis, boolean success)
    {
      AsyncKeyedPoolImpl.this.notifyCreateTime(_poolKey, timeInMillis, success);
    }

    @Override
    protected void notifyResetTime(long timeInMillis, boolean success)
    {
      AsyncKeyedPoolImpl.this.notifyResetTime(_poolKey, timeInMillis, success);
    }

    @Override
    protected void notifyLifetime(long lifetimeInMillis)
    {
      AsyncKeyedPoolImpl.this.notifyLifetime(_poolKey, lifetimeInMillis);
    }
  }

  /**
   * Returns the maximum time for a resource to live, with a bit of random jitter.
   * @return resource lifetime in milliseconds.
   */
  protected long getExpireMilliseconds()
  {
    ImmutablePair<Long, TimeUnit> expireTime = _expireTime;
    if (expireTime == null)
      return 0L;
    long lifetime = expireTime.second.toMillis(expireTime.first);
    synchronized (_rnd)
    {
      return Math.round(lifetime * (0.95 + _rnd.nextDouble() * 0.1));
    }
  }
}
