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

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.Format;
import java.text.ParsePosition;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author atcurtis
 * @since 2014-11-20
 */
public final class Utils
{
  private Utils() { }

  private interface ValueOf<T>
  {
    T valueOf(CharSequence text)
        throws InstantiationException, InvocationTargetException,
               IllegalAccessException;
  }

  private static final sun.misc.Unsafe UNSAFE;

  private static final ConcurrentIdentityHashMap<Class<?>, ValueOf<?>> VALUEOF_MAP;

  private static final Properties PROPERTIES;

  public static final int PROCESSORS = Runtime.getRuntime().availableProcessors();

  public static String getBuildVersion()
  {
    return PROPERTIES.getProperty("build.version", "unknown");
  }

  public static String getBuildUser()
  {
    return PROPERTIES.getProperty("build.user", "unknown");
  }

  public static String getBuildGitCommit()
  {
    return PROPERTIES.getProperty("build.git-sha-1", "unknown");
  }

  public static LocalDateTime getBuildTime()
  {
    String timestamp = PROPERTIES.getProperty("build.time");
    if (timestamp != null)
      return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    else
      return null;
  }

  public static void Yield()
  {
    Thread.yield();
  }

  public static sun.misc.Unsafe getUnsafe()
  {
    return UNSAFE;
  }

  static void rethrow(Throwable ex)
  {
    PlatformDependent.throwException(ex);
  }

  private static char parseChar(CharSequence arg)
  {
    if (arg.length() != 1)
      throw new IllegalArgumentException("Expected only one character");
    return arg.charAt(0);
  }


  public static String getSimpleName(Class<?> type)
  {
    String name = type.getName();
    return name.substring(name.lastIndexOf(".")+1); // strip the package name
  }

  /**
   * Parse a string using the supplied {@link java.text.Format}.
   * @param format Format of text.
   * @param arg Text to be parsed.
   * @return Parsed data.
   */
  public static Object[] parseString(Format format, CharSequence arg)
  {
    ParsePosition pos = new ParsePosition(0);
    Object result = format.parseObject(arg.toString(), pos);
    if (pos.getIndex() < arg.length())
      throw new BadArgumentException("Could not pass whole argument");
    if (!result.getClass().isArray())
      return new Object[] { result };
    else
      return (Object[]) result;
  }

  public static CharSequence[] split(CharSequence source)
  {
    LinkedList<CharSequence> parts = new LinkedList<>();
    int length = source.length();
    int rstart = -1, rend = -1;
    for (int i = 0, start = 0; i <= length; i++)
    {
      char ch, end;
      if (i == length || (ch = source.charAt(i)) == ',')
      {
        if (rstart == start + 1 && rend == i - 1)
          parts.add(source.subSequence(rstart, rend));
        else
          parts.add(source.subSequence(start, i));
        start = i + 1; rstart = rend = -1;
        continue;
      }
      switch (end = ch)
      {
      case '\\':
        i++;
        continue;
      case '(':
        end = ')';
        break;
      case '{':
        end = '}';
        break;
      case '[':
        end = ']';
        break;
      case '"':
      case '\'':
        break;
      default:
        continue;
      }
      rstart = i+1;
      int depth = 1;
      while (++i < length)
      {
        if (source.charAt(i) == '\\')
        {
          i++;
          continue;
        }
        if (end != ch && source.charAt(i) == ch)
          depth++;
        else
        if (source.charAt(i) == end && --depth == 0)
          break;
      }
      rend = i;
    }
    return parts.toArray(new CharSequence[parts.size()]);
  }

