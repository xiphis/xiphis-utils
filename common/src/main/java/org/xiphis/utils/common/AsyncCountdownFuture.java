package org.xiphis.utils.common;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author antony
 * @since 2015-01-12
 */
public final class AsyncCountdownFuture<V> implements RunnableFuture<V>, Future<V>
{
  private final Future<V> _future;
  private final V _value;
  private volatile int _countdown;

  private static final AtomicIntegerFieldUpdater<AsyncCountdownFuture> _countdownUpdater =
      AtomicIntegerFieldUpdater.newUpdater(AsyncCountdownFuture.class, "_countdown");

  public AsyncCountdownFuture(int countdown, V value) {
    this(GlobalEventExecutor.INSTANCE, countdown, value);
  }

  public AsyncCountdownFuture(EventExecutor eventExecutors, int countdown, V value) {
    if (countdown < 0)
      throw new IllegalArgumentException();
    if (countdown > 0)
    {
      _future = eventExecutors.newPromise();
      _value = value;
      _countdown = countdown;
    }
    else
    {
      _future = eventExecutors.newSucceededFuture(value);
      _value = null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSuccess() {
    return _future.isSuccess();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isCancellable() {
    return _future.isCancellable();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Throwable cause() {
    return _future.cause();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<V> addListener(GenericFutureListener<? extends Future<? super V>> genericFutureListener) {
    _future.addListener(genericFutureListener);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... genericFutureListeners) {
    _future.addListeners(genericFutureListeners);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> genericFutureListener) {
    _future.removeListener(genericFutureListener);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... genericFutureListeners) {
    _future.removeListeners(genericFutureListeners);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<V> sync() throws InterruptedException {
    _future.sync();
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<V> syncUninterruptibly() {
    _future.syncUninterruptibly();
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<V> await() throws InterruptedException {
    _future.await();
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<V> awaitUninterruptibly() {
    _future.awaitUninterruptibly();
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean await(long l, TimeUnit timeUnit) throws InterruptedException {
    return _future.await(l, timeUnit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean await(long l) throws InterruptedException {
    return _future.await(l);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean awaitUninterruptibly(long l, TimeUnit timeUnit) {
    return _future.awaitUninterruptibly(l, timeUnit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean awaitUninterruptibly(long l) {
    return _future.awaitUninterruptibly(l);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public V getNow() {
    return _future.getNow();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void run() {
    int count = _countdownUpdater.decrementAndGet(this);
    if (count < 0)
      throw new IllegalStateException();
    if (count == 0)
      ((Promise<V>) _future).setSuccess(_value);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return _future.cancel(mayInterruptIfRunning);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isCancelled() {
    return _future.isCancelled();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isDone() {
    return _future.isDone();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public V get() throws InterruptedException, ExecutionException {
    return _future.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return _future.get(timeout, unit);
  }
}
