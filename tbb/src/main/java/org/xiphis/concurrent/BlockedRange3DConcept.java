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
public final class BlockedRange3DConcept<PageValue extends RangeConcept<PageValue>.Range, RowValue extends RangeConcept<RowValue>.Range, ColValue extends RangeConcept<ColValue>.Range>
    extends RangeConcept<BlockedRange3DConcept<PageValue, RowValue, ColValue>.BlockedRange3D>
{
  /**
   * Clone range.
   *
   * @param range
   * @return
   */
  @Override
  public BlockedRange3DConcept<PageValue, RowValue, ColValue>.BlockedRange3D dup(
      BlockedRange3DConcept<PageValue, RowValue, ColValue>.BlockedRange3D range)
  {
    PageValue pages = range.pages();
    RowValue rows = range.rows();
    ColValue cols = range.cols();

    return new BlockedRange3D(pages.concept().dup(pages), rows.concept().dup(rows), cols.concept().dup(cols));
  }

  /**
   * Split range into two subranges.
   *
   * @param range
   * @return
   */
  @Override
  public BlockedRange3DConcept<PageValue, RowValue, ColValue>.BlockedRange3D split(
      BlockedRange3DConcept<PageValue, RowValue, ColValue>.BlockedRange3D range)
  {
    PageValue pages = range.pages();
    RowValue rows = range.rows();
    ColValue cols = range.cols();

    if (pages.size() * (double) rows.grainSize() < rows.size() * (double) pages.grainSize())
    {
      if (rows.size() * (double) cols.grainSize() < cols.size() * (double) rows.grainSize())
      {
        pages = pages.concept().dup(pages);
        rows = rows.concept().dup(rows);
        cols = cols.concept().split(cols);
      }
      else
      {
        pages = pages.concept().dup(pages);
        rows = rows.concept().split(rows);
        cols = cols.concept().dup(cols);
      }
    }
    else
    {
      if (pages.size() * (double) cols.grainSize() < cols.size() * (double) pages.grainSize())
      {
        pages = pages.concept().dup(pages);
        rows = rows.concept().dup(rows);
        cols = cols.concept().split(cols);
      }
      else
      {
        pages = pages.concept().split(pages);
        rows = rows.concept().dup(rows);
        cols = cols.concept().dup(cols);
      }
    }

    return new BlockedRange3D(pages, rows, cols);
  }

  public final class BlockedRange3D extends RangeConcept<BlockedRange3D>.Range
  {
    private final PageValue my_pages;
    private final RowValue my_rows;
    private final ColValue my_cols;

    public BlockedRange3D(PageValue my_pages, RowValue my_rows, ColValue my_cols)
    {
      this.my_pages = my_pages;
      this.my_rows = my_rows;
      this.my_cols = my_cols;
    }

    /**
     * Determines size of this range.
     *
     * @return
     */
    @Override
    public int size()
    {
      return my_pages.size() * my_rows.size() * my_cols.size();
    }

    /**
     * Test if range is empty.
     *
     * @return if range is empty.
     */
    @Override
    public boolean isEmpty()
    {
      return my_pages.isEmpty() || my_rows.isEmpty() || my_cols.isEmpty();
    }

    /**
     * Test if range can be partitioned into two subranges.
     *
     * @return
     */
    @Override
    public boolean isDivisible()
    {
      return my_pages.isDivisible() || my_rows.isDivisible() || my_cols.isDivisible();
    }

    public PageValue pages()
    {
      return my_pages;
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