  /**
   * Attempt to parse the supplied text and convert into the specified class.
   * Enums will be parsed using their {@code valueOf()} method.
   * If the class has a constructor which accepts a String, that would be used.
   * If there is a static method, named {@code parse}, that may be used.
   *
   * @param <T> Generic class type.
   * @param type Type of value.
   * @param arg Text to be parsed.
   * @return parsed value.
   */
  @SuppressWarnings("unchecked")
  public static <T> T parseString(Class<T> type, CharSequence arg)
  {
    if (type.isAssignableFrom(String.class))
      return (T) arg;

    if (type.isArray())
    {
      Class<?> componentType = type.getComponentType();
      CharSequence[] args = split(arg);
      Object result = Array.newInstance(componentType, args.length);
      for (int i = 0; i < args.length; i++)
        Array.set(result, i, parseString(componentType, args[i]));
      return (T) result;
    }

    ValueOf<T> valueOf = (ValueOf<T>) VALUEOF_MAP.get(type);
    try
    {
      if (valueOf != null)
        return valueOf.valueOf(arg);

      if (type.isEnum())
        valueOf = text -> (T) Enum.valueOf((Class) type, text.toString());

      for (Method method : type.getDeclaredMethods())
      {
        int modifier = method.getModifiers();
        if (!Modifier.isPublic(modifier) ||
            !Modifier.isStatic(modifier) ||
            !(method.getName().startsWith("parse") || method.getName().equals("valueOf")) ||
            method.getParameterCount() != 1 ||
            !type.isAssignableFrom(method.getReturnType()) ||
            !method.getParameterTypes()[0].isAssignableFrom(String.class))
          continue;

        if ("parse".equals(method.getName()) || "valueOf".equals(method.getName()) ||
            method.getName().startsWith("parse") && getSimpleName(type).startsWith(method.getName().substring(5)))
        {
          valueOf = text -> (T) method.invoke(null, text.toString());
          break;
        }
      }

      for (Constructor<T> constructor : (Constructor<T>[]) type.getConstructors())
      {
        int modifier = constructor.getModifiers();
        if (!Modifier.isPublic(modifier) ||
            constructor.getParameterCount() != 1 ||
            !constructor.getParameterTypes()[0].isAssignableFrom(String.class))
          continue;
        valueOf = text -> constructor.newInstance(text.toString());
        break;
      }

      if (valueOf != null)
      {
        T value = valueOf.valueOf(arg);
        VALUEOF_MAP.putIfAbsent(type, valueOf);
        return value;
      }
    }
    catch (IllegalAccessException e)
    {
      throw new ParserException(e);
    }
    catch (InstantiationException | InvocationTargetException e)
    {
      throw new ParserException(e.getCause());
    }

    throw new ParserException("Unable to parse type: " + type);
  }

  public static <V> Future<List<? extends V>> combineFutures(EventExecutor eventExecutor, List<Future<? extends V>> futuresToCombine)
  {
    ArrayList<Future<? extends V>> f = new ArrayList<>(futuresToCombine);
    if (f.isEmpty())
      return eventExecutor.newSucceededFuture(Collections.<V>emptyList());

    Promise<List<? extends V>> promise = eventExecutor.newPromise();

    if (f.size() == 1)
    {
      GenericFutureListener<Future<V>> listener = future -> {
        if (future.isSuccess())
          promise.setSuccess(Collections.singletonList(future.getNow()));
        else
          promise.setFailure(future.cause());
      };
      f.get(0).addListener(listener);
      return promise;
    }

    AtomicInteger _countDown = new AtomicInteger(f.size());
    ArrayList<V> completed = new ArrayList<>(f.size());
    GenericFutureListener<Future<V>> listener = future -> {
      if (future.isSuccess())
      {
        synchronized (completed)
        {
          completed.add(future.getNow());
        }
        if (_countDown.decrementAndGet() == 0)
        {
          promise.setSuccess(completed);
        }
      }
      else
      {
        promise.setFailure(future.cause());
      }
    };

    for (Future<? extends V> future : f)
      future.addListener(listener);

    return promise;
  }

  public static <T> T call(Callable<T> callable)
  {
    T result = null;
    try
    {
      result = callable.call();
    }
    catch (Exception e)
    {
      rethrow(e);
    }
    return result;
  }

  static
  {
    VALUEOF_MAP = new ConcurrentIdentityHashMap<>();
    VALUEOF_MAP.put(Byte.TYPE, text -> Byte.parseByte(text.toString()));
    VALUEOF_MAP.put(Short.TYPE, text -> Short.parseShort(text.toString()));
    VALUEOF_MAP.put(Integer.TYPE, text -> Integer.parseInt(text.toString()));
    VALUEOF_MAP.put(Long.TYPE, text -> Long.parseLong(text.toString()));
    VALUEOF_MAP.put(Character.TYPE, Utils::parseChar);
    VALUEOF_MAP.put(Double.TYPE, text -> Double.parseDouble(text.toString()));
    VALUEOF_MAP.put(Float.TYPE, text -> Float.parseFloat(text.toString()));
    VALUEOF_MAP.put(Boolean.TYPE, text -> Boolean.parseBoolean(text.toString()));

    try (InputStream in = Utils.class.getResourceAsStream("/org.xiphis.utils.properties"))
    {
      PROPERTIES = new Properties();
      PROPERTIES.load(in);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read properties file", e);
    }

    UNSAFE = call(() -> {
      Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      return (sun.misc.Unsafe) f.get(null);
    });
  }
}
