/*
 Copyright (c) 2014, Xiphis
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 Neither the name of the Xiphis nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.xiphis.utils.common;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author atcurtis
 * @since 2014-11-30
 */
public final class Logger
{
  private final InternalLogger _logger;

  private static final ConcurrentHashMap<String, Logger> _map = new ConcurrentHashMap<>();

  @SuppressWarnings("unchecked")
  private Logger(InternalLogger internalLogger)
  {
    _logger = internalLogger;
  }

  public static Logger getInstance(Class<?> clazz)
  {
    return getInstance(clazz.getName());
  }

  public static Logger getInstance(String name)
  {
    Logger logger = _map.get(name);
    if (logger == null)
    {
      Logger newLogger = new Logger(InternalLoggerFactory.getInstance(name));
      logger = _map.putIfAbsent(name, newLogger);
      if (logger == null)
        logger = newLogger;
    }
    return logger;
  }

  public String name()
  {
    return _logger.name();
  }

  public boolean isTraceEnabled()
  {
    return _logger.isTraceEnabled();
  }

  public void trace(String paramString)
  {
    _logger.trace(paramString);
  }

  public void trace(String paramString, Object paramObject)
  {
    _logger.trace(paramString, paramObject);
  }

  public void trace(String paramString, Object paramObject1, Object paramObject2)
  {
    _logger.trace(paramString, paramObject1, paramObject2);
  }

  public void trace(String paramString, Object... paramVarArgs)
  {
    _logger.trace(paramString, paramVarArgs);
  }

  public void trace(String paramString, Throwable paramThrowable)
  {
    _logger.trace(paramString, paramThrowable);
  }

  public boolean isDebugEnabled()
  {
    return _logger.isDebugEnabled();
  }

  public void debug(String paramString)
  {
    _logger.debug(paramString);
  }

  public void debug(String paramString, Object paramObject)
  {
    _logger.debug(paramString, paramObject);
  }

  public void debug(String paramString, Object paramObject1, Object paramObject2)
  {
    _logger.debug(paramString, paramObject1, paramObject2);
  }

  public void debug(String paramString, Object... paramVarArgs)
  {
    _logger.debug(paramString, paramVarArgs);
  }

  public void debug(String paramString, Throwable paramThrowable)
  {
    _logger.debug(paramString, paramThrowable);
  }

  public boolean isInfoEnabled()
  {
    return _logger.isInfoEnabled();
  }

  public void info(String paramString)
  {
    _logger.info(paramString);
  }

  public void info(String paramString, Object paramObject)
  {
    _logger.info(paramString, paramObject);
  }

  public void info(String paramString, Object paramObject1, Object paramObject2)
  {
    _logger.info(paramString, paramObject1, paramObject2);
  }

  public void info(String paramString, Object... paramVarArgs)
  {
    _logger.info(paramString, paramVarArgs);
  }

  public void info(String paramString, Throwable paramThrowable)
  {
    _logger.info(paramString, paramThrowable);
  }

  public boolean isWarnEnabled()
  {
    return _logger.isWarnEnabled();
  }

  public void warn(String paramString)
  {
    _logger.warn(paramString);
  }

  public void warn(String paramString, Object paramObject)
  {
    _logger.warn(paramString, paramObject);
  }

  public void warn(String paramString, Object... paramVarArgs)
  {
    _logger.warn(paramString, paramVarArgs);
  }

  public void warn(String paramString, Object paramObject1, Object paramObject2)
  {
    _logger.warn(paramString, paramObject1, paramObject2);
  }

  public void warn(String paramString, Throwable paramThrowable)
  {
    _logger.warn(paramString, paramThrowable);
  }

  public boolean isErrorEnabled()
  {
    return _logger.isErrorEnabled();
  }

  public void error(String paramString)
  {
    _logger.error(paramString);
  }

  public void error(String paramString, Object paramObject)
  {
    _logger.error(paramString, paramObject);
  }

  public void error(String paramString, Object paramObject1, Object paramObject2)
  {
    _logger.error(paramString, paramObject1, paramObject2);
  }

  public void error(String paramString, Object... paramVarArgs)
  {
    _logger.error(paramString, paramVarArgs);
  }

  public void error(String paramString, Throwable paramThrowable)
  {
    _logger.error(paramString, paramThrowable);
  }

  public boolean isEnabled(LogLevel paramLogLevel)
  {
    return _logger.isEnabled(paramLogLevel.toInternalLevel());
  }

  public void log(LogLevel paramLogLevel, String paramString)
  {
    _logger.log(paramLogLevel.toInternalLevel(), paramString);
  }

  public void log(LogLevel paramLogLevel, String paramString, Object paramObject)
  {
    _logger.log(paramLogLevel.toInternalLevel(), paramString, paramObject);
  }

  public void log(LogLevel paramLogLevel, String paramString, Object paramObject1, Object paramObject2)
  {
    _logger.log(paramLogLevel.toInternalLevel(), paramString, paramObject1, paramObject2);
  }

  public void log(LogLevel paramLogLevel, String paramString, Object... paramVarArgs)
  {
    _logger.log(paramLogLevel.toInternalLevel(), paramString, paramVarArgs);
  }

  public void log(LogLevel paramLogLevel, String paramString, Throwable paramThrowable)
  {
    _logger.log(paramLogLevel.toInternalLevel(), paramString, paramThrowable);
  }

  public void setLevel(LogLevel level)
  {
    try
    {
      Object logger = Utils.getUnsafe()
                           .getObject(_logger,
                                      Utils.getUnsafe().objectFieldOffset(_logger.getClass().getField("logger")));
      Method method = logger.getClass().getMethod("setLevel");
      Class<? extends Enum> enumClass = method.getParameterTypes()[0].asSubclass(Enum.class);
      for (Enum e : enumClass.getEnumConstants())
      {
        if (level.name().equals(e.name()))
        {
          method.invoke(logger, e);
        }
      }
    }
    catch (Exception ignored)
    {
    }
    throw new UnsupportedOperationException();
  }

}
