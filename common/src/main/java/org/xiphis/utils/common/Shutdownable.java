package org.xiphis.utils.common;

import java.util.concurrent.TimeUnit;

/**
 * @author antony
 * @since 2015-01-14
 */
public interface Shutdownable {

  void shutdown();

  /**
   * Returns {@code true} if this object has been shut down.
   *
   * @return {@code true} if this object has been shut down
   */
  boolean isShutdown();

  boolean isTerminated();

  boolean awaitTermination(long timeout, TimeUnit unit)
      throws InterruptedException;

}
