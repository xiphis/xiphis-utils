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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.Format;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author atcurtis
 * @since 2014-11-20
 */
public final class Utils
{
  private Utils() { }

  private static final sun.misc.Unsafe UNSAFE;
  private static final Map<Class<?>, Filter<?,String>> PRIMATIVE_MAP;

  public static final int PROCESSORS = Runtime.getRuntime().availableProcessors();

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
    UNSAFE.throwException(ex);
  }

  private static char parseChar(String arg)
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
  public static Object[] parseString(Format format, String arg)
  {
    ParsePosition pos = new ParsePosition(0);
    Object result = format.parseObject(arg, pos);
    if (pos.getIndex() < arg.length())
      throw new BadArgumentException("Could not pass whole argument");
    if (!result.getClass().isArray())
      return new Object[] { result };
    else
      return (Object[]) result;
  }

  /**
   * Attempt to parse the supplied text and convert into the specified class.
   * Enums will be parsed using their {@code valueOf()} method.
   * If the class has a constructor which accepts a String, that would be used.
   * If there is a static method, named {@code parse}, that may be used.
   *
   * @param type Type of value.
   * @param arg Text to be parsed.
   * @return parsed value.
   */
  @SuppressWarnings("unchecked")
  public static <T> T parseString(Class<T> type, String arg)
  {
    if (type.isAssignableFrom(String.class))
      return (T) arg;

    if (type.isPrimitive())
    {
      Filter parser = PRIMATIVE_MAP.get(type);
      return (T) parser.call(arg);
    }

    if (type.isArray())
    {
      Class<?> componentType = type.getComponentType();
      String[] args = arg.split(",");
      Object result = Array.newInstance(componentType, args.length);
      for (int i = 0; i < args.length; i++)
        Array.set(result, i, parseString(componentType, args[i]));
      return (T) result;
    }

    if (type.isEnum())
    {
      try
      {
        Method method = type.getMethod("valueOf", String.class);
        int modifier = method.getModifiers();
        if (Modifier.isPublic(modifier) && Modifier.isStatic(modifier))
        {
          return (T) method.invoke(null, arg);
        }
      }
      catch (NoSuchMethodException | IllegalAccessException e)
      {
        throw new ParserException(e);
      }
      catch (InvocationTargetException e)
      {
        throw new BadArgumentException(e.getCause());
      }
    }

    for (Method method : type.getDeclaredMethods())
    {
      int modifier = method.getModifiers();
      if (!Modifier.isPublic(modifier) ||
          !Modifier.isStatic(modifier) ||
          !method.getName().startsWith("parse") ||
          method.getParameterCount() != 1 ||
          !type.isAssignableFrom(method.getReturnType()) ||
          !method.getParameterTypes()[0].isAssignableFrom(String.class))
        continue;

      if ("parse".equals(method.getName()) || getSimpleName(type).startsWith(method.getName().substring(5)))
      {
        try
        {
          System.out.println(method.getName());
          return (T) method.invoke(null, arg);
        }
        catch (IllegalAccessException e)
        {
          throw new ParserException(e);
        }
        catch (InvocationTargetException e)
        {
          throw new BadArgumentException(e.getCause());
        }
      }
    }

    try
    {
      Constructor<T> constructor = type.getConstructor(String.class);
      int modifier = constructor.getModifiers();
      if (Modifier.isPublic(modifier))
      {
        try
        {
          return constructor.newInstance(arg);
        }
        catch (InstantiationException | InvocationTargetException e)
        {
          throw new BadArgumentException(e.getCause());
        }
        catch (IllegalAccessException e)
        {
          throw new ParserException(e);
        }
      }
    }
    catch (NoSuchMethodException ignored) { }

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

  static
  {
    Map<Class<?>, Filter<?,String>> map = new IdentityHashMap<>();
    map.put(Byte.TYPE, Byte::parseByte);
    map.put(Short.TYPE, Short::parseShort);
    map.put(Integer.TYPE, Integer::parseInt);
    map.put(Long.TYPE, Long::parseLong);
    map.put(Character.TYPE, Utils::parseChar);
    map.put(Double.TYPE, Double::parseDouble);
    map.put(Float.TYPE, Float::parseFloat);
    map.put(Boolean.TYPE, Boolean::parseBoolean);
    PRIMATIVE_MAP = Collections.unmodifiableMap(map);

    try
    {
      Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      UNSAFE = (sun.misc.Unsafe) f.get(null);
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }
}
