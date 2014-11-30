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
import org.xiphis.utils.common.Pair;
import org.xiphis.utils.common.Setable;

/**
 * @author atcurtis
 * @since 2014-11-17
 */
public class VarLong extends VarNumber<Long, VarLong>
{
  private long _value;
  private final IntegerFormat _format;

  protected VarLong(Builder builder)
  {
    super(builder);
    _value = builder._value;
    _format = builder._format;
  }

  private static class VarSettable extends VarLong implements Setable<Long>
  {
    protected VarSettable(VarLong.Builder builder)
    {
      super(builder);
    }
  }

  public static class Builder extends VarNumber.Builder<Builder, Long, VarLong>
  {
    private long _value;
    private IntegerFormat _format = IntegerFormat.SIGNED;
    private boolean _setable;

    private Builder(VarGroup base, String path)
    {
      super(base, path);
    }

    public Builder value(long value)
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
    protected VarLong buildVar()
    {
      return !_setable ? new VarLong(this) : new VarSettable(this);
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

  public synchronized void set(long value)
  {
    if (_value == value)
      return;
    if ((_minValue != null && _minValue > value) ||
        (_maxValue != null && _maxValue < value))
      throw new IllegalArgumentException(String.format("minValue[%s] <= %d <= maxValue[%s]", _minValue, value, _maxValue));
    _value = value;
    _attr.update(value);
  }

  public long get()
  {
    return _value;
  }

  @Override
  public Long getValue()
  {
    return get();
  }

  @Override
  public void setStringValue(String value)
  {
    set(_format.parseLong(value));
  }

  public void setValue(Long value)
  {
    set(value);
  }

  @Override
  public String toString()
  {
    return _format.toString(_value);
  }
}
