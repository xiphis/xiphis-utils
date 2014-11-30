/*
 Copyright (c) 2014, Xiphis
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 Neither the name of the Xiphis nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.xiphis.utils.var;

import org.xiphis.utils.common.IntegerFormat;
import org.xiphis.utils.common.Setable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

/**
 * @author atcurtis
 * @since 2014-11-28
 */
public class VarAtomicInteger extends VarNumber<Integer, VarAtomicInteger>
{
  private final AtomicInteger _value;
  private final IntegerFormat _format;

  protected VarAtomicInteger(Builder builder)
  {
    super(builder);
    _value = new AtomicInteger(builder._value);
    _format = builder._format;
  }

  private static class VarSetable extends VarAtomicInteger implements Setable<Integer>
  {
    protected VarSetable(VarAtomicInteger.Builder builder)
    {
      super(builder);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Item extends VarItem> Item cast(VarItem existing)
  {
    if (existing.getClass() == getClass())
      return (Item) existing;
    throw new ClassCastException();
  }

  public static class Builder extends VarNumber.Builder<Builder, Integer, VarAtomicInteger>
  {
    private int _value;
    private IntegerFormat _format = IntegerFormat.SIGNED;
    private boolean _setable;

    private Builder(VarGroup base, String path)
    {
      super(base, path);
    }

    public Builder value(int value)
    {
      _value = value;
      return this;
    }

    public Builder setable()
    {
      _setable = true;
      return this;
    }

    public Builder signed()
    {
      _format = IntegerFormat.SIGNED;
      return this;
    }

    public Builder unsigned()
    {
      _format = IntegerFormat.UNSIGNED;
      return this;
    }

    public Builder bin()
    {
      _format = IntegerFormat.BINARY;
      return this;
    }

    public Builder hex()
    {
      _format = IntegerFormat.HEX;
      return this;
    }

    public Builder oct()
    {
      _format = IntegerFormat.OCTAL;
      return this;
    }

    @Override
    protected VarAtomicInteger buildVar()
    {
      return !_setable ? new VarAtomicInteger(this) : new VarSetable(this);
    }
  }

  public static Builder builder(String path)
  {
    return builder(VarGroup.ROOT, path);
  }

  public static Builder builder(VarGroup base, String path)
  {
    return new Builder(base, path);
  }

  public void checkRange(int value)
  {
    if ((_minValue != null && _minValue > value) ||
        (_maxValue != null && _maxValue < value))
      throw new IllegalArgumentException(String.format("minValue[%s] <= %d <= maxValue[%s]", _minValue, value, _maxValue));
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#getAndSet(int)
   */
  public final int getAndSet(int value)
  {
    checkRange(value);
    int oldValue = _value.getAndSet(value);
    if (oldValue != value)
      _attr.update(value);
    return oldValue;
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#set(int)
   */
  public final void set(int value)
  {
    getAndSet(value);
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#compareAndSet(int, int)
   */
  public final boolean compareAndSet(int expect, int value)
  {
    checkRange(value);
    if (_value.compareAndSet(expect, value))
    {
      _attr.update(value);
      return true;
    }
    return false;
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#getAndAdd(int)
   */
  public final int getAndAdd(int delta)
  {
    int oldValue, value;
    do
    {
      value = (oldValue = get()) + delta;
      checkRange(value);
    }
    while (!compareAndSet(oldValue, value));
    return oldValue;
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#getAndIncrement()
   */
  public final int getAndIncrement()
  {
    return getAndAdd(1);
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#incrementAndGet()
   */
  public final int incrementAndGet()
  {
    return getAndIncrement() + 1;
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#getAndDecrement()
   */
  public final int getAndDecrement()
  {
    return getAndAdd(-1);
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#decrementAndGet()
   */
  public final int decrementAndGet()
  {
    return getAndDecrement() - 1;
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#getAndUpdate(java.util.function.IntUnaryOperator)
   */
  public final int getAndUpdate(IntUnaryOperator updateFunction) {
    int prev, next;
    do {
      prev = get();
      next = updateFunction.applyAsInt(prev);
    } while (!compareAndSet(prev, next));
    return prev;
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#updateAndGet(java.util.function.IntUnaryOperator)
   */
  public final int updateAndGet(IntUnaryOperator updateFunction) {
    int prev, next;
    do {
      prev = get();
      next = updateFunction.applyAsInt(prev);
    } while (!compareAndSet(prev, next));
    return next;
  }

  public final int get()
  {
    return _value.get();
  }

  @Override
  public void setStringValue(String value)
  {
    set(_format.parseInt(value));
  }

  public void setValue(Integer value)
  {
    set(value);
  }

  @Override
  public Integer getValue()
  {
    return get();
  }

  protected final Number getNumber()
  {
    return _value;
  }

  @Override
  public String toString()
  {
    return _format.toString(get());
  }
}
