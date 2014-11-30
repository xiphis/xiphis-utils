package org.xiphis.utils.var;

import org.xiphis.utils.common.IntegerFormat;
import org.xiphis.utils.common.Setable;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

/**
 * @author atcurtis
 * @since 2014-11-28
 */
public class VarAtomicLong extends VarNumber<Long, VarAtomicLong>
{
  private final AtomicLong _value;
  private final IntegerFormat _format;

  protected VarAtomicLong(Builder builder)
  {
    super(builder);
    _value = new AtomicLong(builder._value);
    _format = builder._format;
  }

  private static class VarSettable extends VarAtomicLong implements Setable<Long>
  {
    protected VarSettable(VarAtomicLong.Builder builder)
    {
      super(builder);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Item extends VarItem> Item cast(VarItem existing)
  {
    if (existing instanceof VarAtomicLong)
      return (Item) existing;
    throw new ClassCastException();
  }

  public static class Builder extends VarNumber.Builder<Builder, Long, VarAtomicLong>
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
    protected VarAtomicLong buildVar()
    {
      return !_setable ? new VarAtomicLong(this) : new VarSettable(this);
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

  public void checkRange(long value)
  {
    if ((_minValue != null && _minValue > value) ||
        (_maxValue != null && _maxValue < value))
      throw new IllegalArgumentException(String.format("minValue[%s] <= %d <= maxValue[%s]", _minValue, value, _maxValue));
  }

  /**
   * @see java.util.concurrent.atomic.AtomicLong#getAndSet(long)
   */
  public final long getAndSet(long value)
  {
    checkRange(value);
    long oldValue = _value.getAndSet(value);
    if (oldValue != value)
      _attr.update(value);
    return oldValue;
  }

  /**
   * @see java.util.concurrent.atomic.AtomicLong#set(long)
   */
  public final void set(long value)
  {
    getAndSet(value);
  }

  /**
   * @see java.util.concurrent.atomic.AtomicLong#compareAndSet(long, long)
   */
  public final boolean compareAndSet(long expect, long value)
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
   * @see java.util.concurrent.atomic.AtomicLong#getAndAdd(long)
   */
  public final long getAndAdd(long delta)
  {
    long oldValue, value;
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
  public final long getAndIncrement()
  {
    return getAndAdd(1);
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#incrementAndGet()
   */
  public final long incrementAndGet()
  {
    return getAndIncrement() + 1;
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#getAndDecrement()
   */
  public final long getAndDecrement()
  {
    return getAndAdd(-1);
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#decrementAndGet()
   */
  public final long decrementAndGet()
  {
    return getAndDecrement() - 1;
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#getAndUpdate(java.util.function.IntUnaryOperator)
   */
  public final long getAndUpdate(LongUnaryOperator updateFunction) {
    long prev, next;
    do {
      prev = get();
      next = updateFunction.applyAsLong(prev);
    } while (!compareAndSet(prev, next));
    return prev;
  }

  /**
   * @see java.util.concurrent.atomic.AtomicInteger#updateAndGet(java.util.function.IntUnaryOperator)
   */
  public final long updateAndGet(LongUnaryOperator updateFunction) {
    long prev, next;
    do {
      prev = get();
      next = updateFunction.applyAsLong(prev);
    } while (!compareAndSet(prev, next));
    return next;
  }

  public final long get()
  {
    return _value.get();
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
