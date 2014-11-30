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

import java.util.concurrent.Callable;

/**
 * @author atcurtis
 * @since 2014-11-28
 */
public class VarFuncNumber<Type extends Number> implements VarBase<Type, VarFuncNumber<Type>>
{
  private final Callable<Type> _value;

  protected VarFuncNumber(Builder<Type> builder)
  {
    if (builder._value == null)
      throw new NullPointerException();
    _value = builder._value;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Item extends VarItem> Item cast(VarItem existing)
  {
    if (existing instanceof VarFuncNumber)
    {
      if (((VarFuncNumber) existing)._value.getClass() == _value.getClass())
        return (Item) existing;
    }
    throw new ClassCastException();
  }

  public static class Builder<Type extends Number> extends VarBase.Builder<Builder<Type>, Type, VarFuncNumber<Type>>
  {
    private Callable<Type> _value;

    private Builder(VarGroup base, String path, Callable<Type> value)
    {
      super(base, path);
      readonly();
      _value = value;
    }

    @Override
    protected VarFuncNumber<Type> buildVar()
    {
      return new VarFuncNumber<>(this);
    }
  }

  public static <Type extends Number> Builder<Type> builder(String path, Callable<Type> callable)
  {
    return builder(VarGroup.ROOT, path, callable);
  }

  public static <Type extends Number> Builder<Type> builder(VarGroup base, String path, Callable<Type> callable)
  {
    if (callable == null)
      throw new NullPointerException();
    return new Builder<>(base, path, callable);
  }

  public Type get()
  {
    try
    {
      return _value.call();
    }
    catch (Exception e)
    {
      return null;
    }
  }

  @Override
  public void setStringValue(String value)
  {
    throw new IllegalArgumentException("func");
  }

  @Override
  public void addListener(VarListener<Type, VarFuncNumber<Type>> listener)
  {
  }

  @Override
  public void removeListener(VarListener<Type, VarFuncNumber<Type>> listener)
  {
  }

  @Override
  public Type getValue()
  {
    return get();
  }

  @Override
  public Pair<Type, Long> getValueAndTimestamp()
  {
    return Pair.make(getValue(), System.currentTimeMillis());
  }

  @Override
  public String toString()
  {
    return String.valueOf(get());
  }

}
