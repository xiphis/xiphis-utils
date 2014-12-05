/*
 Copyright (c) 2014, Xiphis
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 Neither the name of the Xiphis nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.xiphis.concurrent;

public abstract class BlockedRangeConcept<Value, R extends BlockedRangeConcept<Value, R>.BlockedRange> //
    extends RangeConcept<R>
{
  private final int _grainsize;

  public BlockedRangeConcept()
  {
    this(1);
  }

  public BlockedRangeConcept(int grainsize)
  {
    assert grainsize > 0 : "grainsize must be positive";
    _grainsize = grainsize;
  }

  public abstract R newInstance(Value begin, Value end);

  @Override
  public R dup(R range)
  {
    return newInstance(range.begin(), range.end());
  }

  @Override
  public R split(R r)
  {
    assert r.isDivisible() : "cannot split indivisible range";
    R s = dup(r);
    Value middle = r.increment(r.begin(), r.difference(r.end(), r.begin()) / 2);
    r._end = middle;
    s._begin = middle;
    return s;
  }

  public abstract class BlockedRange extends Range
  {
    Value _begin;
    Value _end;

    protected BlockedRange(Value begin, Value end)
    {
      _begin = begin;
      _end = end;
    }

    public final Value begin()
    {
      return _begin;
    }

    public final Value end()
    {
      return _end;
    }

    /**
     * Compares values i and j.
     *
     * @param i first value
     * @param j second value
     * @return {@code true} if value i precedes value j.
     */
    public abstract boolean lessThan(Value i, Value j);

    /**
     * Number of values in range i..j
     *
     * @param i first value
     * @param j second value
     * @return distance between values
     */
    public abstract int difference(Value i, Value j);

    /**
     * @param i value
     * @param k delta
     * @return kth value after i
     */
    public abstract Value increment(Value i, int k);
  }
}
