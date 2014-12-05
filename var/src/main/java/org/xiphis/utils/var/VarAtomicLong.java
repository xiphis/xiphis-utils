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
   * Atomically sets to the given value and returns the old value.
   *
   * @param newValue the new value
   * @return the previous value
   *
   * @see java.util.concurrent.atomic.AtomicLong#getAndSet(long)
   */
  public final long getAndSet(long newValue)
  {
    checkRange(newValue);
    long oldValue = _value.getAndSet(newValue);
    if (oldValue != newValue)
      _attr.update(newValue);
    return oldValue;
  }

  /**
   * Sets to the given value.
   *
   * @param newValue the new value
   *
   * @see java.util.concurrent.atomic.AtomicLong#set(long)
   */
  public final void set(long newValue)
  {
    getAndSet(newValue);
  }

  /**
   * Atomically sets the value to the given updated value
   * if the current value {@code ==} the expected value.
   *
   * @param expect the expected value
   * @param update the new value
   * @return {@code true} if successful. False return indicates that
   * the actual value was not equal to the expected value.
   *
   * @see java.util.concurrent.atomic.AtomicLong#compareAndSet(long, long)
   */
  public final boolean compareAndSet(long expect, long update)
  {
    checkRange(update);
    if (_value.compareAndSet(expect, update))
    {
      _attr.update(update);
      return true;
    }
    return false;
  }

  /**
   * Atomically adds the given value to the current value.
   *
   * @param delta the value to add
   * @return the previous value
   *
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
   * Atomically increments by one the current value.
   *
   * @return the previous value
   *
   * @see java.util.concurrent.atomic.AtomicInteger#getAndIncrement()
   */
  public final long getAndIncrement()
  {
    return getAndAdd(1L);
  }

  /**
   * Atomically increments by one the current value.
   *
   * @return the updated value
   *
   * @see java.util.concurrent.atomic.AtomicInteger#incrementAndGet()
   */
  public final long incrementAndGet()
  {
    return getAndIncrement() + 1L;
  }

  /**
   * Atomically decrements by one the current value.
   *
   * @return the previous value
   *
   * @see java.util.concurrent.atomic.AtomicInteger#getAndDecrement()
   */
  public final long getAndDecrement()
  {
    return getAndAdd(-1L);
  }

  /**
   * Atomically decrements by one the current value.
   *
   * @return the updated value
   *
   * @see java.util.concurrent.atomic.AtomicInteger#decrementAndGet()
   */
  public final long decrementAndGet()
  {
    return getAndDecrement() - 1L;
  }

  /**
   * Atomically updates the current value with the results of
   * applying the given function, returning the previous value. The
   * function should be side-effect-free, since it may be re-applied
   * when attempted updates fail due to contention among threads.
   *
   * @param updateFunction a side-effect-free function
   * @return the previous value
   *
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
   * Atomically updates the current value with the results of
   * applying the given function, returning the updated value. The
   * function should be side-effect-free, since it may be re-applied
   * when attempted updates fail due to contention among threads.
   *
   * @param updateFunction a side-effect-free function
   * @return the updated value
   *
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

  /**
   * Gets the current value.
   *
   * @return the current value
   */
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
