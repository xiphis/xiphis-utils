package org.xiphis.utils.app;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author antony
 * @since 2014-12-13
 */
public interface ExecutorModule<E extends ExecutorService>
    extends Module, ExecutorService {

  E getExecutor();


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
  default <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return getExecutor().invokeAll(tasks);
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
    return getExecutor().invokeAll(tasks, timeout, unit);
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return getExecutor().invokeAny(tasks);
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return getExecutor().invokeAny(tasks, timeout, unit);
  }

  /***
   * {@inheritDoc}
   */
  @Override
  default void execute(Runnable command) {
    getExecutor().execute(command);
  }
}
