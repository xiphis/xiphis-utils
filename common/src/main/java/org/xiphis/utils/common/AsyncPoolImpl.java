package org.xiphis.utils.common;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * @author antony
 * @since 2015-01-12
 */
public class AsyncPoolImpl<Resource> implements AsyncPool<Resource>, Shutdownable
{
  private final Logger LOG = Logger.getInstance(getClass());
  private final AsyncPoolLifeCycle<Resource> _lifeCycle;
  private volatile int _poolSize;
  private volatile int _createInProgress;
  private final AsyncQueue<Element<Resource>> _idle;
  private final Map<Resource, Element<Resource>> _elementMap;
  private int _maxPoolSize;
  private int _maxCreateInProgress;
  private long _expireMilliseconds;
  private ImmutablePair<Long, TimeUnit> _checkTime;
  private volatile CountDownLatch _shutdown = null;
  private final Element<Resource> _shutdownElement = new Element<>(this);

  private static final AtomicIntegerFieldUpdater<AsyncPoolImpl> _poolSizeUpdater =
      AtomicIntegerFieldUpdater.newUpdater(AsyncPoolImpl.class, "_poolSize");
  private static final AtomicIntegerFieldUpdater<AsyncPoolImpl> _createInProgressUpdater =
      AtomicIntegerFieldUpdater.newUpdater(AsyncPoolImpl.class, "_createInProgress");
  private static final AtomicReferenceFieldUpdater<AsyncPoolImpl, CountDownLatch> _shutdownUpdater =
      AtomicReferenceFieldUpdater.newUpdater(AsyncPoolImpl.class, CountDownLatch.class, "_shutdown");
  private static final Future<?> _emptyFuture;

  static {
    _emptyFuture = new EmptyPromise<>();
    _emptyFuture.cancel(false);
  }

  public AsyncPoolImpl(AsyncPoolLifeCycle<Resource> lifeCycle,
                       int maxPoolSize)
  {
    this(lifeCycle, new AsyncLinkedQueue<>(),
        Collections.synchronizedMap(new IdentityHashMap<>()),
        maxPoolSize, 1, 0);
  }

