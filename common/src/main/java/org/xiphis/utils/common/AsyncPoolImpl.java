package org.xiphis.utils.common;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author atcurtis
 * @since 2014-12-17
 */
public class AsyncPoolImpl<PoolKey, Resource> implements AsyncPool<PoolKey, Resource>
{
  private final Logger LOG = Logger.getInstance(getClass());
  private final ConcurrentHashMap<PoolKey, Future<? extends Pool<Resource>>> _poolMap;
  private final ConcurrentIdentityHashMap<Resource, Element> _elementMap;
  private final Factory<PoolKey, Resource> _factory;
  private int _maxPoolSize;
  private int _maxCreateInProgress;
  private ImmutablePair<Long, TimeUnit> _checkTime;
  private ImmutablePair<Long, TimeUnit> _expireTime;
  private final Random _rnd = new Random(System.nanoTime());

  public AsyncPoolImpl(Factory<PoolKey, Resource> factory)
  {
    _poolMap = new ConcurrentHashMap<>();
    _elementMap = new ConcurrentIdentityHashMap<>();
    _factory = factory;
    setCheckTime(30, TimeUnit.SECONDS);
    setExpireTime(24, TimeUnit.HOURS);
    setMaxPoolSize(100);
    setMaxPoolCreateInProgress(1);
  }

  public final int getMaxPoolSize()
  {
    return _maxPoolSize;
  }

  public void setMaxPoolSize(int size)
  {
    _maxPoolSize = size;
  }

  public final int getMaxPoolCreateInProgress()
  {
    return _maxCreateInProgress;
  }

  public void setMaxPoolCreateInProgress(int createInProgress)
  {
    if (createInProgress < 1)
      throw new IllegalArgumentException();
    _maxCreateInProgress = createInProgress;
  }

  public void setCheckTime(long time, TimeUnit unit)
  {
    if (unit == null)
      throw new NullPointerException();
    if (time < 1)
      throw new IllegalArgumentException();
    _checkTime = ImmutablePair.make(time, unit);
  }

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
    Future<? extends Pool<Resource>> poolFuture = getPool(executor, key);

    if (poolFuture.isDone())
    {
      return poolFuture.isSuccess() ? poolFuture.getNow().allocate(executor)
                                    : executor.newFailedFuture(poolFuture.cause());
    }

