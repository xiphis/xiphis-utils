package org.xiphis.utils.common;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author atcurtis
 * @since 2014-12-25
 */
public final class EmptyPromise<E> implements Promise<E>
{
  private volatile Pair<E, Throwable> _value;
  private final CountDownLatch _latch;

  public EmptyPromise()
  {
    _latch = new CountDownLatch(1);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Promise<E> setSuccess(E e)
  {
    if (trySuccess(e))
      return this;
    throw new IllegalStateException("complete already: " + this);
  }

  private synchronized boolean trySet(E value, Throwable throwable)
  {
    if (_value == null)
    {
      _value = Pair.make(value, throwable);
      _latch.countDown();
      return true;
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean trySuccess(E e)
  {
    return trySet(e, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Promise<E> setFailure(Throwable throwable)
  {
    if (tryFailure(throwable))
      return this;
    throw new IllegalStateException("complete already: " + this, throwable);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean tryFailure(Throwable throwable)
  {
    return throwable != null && trySet(null, throwable);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean setUncancellable()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSuccess()
  {
    Pair<E, Throwable> value = _value;
    return value != null && value.second == null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isCancellable()
  {
    return _value == null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Throwable cause()
  {
    Pair<E, Throwable> value = _value;
    return value != null ? value.second : null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Promise<E> addListener(GenericFutureListener<? extends Future<? super E>> genericFutureListener)
  {
    throw new IllegalStateException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Promise<E> addListeners(GenericFutureListener<? extends Future<? super E>>... genericFutureListeners)
  {
    throw new IllegalStateException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Promise<E> removeListener(GenericFutureListener<? extends Future<? super E>> genericFutureListener)
  {
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Promise<E> removeListeners(GenericFutureListener<? extends Future<? super E>>... genericFutureListeners)
  {
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Promise<E> await()
      throws InterruptedException
  {
    _latch.await();
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Promise<E> awaitUninterruptibly()
  {
    for (;;)
    {
      try
      {
        return await();
      }
      catch (InterruptedException ignored)
      {
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean await(long l, TimeUnit timeUnit)
      throws InterruptedException
  {
    return _latch.await(l, timeUnit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean await(long l)
      throws InterruptedException
  {
    return await(l, TimeUnit.MILLISECONDS);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean awaitUninterruptibly(long l, TimeUnit timeUnit)
  {
    long timeout = System.nanoTime() + timeUnit.toNanos(l);
    for (;;)
    {
      try
      {
        return await(timeout - System.nanoTime(), TimeUnit.NANOSECONDS);
      }
      catch (InterruptedException ignored)
      {
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean awaitUninterruptibly(long l)
  {
    return awaitUninterruptibly(l, TimeUnit.MILLISECONDS);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E getNow()
  {
    Pair<E, Throwable> value = _value;
    return value != null ? value.first : null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean cancel(boolean b)
  {
    return trySet(null, new CancellationException());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isCancelled()
  {
    return cause() instanceof CancellationException;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isDone()
  {
    return _value != null;
  }

  private E get0()
      throws ExecutionException
  {
    Pair<E, Throwable> pair = _value;
    assert pair != null;
    if (pair.second != null)
      throw new ExecutionException(pair.second);
    else
      return pair.first;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E get()
      throws InterruptedException, ExecutionException
  {
    await();
    return get0();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException
  {
    if (await(timeout, unit))
      return get0();
    throw new TimeoutException();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Promise<E> sync()
      throws InterruptedException
  {
    await();
    rethrowIfFailed();
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Promise<E> syncUninterruptibly()
  {
    awaitUninterruptibly();
    rethrowIfFailed();
    return this;
  }

  private void rethrowIfFailed()
  {
    Throwable cause = cause();
    if (cause == null) {
      return;
    }
    PlatformDependent.throwException(cause);
  }
}