  public AsyncPoolImpl(AsyncPoolLifeCycle<Resource> lifeCycle,
                       AsyncQueue<Element<Resource>> idleQueue,
                       Map<Resource, Element<Resource>> elementMap,
                       int maxPoolSize, int maxPoolCreateInProgress,
                       long expireMilliseconds)
  {
    if (lifeCycle == null || idleQueue == null || elementMap == null)
      throw new NullPointerException();
    _lifeCycle = lifeCycle;
    _poolSize = 0;
    _createInProgress = 0;
    _idle = idleQueue;
    _elementMap = elementMap;
    setMaxPoolSize(maxPoolSize);
    setMaxCreateInProgress(maxPoolCreateInProgress);
    setExpireMilliseconds(expireMilliseconds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int size()
  {
    return _poolSize;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int idle()
  {
    return _idle.size();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int waiters()
  {
    return _idle.getWaiters();
  }

  /**
   *
   * @return
   */
  public ImmutablePair<Long, TimeUnit> getCheckTime()
  {
    return _checkTime;
  }

  /**
   *
   * @param time
   * @param unit
   */
  public void setCheckTime(long time, TimeUnit unit)
  {
    setCheckTime(ImmutablePair.make(time, unit));
  }

  /**
   *
   * @param checkTime
   */
  public void setCheckTime(ImmutablePair<Long, TimeUnit> checkTime) {
    if (checkTime != null) {
      if (checkTime.first == null || checkTime.second == null)
        throw new NullPointerException();
      if (checkTime.first < 1)
        throw new IllegalArgumentException();
    }
    _checkTime = checkTime;
  }

  /**
   *
   * @return
   */
  public long getExpireMilliseconds() {
    return _expireMilliseconds;
  }

  /**
   *
   * @param expireMilliseconds
   */
  public void setExpireMilliseconds(long expireMilliseconds) {
    if (expireMilliseconds < 0)
      throw new IllegalArgumentException();
    _expireMilliseconds = expireMilliseconds;
  }

  /**
   *
   * @return
   */
  public int getMaxPoolSize() {
    return _maxPoolSize;
  }

  /**
   *
   * @param maxPoolSize
   */
  public void setMaxPoolSize(int maxPoolSize) {
    if (maxPoolSize < 0)
      throw new IllegalArgumentException();
    _maxPoolSize = maxPoolSize;
  }

  /**
   *
   * @return
   */
  public int getMaxCreateInProgress() {
    return _maxCreateInProgress;
  }

  /**
   *
   * @param maxPoolCreateInProgress
   */
  public void setMaxCreateInProgress(int maxPoolCreateInProgress) {
    if (maxPoolCreateInProgress < 0)
      throw new IllegalArgumentException();
    _maxCreateInProgress = maxPoolCreateInProgress;
  }


  private void remove(Element<Resource> e)
  {
    if (_elementMap.remove(e.resource, e)) {
      _poolSizeUpdater.decrementAndGet(this);
      try {
        _lifeCycle.destroy(e.resource);
      }
      finally {
        notifyLifetime(System.currentTimeMillis() - e.createTimestamp);
      }
    }
    else {
      LOG.debug("Element not removed");
    }
  }

  private void offer(EventExecutor executor, Element<Resource> e)
  {
    ImmutablePair<Long, TimeUnit> checkTime = getCheckTime();
    e.timestamp = System.currentTimeMillis();
    Future<?> future = _idle.offer(executor, e);
    if (e == _shutdownElement)
      return;
    Future<?> check = checkTime != null ? executor.schedule(() -> {
      if (future.cancel(false)) {
        if (isShutdown()) {
          remove(e);
          return;
        }
        _lifeCycle.check(executor, e.resource).addListener(checkFuture -> {
          if (checkFuture.isSuccess())
            offer(executor, e);
          else
            remove(e);
        });
      }
    }, checkTime.first, checkTime.second) : _emptyFuture;

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
      if (createFuture.isSuccess()) {
        Element<Resource> e = new Element<>(this, createFuture.getNow());
        if (_elementMap.putIfAbsent(e.resource, e) == null) {
          long createTimeInMillis = System.currentTimeMillis() - startTime;

          int maxPoolSize = getMaxPoolSize();
          boolean notFull = _poolSizeUpdater.incrementAndGet(this) < maxPoolSize || maxPoolSize == 0;
          offer(executor, e);

          notifyCreateTime(createTimeInMillis, true);

          if (notFull && _idle.getWaiters() > 0) {
            _createInProgressUpdater.incrementAndGet(this);
            create0(executor);
          }
        }
      } else {
        notifyCreateTime(System.currentTimeMillis() - startTime, false);
        _idle.failWaiters(createFuture.cause());
      }
      _createInProgressUpdater.decrementAndGet(this);
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<Resource> acquire(EventExecutor executor) {
    if (isShutdown()) {
      notifyAcquireTime(0, false);
      return executor.newFailedFuture(new RejectedExecutionException());
    }

    long startTime = System.currentTimeMillis();

    int maxPoolSize = getMaxPoolSize(), maxCreateInProgress = getMaxCreateInProgress();
    int createInProgress;
    if (_idle.isEmpty() && (_poolSize < maxPoolSize || maxPoolSize == 0) &&
        ((createInProgress = _createInProgress) < maxCreateInProgress || maxCreateInProgress == 0) &&
        _createInProgressUpdater.compareAndSet(this, createInProgress, createInProgress + 1)) {
      create0(executor);
    }

    Future<Element<Resource>> elementFuture = _idle.take(executor);
    if (elementFuture.isSuccess()) {
      long acquireTimeInMillis = System.currentTimeMillis() - startTime;
      try {
        return executor.newSucceededFuture(elementFuture.getNow().resource);
      } finally {
        notifyAcquireTime(acquireTimeInMillis, true);
      }
    } else {
      Promise<Resource> promise = executor.newPromise();
      elementFuture.addListener((Future<Element<Resource>> future) -> {
        if (future.isSuccess()) {
          long acquireTimeInMillis = System.currentTimeMillis() - startTime;
          if (future.getNow() == _shutdownElement) {
            if (promise.tryFailure(new RejectedExecutionException()))
              notifyAcquireTime(acquireTimeInMillis, false);
            return;
          }
          if (promise.trySuccess(future.getNow().resource)) {
            notifyAcquireTime(acquireTimeInMillis, true);
            return;
          }
          offer(executor, future.getNow());
        } else {
          notifyAcquireTime(System.currentTimeMillis() - startTime, false);
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
  public void release(EventExecutor executor, Resource resource) {
    long startTime = System.currentTimeMillis();
    Element<Resource> e = _elementMap.get(resource);
    if (e.pool != this)
      throw new IllegalStateException();
    executor.submit(() -> _lifeCycle.reset(executor, resource).addListener(future -> {
      long now = System.currentTimeMillis();
      int maxPoolSize;
      if (isShutdown()) {
        offer(executor, _shutdownElement);
        remove(e);
      }
      else
      if (future.isSuccess() &&
          ((maxPoolSize = getMaxPoolSize()) == 0 || maxPoolSize >= size()) &&
          (e.expireTimestamp == 0L || now < e.expireTimestamp))
        offer(executor, e);
      else
        remove(e);
      notifyResetTime(now - startTime, future.isSuccess());
    }));
  }

  /**
   * Invoked after a Resource is successfully issued to a caller of {@link #acquire(io.netty.util.concurrent.EventExecutor)}
   * @param timeInMillis time taken in milliseconds.
   * @param success {@code true} if successful.
   */
  protected void notifyAcquireTime(long timeInMillis, boolean success)
  {
    LOG.trace("notifyAcquireTime({},{})", timeInMillis, success);
  }

  /**
   * Invoked after {@link org.xiphis.utils.common.AsyncPoolLifeCycle#create(io.netty.util.concurrent.EventExecutor)} is invoked.
   * @param timeInMillis time taken for reset, in milliseconds.
   * @param success {@code true} if successful.
   */
  protected void notifyCreateTime(long timeInMillis, boolean success)
  {
    LOG.trace("notifyCreateTime({},{})", timeInMillis, success);
  }

  /**
   * Invoked after {@link org.xiphis.utils.common.AsyncPoolLifeCycle#reset(io.netty.util.concurrent.EventExecutor, Resource)} is invoked.
   * @param timeInMillis time taken for reset, in milliseconds.
   * @param success {@code true} if reset was successful.
   */
  protected void notifyResetTime(long timeInMillis, boolean success)
  {
    LOG.trace("notifyResetTime({},{})", timeInMillis, success);
  }

  /**
   * Invoked after {@link org.xiphis.utils.common.AsyncPoolLifeCycle#destroy(Resource)} is invoked.
   * @param lifetimeInMillis Resource lifetime in milliseconds.
   */
  protected void notifyLifetime(long lifetimeInMillis)
  {
    LOG.trace("notifyLifetime({})", lifetimeInMillis);
  }

  @Override
  public void shutdown() {
    if (_shutdown == null) {
      _shutdownUpdater.compareAndSet(this, null, new CountDownLatch(1));
    }
  }

  @Override
  public boolean isShutdown() {
    return _shutdown != null;
  }

  @Override
  public boolean isTerminated() {
    return isShutdown() && _shutdown.getCount() == 0;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    CountDownLatch latch = _shutdown;
    if (latch == null)
      throw new IllegalStateException("Not shutdown");
    return latch.await(timeout, unit);
  }

  /**
   *
   * @param <Resource>
   */
  public static class Element<Resource>
  {
    final AsyncPoolImpl<Resource> pool;
    final Resource resource;
    public final long createTimestamp;
    public final long expireTimestamp;
    long timestamp;

    private Element(AsyncPoolImpl<Resource> pool)
    {
      this.pool = pool;
      this.resource = null;
      this.createTimestamp = System.currentTimeMillis();
      this.expireTimestamp = 0;
    }

    private Element(AsyncPoolImpl<Resource> pool, Resource resource)
    {
      this.pool = pool;
      this.resource = resource;
      this.createTimestamp = System.currentTimeMillis();
      long expireMilliseconds = pool.getExpireMilliseconds();
      this.expireTimestamp = expireMilliseconds > 0 ? createTimestamp + expireMilliseconds : 0;
    }
  }
}
