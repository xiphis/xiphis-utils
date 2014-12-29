package org.xiphis.utils.common;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

import java.util.Queue;

/**
 * @author atcurtis
 * @since 2014-12-23
 */
public interface AsyncQueue<E> extends Queue<E>
{

  int getWaiters();

  int failWaiters(Throwable cause);

  /**
   * Insert the specified element into the queue without violating
   * the queue's capacity constraints.
   *
   * @param executor Event executor
   * @param element Element executor.
   * @return Future which is called when an offer is taken.
   */
  Future<?> offer(EventExecutor executor, E element);

  /**
   * Take an element from the queue.
   *
   * @param executor Event executor.
   * @return Future for taking the element.
   */
  Future<E> take(EventExecutor executor);
}
