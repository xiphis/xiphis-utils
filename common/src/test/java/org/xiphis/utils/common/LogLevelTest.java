package org.xiphis.utils.common;

import io.netty.util.internal.logging.InternalLogLevel;
import org.junit.Assert;
import org.junit.Test;


public class LogLevelTest {

  @Test
  public void testToInternalLevel() throws Exception {
    Assert.assertSame(InternalLogLevel.TRACE, LogLevel.TRACE.toInternalLevel());
    Assert.assertSame(InternalLogLevel.DEBUG, LogLevel.DEBUG.toInternalLevel());
    Assert.assertSame(InternalLogLevel.INFO, LogLevel.INFO.toInternalLevel());
    Assert.assertSame(InternalLogLevel.WARN, LogLevel.WARN.toInternalLevel());
    Assert.assertSame(InternalLogLevel.ERROR, LogLevel.ERROR.toInternalLevel());
  }
}