    Promise<Resource> promise = executor.newPromise();
    poolFuture.addListener((Future<Pool<Resource>> future) -> {
      if (future.isSuccess())
      {
        if (promise.isCancelled())
          return;
        future.getNow().allocate(executor).addListener((Future<Resource> resource) -> {
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
    Element element = _elementMap.get(resource);
    if (element == null)
      throw new IllegalStateException();
    element.pool.release(executor, resource);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<? extends Pool<Resource>> getPool(EventExecutor executor, PoolKey key)
  {
    Future<? extends Pool<Resource>> poolFuture = _poolMap.get(key);
    if (poolFuture == null)
    {
      Promise<PoolImpl> promise = executor.newPromise();
      if ((poolFuture = _poolMap.putIfAbsent(key, promise)) == null)
      {
        promise.addListener((Future<Pool<Resource>> future) -> {
          if (!future.isSuccess())
          {
            _poolMap.remove(key, promise);
          }
        });

        _factory.create(executor, key).addListener((Future<LifeCycle<Resource>> future) -> {
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

  protected void notifyAcquireTime(PoolKey key, long timeInMillis, boolean success)
  {
    assert key != null;
  }

  protected void notifyCreateTime(PoolKey key, long timeInMillis, boolean success)
  {
    assert key != null;
  }

  protected void notifyResetTime(PoolKey key, long timeInMillis, boolean success)
  {
    assert key != null;
  }

  protected <T> AsyncQueue<T> newAsyncQueue()
  {
    return new AsyncLinkedQueue<>();
  }

  private class PoolImpl implements Pool<Resource>
  {
    private final PoolKey _poolKey;
    private final LifeCycle<Resource> _lifeCycle;
    private final AtomicInteger _poolSize;
    private final AtomicInteger _createInProgress;
    private final AsyncQueue<Element> _idle;

    private PoolImpl(PoolKey poolKey, LifeCycle<Resource> lifeCycle)
    {
      _poolKey = poolKey;
      _lifeCycle = lifeCycle;
      _poolSize = new AtomicInteger();
      _createInProgress = new AtomicInteger();
      _idle = newAsyncQueue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size()
    {
      return _poolSize.get();
    }

    private void remove(Element e)
    {
      _elementMap.remove(e.resource, e);
      _poolSize.decrementAndGet();
      _lifeCycle.destroy(e.resource);
    }

    private void offer(EventExecutor executor, Element e)
    {
      ImmutablePair<Long, TimeUnit> checkTime = _checkTime;
      e.timestamp = System.currentTimeMillis();
      Future<?> future = _idle.offer(executor, e);
      Future<?> check = executor.schedule(() -> {
        if (future.cancel(false))
        {
          _lifeCycle.check(executor, e.resource).addListener(checkFuture -> {
            if (checkFuture.isSuccess())
              offer(executor, e);
            else
              remove(e);
          });
        }
      }, checkTime.first, checkTime.second);

      future.addListener((Future<Object> offer) -> {
        check.cancel(false);
        if (offer.isSuccess() || offer.isCancelled())
          return;
        remove(e);
      });
    }

    private void create0(EventExecutor executor)
    {
      long startTime = System.currentTimeMillis();
      _lifeCycle.create(executor).addListener((Future<Resource> createFuture) -> {
        if (createFuture.isSuccess())
        {
          Element e = new Element(this, createFuture.getNow());
          if (_elementMap.putIfAbsent(e.resource, e) == null)
          {
            long createTimeInMillis = System.currentTimeMillis() - startTime;

            boolean notFull = _poolSize.incrementAndGet() < getMaxPoolSize();
            offer(executor, e);

            notifyCreateTime(_poolKey, createTimeInMillis, true);

            if (notFull && _idle.getWaiters() > 0)
            {
              _createInProgress.incrementAndGet();
              create0(executor);
            }
          }
        }
        else
        {
          notifyCreateTime(_poolKey, System.currentTimeMillis() - startTime, false);
          _idle.failWaiters(createFuture.cause());
        }
        _createInProgress.decrementAndGet();
      });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Resource> allocate(EventExecutor executor)
    {
      long startTime = System.currentTimeMillis();

      int createInProgress;
      if (_idle.isEmpty() && _poolSize.get() < getMaxPoolSize() &&
          (createInProgress = _createInProgress.get()) < getMaxPoolCreateInProgress() &&
          _createInProgress.compareAndSet(createInProgress, createInProgress+1))
      {
        create0(executor);
      }

      Future<Element> elementFuture = _idle.take(executor);
      if (elementFuture.isSuccess())
      {
        long acquireTimeInMillis = System.currentTimeMillis() - startTime;
        try
        {
          return executor.newSucceededFuture(elementFuture.getNow().resource);
        }
        finally
        {
          notifyAcquireTime(_poolKey, acquireTimeInMillis, true);
        }
      }
      else
      {
        Promise<Resource> promise = executor.newPromise();
        elementFuture.addListener((Future<Element> future) -> {
          if (future.isSuccess())
          {
            long acquireTimeInMillis = System.currentTimeMillis() - startTime;
            if (promise.trySuccess(future.getNow().resource))
            {
              notifyAcquireTime(_poolKey, acquireTimeInMillis, true);
              return;
            }
            offer(executor, future.getNow());
          }
          else
          {
            notifyAcquireTime(_poolKey, System.currentTimeMillis() - startTime, false);
            promise.setFailure(future.cause());
          }
        });
        return promise;
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void release(EventExecutor executor, Resource resource)
    {
      long startTime = System.currentTimeMillis();
      Element e = _elementMap.get(resource);
      if (e.pool != this)
        throw new IllegalStateException();
      executor.submit(() -> _lifeCycle.reset(executor, resource).addListener(future -> {
        long now = System.currentTimeMillis();
        if (future.isSuccess() && getMaxPoolSize() >= size() && now < e.expireTimestamp)
          offer(executor, e);
        else
          remove(e);
        notifyResetTime(_poolKey, now - startTime, future.isSuccess());
      }));
    }
  }

  protected long getExpireMilliseconds()
  {
    ImmutablePair<Long, TimeUnit> expireTime = _expireTime;
    long lifetime = expireTime.second.toMillis(expireTime.first);
    synchronized (_rnd)
    {
      return Math.round(lifetime * (0.95 + _rnd.nextDouble() * 0.1));
    }
  }

  private class Element
  {
    final PoolImpl pool;
    final Resource resource;
    final long createTimestamp;
    final long expireTimestamp;
    long timestamp;

    private Element(PoolImpl pool, Resource resource)
    {
      this.pool = pool;
      this.resource = resource;
      this.createTimestamp = System.currentTimeMillis();
      this.expireTimestamp = createTimestamp + getExpireMilliseconds();
    }
  }
}
