package org.xiphis.utils.common;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.Iterator;

/**
 * @author atcurtis
 * @since 2014-12-25
 */
public class AsyncLinkedQueue<E> extends AbstractAsyncQueue<E>
{
  private final ConcurrentDoublyLinkedList<ImmutablePair<E, Promise<Void>>> _queue;
  private final ConcurrentDoublyLinkedList<Promise<E>> _waiters;

  public AsyncLinkedQueue()
  {
    _queue = new ConcurrentDoublyLinkedList<>();
    _waiters = new ConcurrentDoublyLinkedList<>();
  }

  public int getWaiters()
  {
    return _waiters.size();
  }

  public int failWaiters(Throwable cause)
  {
    Promise<E> promise;
    int count = 0;
    while ((promise = _waiters.poll()) != null)
      if (promise.tryFailure(cause))
        count++;
    return count;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Iterator<E> iterator()
  {
    return new Iterator<E>()
    {
      final Iterator<ImmutablePair<E, Promise<Void>>> _it = _queue.iterator();

      @Override
      public boolean hasNext()
      {
        return _it.hasNext();
      }

      @Override
      public E next()
      {
        return _it.next().first;
      }
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int size()
  {
    return _queue.size();
  }

  private boolean flush()
  {
    ConcurrentDoublyLinkedNode<ImmutablePair<E, Promise<Void>>> node;
    while ((node = _queue.peekFirstNode()) != null)
    {
      ImmutablePair<E, Promise<Void>> pair = node.getElement();
      if (pair != null && pair.second.isDone())
      {
        node.delete();
        continue;
      }
      return false;
    }
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<?> offer(EventExecutor executor, E element)
  {
    if (!_waiters.isEmpty())
    {
      Promise<E> promise;
      while (flush() && (promise = _waiters.poll()) != null)
      {
        if (promise.trySuccess(element))
          return executor.newSucceededFuture(null);
      }
    }

    Promise<Void> promise = executor.newPromise();
    if (_queue.offer(ImmutablePair.make(element, promise)))
    {
      if (!_waiters.isEmpty())
        executor.execute(this::test);
      return promise;
    }
    else
      return executor.newFailedFuture(new IllegalStateException());
  }

  private void test()
  {
    for (;;)
    {
      ConcurrentDoublyLinkedNode<Promise<E>> node = _waiters.peekFirstNode();
      if (node == null)
        return;
      if (node.isDeleted())
        continue;
      ConcurrentDoublyLinkedNode<ImmutablePair<E, Promise<Void>>> pairNode = _queue.peekFirstNode();
      final ImmutablePair<E, Promise<Void>> pair;
      if (pairNode == null)
        return;
      if ((pair = pairNode.getElement()) == null)
        continue;

      synchronized (pair.second)
      {
        if (!pair.second.isDone() && node.delete())
        {
          if (node.getElement().trySuccess(pair.first))
          {
            pair.second.setSuccess(null);
            pairNode.delete();
          }
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<E> take(EventExecutor executor)
  {
    for (;;)
    {
      ImmutablePair<E, Promise<Void>> pair = _queue.poll();
      if (pair != null && pair.second.trySuccess(null))
        return executor.newSucceededFuture(pair.first);
      if (pair != null)
        continue;
      Promise<E> promise = executor.newPromise();
      if (_waiters.offer(promise))
        return promise;
      else
        return executor.newFailedFuture(new IllegalStateException());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean offer(E e)
  {
    Promise<E> promise;
    while (_queue.isEmpty() && (promise = _waiters.poll()) != null)
      if (promise.trySuccess(e))
        return true;
    return _queue.offer(ImmutablePair.make(e, new EmptyPromise<>()));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E poll()
  {
    for (;;)
    {
      ImmutablePair<E, Promise<Void>> pair = _queue.poll();
      if (pair == null)
        return null;
      if (pair.second.trySuccess(null))
        return pair.first;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public E peek()
  {
    for (;;)
    {
      ConcurrentDoublyLinkedNode<ImmutablePair<E, Promise<Void>>> node = _queue.peekFirstNode();
      if (node == null) return null;
      ImmutablePair<E, Promise<Void>> pair = node.getElement();
      if (pair != null)
      {
        if (pair.second.isDone()) node.delete();
        else return pair.first;
      }
    }
  }
}
