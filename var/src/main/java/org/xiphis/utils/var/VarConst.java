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

/**
 * @author atcurtis
 * @since 2014-11-20
 */
public class VarConst<Type> extends VarBase<Type, VarConst<Type>>
{
  private final Type _value;

  protected VarConst(Builder<Type> builder)
  {
    super(builder);
    _value = builder._value;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Item extends VarItem> Item cast(VarItem existing)
  {
    if (existing instanceof VarConst)
    {
      if (((VarConst) existing)._value.equals(_value))
        return (Item) existing;
    }
    throw new ClassCastException();
  }

  public static class Builder<Type> extends VarBase.Builder<Builder<Type>, Type, VarConst<Type>>
  {
    private final Type _value;

    private Builder(VarGroup base, String path, Type value)
    {
      super(base, path);
      _value = value;
      readonly();
    }

    @Override
    public VarConst<Type> build()
    {
      return super.build();
    }

    @Override
    protected VarConst<Type> buildVar()
    {
      return new VarConst<>(this);
    }
  }

  public static <Type> Builder builder(String path, Type value)
  {
    return builder(VarGroup.ROOT, path, value);
  }

  public static <Type> Builder builder(VarGroup base, String path, Type value)
  {
    return new Builder<>(base, path, value);
  }

  public Type get()
  {
    return _value;
  }

  @Override
  public void setStringValue(String value)
  {
    throw new IllegalArgumentException("const");
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
