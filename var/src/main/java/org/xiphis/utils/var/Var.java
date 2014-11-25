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
import org.xiphis.utils.common.Utils;

import java.text.Format;
import java.text.MessageFormat;

/**
 * @author atcurtis
 * @since 2014-11-17
 */
public class Var<Type> extends VarBase<Type, Var<Type>>
{
  private final Class<Type> _type;
  private final Format _format;
  private Type _value;

  protected Var(Builder<Type> builder)
  {
    super(builder);
    _type = builder._type;
    _format = builder._format;
    _value = builder._value;
  }

  private static class VarSetable<Type> extends Var<Type> implements Setable<Type>
  {
    protected VarSetable(Var.Builder<Type> builder)
    {
      super(builder);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Item extends VarItem> Item cast(VarItem existing)
  {
    if (existing instanceof Var)
    {
      if (((Var) existing)._type.equals(_type))
        return (Item) existing;
    }
    throw new ClassCastException();
  }

  public static class Builder<Type> extends VarBase.Builder<Builder<Type>, Type, Var<Type>>
  {
    private final Class<Type> _type;
    private Format _format;
    private Type _value;
    private boolean _setable;

    private Builder(VarGroup base, String path, Class<Type> type)
    {
      super(base, path);
      _type = type;
    }

    public Builder<Type> value(Type value)
    {
      _value = value;
      return this;
    }

    private Builder<Type> format(Format format)
    {
      _format = format;
      return this;
    }

    private Builder<Type> messageFormat(String format)
    {
      return format(new MessageFormat(format));
    }

    public Builder<Type> setable()
    {
      _setable = true;
      return this;
    }

    @Override
    protected Var<Type> buildVar()
    {
      return !_setable ? new Var<>(this) : new VarSetable<>(this);
    }
  }

  public static <Type> Builder builder(String path, Class<Type> type)
  {
    return builder(VarGroup.ROOT, path, type);
  }

  public static <Type> Builder builder(VarGroup base, String path, Class<Type> type)
  {
    return new Builder<>(base, path, type);
  }


  public synchronized void set(Type value)
  {
    if (_value == value)
      return;
    _value = value;
    update();
  }

  public Type get()
  {
    return _value;
  }

  public void setValue(Type value)
  {
    set(value);
  }

  @Override
  public void setStringValue(String value)
  {
    if (_format == null)
    {
      set(Utils.parseString(_type, value));
    }
    else
    {
      set(_type.cast(Utils.parseString(_format, value)[0]));
    }
  }

  @Override
  public Type getValue()
  {
    return get();
  }

  @Override
  public String toString()
  {
    return String.valueOf(get());
  }
}
