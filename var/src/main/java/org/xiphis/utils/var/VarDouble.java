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

import org.xiphis.utils.common.Setable;

/**
 * @author atcurtis
 * @since 2014-11-17
 */
public class VarDouble extends VarNumber<Double, VarDouble>
{
  private double _value;

  protected VarDouble(Builder builder)
  {
    super(builder);
    _value = builder._value;
  }

  private static class VarSetable extends VarDouble implements Setable<Double>
  {
    protected VarSetable(VarDouble.Builder builder)
    {
      super(builder);
    }
  }

  public static class Builder extends VarNumber.Builder<Builder, Double, VarDouble>
  {
    private double _value;
    private boolean _setable;

    private Builder(VarGroup base, String path)
    {
      super(base, path);
    }

    public Builder value(double value)
    {
      _value = value;
      return this;
    }

    public Builder setable()
    {
      _setable = true;
      return this;
    }

    @Override
    protected VarDouble buildVar()
    {
      return !_setable ? new VarDouble(this) : new VarSetable(this);
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

  public synchronized void set(double value)
  {
    if (_value == value)
      return;
    if ((_minValue != null && _minValue > value) ||
        (_maxValue != null && _maxValue < value))
      throw new IllegalArgumentException(String.format("minValue[%s] <= %f <= maxValue[%s]", _minValue, value, _maxValue));
    _value = value;
    _attr.update(value);
  }

  public double get()
  {
    return _value;
  }

  @Override
  public Double getValue()
  {
    return get();
  }

  @Override
  public void setStringValue(String value)
  {
    set(Double.parseDouble(value));
  }

  public void setValue(Double value)
  {
    set(value);
  }

  @Override
  public String toString()
  {
    return Double.toString(_value);
  }
}
