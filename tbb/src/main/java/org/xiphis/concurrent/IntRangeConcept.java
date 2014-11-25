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

public final class IntRangeConcept extends RangeConcept<IntRangeConcept.IntRange>
{

  private final int _grainsize;

  public IntRangeConcept()
  {
    _grainsize = 1;
  }

  public IntRangeConcept(int grainsize)
  {
    _grainsize = grainsize;
  }

  public IntRange newInstance(int begin, int end)
  {
    return new IntRange(begin, end - begin);
  }

  @Override
  public IntRange dup(IntRange range)
  {
    return new IntRange(range._begin, range._size);
  }

  @Override
  public IntRange split(IntRange range)
  {
    int split = range.isDivisible() ? (range.size() + 1) / 2 : range.size();
    IntRange result = new IntRange(range.begin() + split, range.size() - split);
    range._size = split;
    return result;
  }

  public final class IntRange extends RangeConcept<IntRangeConcept.IntRange>.Range
  {
    int _begin;
    int _size;

    IntRange(int begin, int size)
    {
      _begin = begin;
      _size = size;
    }

    public int begin()
    {
      return _begin;
    }

    public int end()
    {
      return _begin + _size;
    }

    @Override
    public int size()
    {
      return _size;
    }

    @Override
    public boolean isEmpty()
    {
      return _size == 0;
    }

    @Override
    public boolean isDivisible()
    {
      return _size > _grainsize;
    }

    public int grainsize()
    {
      return _grainsize;
    }
  }
}
