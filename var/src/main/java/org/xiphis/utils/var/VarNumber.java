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

import org.xiphis.utils.common.Pair;

/**
 * @author atcurtis
 * @since 2014-11-17
 */
public abstract class VarNumber<Type extends Number, VarType extends VarNumber<Type, VarType>>
    extends Number implements VarBase<Type, VarType>
{
  protected final Attributes<Type, VarType> _attr;
  protected final Type _minValue;
  protected final Type _maxValue;

  protected VarNumber(Builder<?, Type, VarType> builder)
  {
    _attr = new Attributes<Type, VarType>(self(), builder.isReadOnly());
    _minValue = builder._minValue;
    _maxValue = builder._maxValue;
  }

  public static abstract class Builder<B extends Builder<B, Type,VarType>, Type extends Number, VarType extends VarBase<Type,VarType>>
      extends VarBase.Builder<B, Type, VarType>
  {
    private Type _minValue;
    private Type _maxValue;

    protected Builder(VarGroup base, String path)
    {
      super(base, path);
    }

    public B minValue(Type minValue)
    {
      _minValue = minValue;
      return self();
    }

    public B maxValue(Type maxValue)
    {
      _maxValue = maxValue;
      return self();
    }
  }

  @Override
  public Pair<Type, Long> getValueAndTimestamp()
  {
    return _attr.getValueAndTimestamp();
  }

  @Override
  public boolean isReadOnly()
  {
    return _attr.isReadOnly();
  }

  @Override
  public void addListener(VarListener<Type, VarType> listener)
  {
    _attr.addListener(listener);
  }

  @Override
  public void removeListener(VarListener<Type, VarType> listener)
  {
    _attr.removeListener(listener);
  }

  protected Number getNumber()
  {
    return getValue();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int intValue()
  {
    return getNumber().intValue();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long longValue()
  {
    return getNumber().longValue();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public float floatValue()
  {
    return getNumber().floatValue();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double doubleValue()
  {
    return getNumber().doubleValue();
  }
}
