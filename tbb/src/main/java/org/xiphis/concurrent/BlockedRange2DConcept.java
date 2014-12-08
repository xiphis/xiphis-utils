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

/**
 * @author atcurtis
 * @since 2014-08-30
 */
public final class BlockedRange2DConcept<RowValue extends RangeConcept<RowValue>.Range, ColValue extends RangeConcept<ColValue>.Range>
    extends RangeConcept<BlockedRange2DConcept<RowValue, ColValue>.BlockedRange2D>
{

  public static <RowValue extends RangeConcept<RowValue>.Range, ColValue extends RangeConcept<ColValue>.Range> BlockedRange2DConcept<RowValue, ColValue>.BlockedRange2D newRange(RowValue rows, ColValue cols)
  {
    return new BlockedRange2DConcept<RowValue,ColValue>().newInstance(rows, cols);
  }

  public BlockedRange2D newInstance(RowValue rows, ColValue cols)
  {
    return new BlockedRange2D(rows, cols);
  }

  /**
   * Clone range.
   *
   * @param range range to duplicate
   * @return new instance
   */
  @Override
  public BlockedRange2D dup(BlockedRange2D range)
  {
    RowValue rows = range.rows(); rows = rows.concept().dup(rows);
    ColValue cols = range.cols(); cols = cols.concept().dup(cols);
    return newInstance(rows, cols);
  }

  /**
   * Split range into two subranges.
   *
   * @param range range to split
   * @return new instance
   */
  @Override
  public BlockedRange2D split(BlockedRange2D range)
  {
    RowValue row = range.rows();
    ColValue col = range.cols();

    if (row.size() * (double) col.grainSize() < col.size() * (double) row.grainSize())
    {
      col = col.concept().split(col);
      row = row.concept().dup(row);
    }
    else
    {
      col = col.concept().dup(col);
      row = row.concept().split(row);
    }

    return newInstance(row, col);
  }

  public final class BlockedRange2D extends RangeConcept<BlockedRange2D>.Range
  {
    private final RowValue my_rows;
    private final ColValue my_cols;

    public BlockedRange2D(RowValue my_rows, ColValue my_cols)
    {
      this.my_rows = my_rows;
      this.my_cols = my_cols;
    }

    /**
     * Determines size of this range.
     *
     * @return size
     */
    @Override
    public int size()
    {
      return my_rows.size() * my_cols.size();
    }

    /**
     * Test if range is empty.
     *
     * @return if range is empty.
     */
    @Override
    public boolean isEmpty()
    {
      return my_rows.isEmpty() || my_cols.isEmpty();
    }

    /**
     * Test if range can be partitioned into two subranges.
     *
     * @return {@code true} if divisible
     */
    @Override
    public boolean isDivisible()
    {
      return my_rows.isDivisible() || my_cols.isDivisible();
    }

    public RowValue rows()
    {
      return my_rows;
    }

    public ColValue cols()
    {
      return my_cols;
    }
  }

}
