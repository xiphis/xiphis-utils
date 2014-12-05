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

import java.util.LinkedList;

/**
 * @author atcurtis
 * @since 2014-11-17
 */
public interface VarBase<Type, VarType extends VarBase<Type, VarType>> extends VarItem
{
  default boolean isReadOnly()
  {
    return true;
  }

  @SuppressWarnings("unchecked")
  default VarType self()
  {
    return (VarType) this;
  }

  default VarBase<?,?> asVarBase()
  {
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  default  <Item extends VarItem> Item cast(VarItem existing)
  {
    if (existing.getClass() == getClass())
      return (Item) existing;
    throw new ClassCastException();
  }

  void setStringValue(String value);

  default void setValue(String value)
  {
    if (isReadOnly())
      throw new IllegalAccessError("read only");
    setStringValue(value);
  }

  void addListener(VarListener<Type, VarType> listener);

  void removeListener(VarListener<Type, VarType> listener);

  /**
   * Gets the current value. If the value type is a primative, the returned value
   * will be a boxed value.
   *
   * @return the current value
   */
  Type getValue();

  Pair<Type, Long> getValueAndTimestamp();

  public static abstract class Builder<B extends Builder<B, Type, VarType>, Type, VarType extends VarBase<Type, VarType>>
  {
    private final VarGroup _base;
    private final String _path;
    private boolean _readonly;

    protected Builder(VarGroup base, String path)
    {
      if (base == null || path == null)
        throw new NullPointerException();
      _base = base;
      _path = path;
    }

    @SuppressWarnings("unchecked")
    public final B self()
    {
      return (B) this;
    }

    public B readonly()
    {
      _readonly = true;
      return self();
    }

    public boolean isReadOnly()
    {
      return _readonly;
    }

    public VarType build()
    {
      VarType var = buildVar();
      Pair<VarGroup, CharSequence> pair = _base.parsePath(_path, true);
      return added(var, pair.first.addItem(pair.second.toString(), var));
    }

    private VarType added(VarType newVar, VarType var)
    {
      if (newVar == var)
        initVar(var);
      return var;
    }

    protected void initVar(VarType var)
    {
    }

    protected abstract VarType buildVar();
  }

  public static class Attributes<Type, VarType extends VarBase<Type, VarType>>
  {
    private final VarType _var;
    private final boolean _readonly;
    private long _lastModified;
    private long _lastReadTime;
    private VarListener<Type, VarType> _listeners;

    public Attributes(VarType var, boolean readonly)
    {
      _var = var;
      _readonly = readonly;
    }

    public boolean isReadOnly()
    {
      return _readonly;
    }

    public synchronized Pair<Type, Long> getValueAndTimestamp()
    {
      _lastReadTime = System.currentTimeMillis();
      return Pair.make(_var.getValue(), _lastModified);
    }


    private static class Listeners<Type, VarType extends VarBase<Type, VarType>>
        extends LinkedList<VarListener<Type, VarType>> implements VarListener<Type, VarType>
    {
      private Listeners(VarListener<Type, VarType> a, VarListener<Type, VarType> b)
      {
        add(a);
        add(b);
      }

      @Override
      public void onUpdate(VarType var, Type value)
      {
        forEach(listener -> listener.onUpdate(var, value));
      }
    }

    public synchronized void addListener(VarListener<Type, VarType> listener)
    {
      if (_listeners == null)
        _listeners = listener;
      else
      if (_listeners instanceof Listeners)
        ((Listeners<Type, VarType>) _listeners).add(listener);
      else
        _listeners = new Listeners<Type, VarType>(_listeners, listener);
    }

    public synchronized void removeListener(VarListener<Type, VarType> listener)
    {
      if (_listeners instanceof Listeners)
        ((Listeners) _listeners).remove(listener);
      else
      if (_listeners == listener)
        _listeners = null;
    }

    @SuppressWarnings("unchecked")
    public void fireOnUpdate(Type value)
    {
      VarListener<Type, VarType> listeners = _listeners;
      if (listeners != null)
        listeners.onUpdate((VarType) this, value);
    }

    public void update(Type value)
    {
      long now = System.currentTimeMillis();
      if (now <= _lastReadTime)
        _lastModified = _lastReadTime + 1;
      else
        _lastModified = now;
      fireOnUpdate(value);
    }
  }
}
