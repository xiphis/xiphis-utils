package org.xiphis.utils.app;

import io.netty.util.concurrent.*;

import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * @author antony
 * @since 2014-12-13
 */
public interface EventExecutorModule<E extends EventExecutorGroup>
    extends ScheduledExecutorModule<E>, EventExecutorGroup {

  /***
   * {@inheritDoc}
   */
  @Override
  default <T> Future<T> submit(Callable<T> task) {
    return getExecutor().submit(task);
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default <T> Future<T> submit(Runnable task, T result) {
    return getExecutor().submit(task, result);
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default Future<?> submit(Runnable task) {
    return getExecutor().submit(task);
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return getExecutor().schedule(command, delay, unit);
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    return getExecutor().schedule(callable, delay, unit);
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
    return getExecutor().scheduleAtFixedRate(command, initialDelay, period, unit);
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return getExecutor().scheduleWithFixedDelay(command, initialDelay, delay, unit);
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default Future<?> shutdownGracefully() {
    return getExecutor().terminationFuture();
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default Future<?> shutdownGracefully(long l, long l1, TimeUnit timeUnit) {
    return getExecutor().terminationFuture();
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default Future<?> terminationFuture() {
    return getExecutor().terminationFuture();
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default EventExecutor next() {
    return getExecutor().next();
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default Iterator<EventExecutor> iterator() {
    return getExecutor().iterator();
  }
}
