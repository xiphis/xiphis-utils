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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author atcurtis
 * @since 2014-11-28
 */
public class VarAverage extends Number implements VarBase<Double, VarAverage>
{
  private final Attributes<Double, VarAverage> _attr;
  private final Bucket _buckets[];
  private int _head, _tail;
  private final Lock _lock;
  private final ConcurrentLinkedQueue<Double> _queue;
  private final long _duration;
  private final TimeUnit _unit;
  private final long _divisorMillis;
  private double _rollingAverage;
  private int _rollingCount;

  private VarAverage(Builder builder)
  {
    if (builder._duration <= 0 || builder._unit == null)
      throw new IllegalArgumentException();
    _attr = new Attributes<Double, VarAverage>(this, true);
    _duration = builder._duration;
    _unit = builder._unit;
    _lock = new ReentrantLock();
    _queue = new ConcurrentLinkedQueue<>();

    _buckets = new Bucket[builder._buckets];
    for (int i = 0; i < _buckets.length; i++)
      _buckets[i] = new Bucket();

    _divisorMillis = Math.max(1L, _unit.toMillis(_duration) / _buckets.length);

    if (builder._source != null)
    {
      builder._source.addListener((var, value) -> {
        set(value.doubleValue());
      });
    }
  }

  public static class Builder
      extends VarBase.Builder<Builder, Double, VarAverage>
  {
    private VarBase<Number,?> _source;
    private long _duration;
    private TimeUnit _unit;
    private int _buckets = 100;

    protected Builder(VarGroup base, String path)
    {
      super(base, path);
    }

    public Builder buckets(int buckets)
    {
      if (buckets <= 0)
        throw new IllegalArgumentException();
      _buckets = buckets;
      return self();
    }

    public Builder duration(long duration, TimeUnit unit)
    {
      if (duration <= 0 || unit == null)
        throw new IllegalArgumentException();
      _duration = duration;
      _unit = unit;
      return self();
    }

    @SuppressWarnings("unchecked")
    public <Type extends Number> Builder source(VarBase<Type,?> source)
    {
      _source = (VarBase<Number,?>) source;
      return self();
    }

    @Override
    protected VarAverage buildVar()
    {
      return new VarAverage(this);
    }
  }

  private static class Bucket
  {
    long timestamp;
    double average;
    int count;

    public void ingest(double value)
    {
      average += (value - average) / ++count;
    }
    public void reset()
    {
      this.timestamp = 0;
      average = 0.0;
      count = 0;
    }
  }

  private Bucket seek(long now)
  {
    long bucketTimestamp = now / _divisorMillis;
    Bucket head = _buckets[_head];
    if (head.timestamp == 0)
      head.timestamp = bucketTimestamp;
    else
    if (head.timestamp != bucketTimestamp)
    {
      long lastTimestamp = (now - _unit.toMillis(_duration)) / _divisorMillis;
      if (head.timestamp < lastTimestamp)
      {
        head.reset();
        head.timestamp = bucketTimestamp;
        _rollingAverage = 0.0;
        _rollingCount = 0;
        _tail = _head;
      }
      else
      {
        Bucket tail;
        while (_tail != _head && (tail = _buckets[_tail]).timestamp < lastTimestamp)
        {
          _rollingAverage -= (tail.average * tail.count) / _rollingCount;
          _rollingCount -= tail.count;
          tail.reset();
          if (_buckets.length == ++_tail)
            _tail = 0;
        }
      }
      do
      {
        _rollingCount += head.count;
        _rollingAverage += (head.average * head.count) / _rollingCount;
        if (_buckets.length == ++_head)
          _head = 0;
        Bucket next = _buckets[_head];
        next.reset();
        next.timestamp = head.timestamp + 1;
        head = next;
      }
      while (bucketTimestamp != head.timestamp);
    }
    return head;
  }

  public void set(double value)
  {
    if (_lock.tryLock())
    {
      double average;
      try
      {
        Bucket bucket = seek(System.currentTimeMillis());
        bucket.ingest(value);
        Double queued;
        while ((queued = _queue.poll()) != null)
          bucket.ingest(queued);
        average = _rollingAverage;
        int count = _rollingCount;
        count += bucket.count;
        average += (bucket.average * bucket.count) / count;
      }
      finally
      {
        _lock.unlock();
      }
      _attr.update(average);
    }
    else
    {
      _queue.add(value);
    }
  }

  private double get()
  {
    _lock.lock();
    try
    {
      Bucket bucket = seek(System.currentTimeMillis());
      double average = _rollingAverage;
      int count = _rollingCount;
      count += bucket.count;
      average += (bucket.average * bucket.count) / count;
      return average;
    }
    finally
    {
      _lock.unlock();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int intValue()
  {
    return (int) get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long longValue()
  {
    return (long) get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public float floatValue()
  {
    return (float) get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double doubleValue()
  {
    return get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setStringValue(String value)
  {

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addListener(VarListener<Double, VarAverage> listener)
  {
    _attr.addListener(listener);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void removeListener(VarListener<Double, VarAverage> listener)
  {
    _attr.removeListener(listener);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Double getValue()
  {
    return get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Pair<Double, Long> getValueAndTimestamp()
  {
    return Pair.make(getValue(), System.currentTimeMillis());
  }
}
