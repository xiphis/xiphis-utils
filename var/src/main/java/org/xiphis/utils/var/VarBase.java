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
public abstract class VarBase<Type, VarType extends VarBase<Type, VarType>> implements VarItem
{
  private long _lastModified;
  private long _lastReadTime;
  private VarListener<VarType> _listeners;
  private final boolean _readonly;

  protected VarBase(Builder<?, Type, VarType> builder)
  {
    _readonly = builder._readonly;
  }

  public boolean isReadOnly()
  {
    return _readonly;
  }

  @SuppressWarnings("unchecked")
  protected VarType self()
  {
    return (VarType) this;
  }

  public VarBase<?,?> asVarBase()
  {
    return this;
  }

  public abstract void setStringValue(String value);

  public void setValue(String value)
  {
    if (_readonly)
      throw new IllegalAccessError("read only");
    setStringValue(value);
  }

  private static class Listeners<VarType extends VarBase<?, VarType>>
      extends LinkedList<VarListener<VarType>> implements VarListener<VarType>
  {
    private final VarType _self;

    private Listeners(VarType self, VarListener<VarType> a, VarListener<VarType> b)
    {
      _self = self;
      add(a);
      add(b);
    }

    @Override
    public void onUpdate(VarType var)
    {
      forEach(listener -> listener.onUpdate(_self));
    }
  }

  public synchronized void addListener(VarListener<VarType> listener)
  {
    if (_listeners == null)
      _listeners = listener;
    else
    if (_listeners instanceof Listeners)
      ((Listeners<VarType>) _listeners).add(listener);
    else
      _listeners = new Listeners<VarType>(self(), _listeners, listener);
  }

  public synchronized void removeListener(VarListener<VarType> listener)
  {
    if (_listeners instanceof Listeners)
      ((Listeners) _listeners).remove(listener);
    else
    if (_listeners == listener)
      _listeners = null;
  }

  @SuppressWarnings("unchecked")
  protected void fireOnUpdate()
  {
    VarListener<VarType> listeners = _listeners;
    if (listeners != null)
      listeners.onUpdate((VarType) this);
  }

  protected void update()
  {
    long now = System.currentTimeMillis();
    if (now <= _lastReadTime)
      _lastModified = _lastReadTime + 1;
    else
      _lastModified = now;
    fireOnUpdate();
  }

  public abstract Type getValue();

  public synchronized Pair<Type, Long> getValueAndTimestamp()
  {
    _lastReadTime = System.currentTimeMillis();
    return Pair.make(getValue(), _lastModified);
  }

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
}
