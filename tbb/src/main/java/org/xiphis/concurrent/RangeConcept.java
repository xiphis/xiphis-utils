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

public abstract class RangeConcept<R extends RangeConcept<R>.Range>
{

  /**
   * Clone range.
   *
   * @param range
   * @return
   */
  public abstract R dup(R range);

  /**
   * Split range into two subranges.
   *
   * @param range
   * @return
   */
  public abstract R split(R range);

  public abstract class Range
  {
    /**
     * Determines size of this range.
     *
     * @return
     */
    public abstract int size();

    /**
     * Test if range is empty.
     *
     * @return if range is empty.
     */
    public abstract boolean isEmpty();

    /**
     * Test if range can be partitioned into two subranges.
     *
     * @return
     */
    public abstract boolean isDivisible();

    /**
     * Returns the grain size of this range.
     *
     * @return grain size
     */
    public int grainSize()
    {
      return 1;
    }

    public final RangeConcept<R> concept()
    {
      return RangeConcept.this;
    }
  }
}
