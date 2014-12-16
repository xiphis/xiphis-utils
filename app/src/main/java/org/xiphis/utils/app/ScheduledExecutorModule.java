package org.xiphis.utils.app;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author antony
 * @since 2014-12-13
 */
public interface ScheduledExecutorModule<E extends ScheduledExecutorService>
    extends ExecutorModule<E>, ScheduledExecutorService {
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
}
