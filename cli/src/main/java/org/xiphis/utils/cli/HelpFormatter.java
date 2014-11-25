/*
 Copyright (c) 2014, Xiphis
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 Neither the name of the Xiphis nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.xiphis.utils.cli;

/**
 * @author atcurtis
 * @since 2014-11-16
 */
public class HelpFormatter
{
  private final StringBuilder _text;
  private int _columns;
  private static final String NL = String.format("%n");

  public HelpFormatter(StringBuilder builder)
  {
    this(Integer.parseUnsignedInt(System.getenv("COLUMNS")), builder);
  }

  public HelpFormatter(int columns, StringBuilder builder)
  {
    _columns = columns;
    _text = builder;
  }

  public void printTitle(String text)
  {
    _text.append(String.format("%s:%n", text));
  }

  public void printText(int indent, int initial, String text)
  {
    int start = 0;
    if (initial > 0 && initial+2 > indent || text.isEmpty())
    {
      _text.append(NL);
      initial = 0;
    }
    while (start != text.length())
    {
      int pos = start, lastBreak = start;
      while (pos != text.length() && pos - start < _columns - indent)
      {
        if (Character.isWhitespace(text.charAt(pos)))
        {
          lastBreak = pos++;
          continue;
        }
        if (text.charAt(pos) == '-')
        {
          lastBreak = ++pos;
          continue;
        }
        pos++;
      }
      for (int i = initial; i < indent; i++)
        _text.append(' ');
      initial = 0;
      if (pos - start < _columns - indent)
      {
        _text.append(text.subSequence(start, pos));
        start = pos;
      }
      else
      {
        _text.append(text.subSequence(start, lastBreak));
        start = lastBreak;
      }
      _text.append(NL);
      while (start != text.length() && Character.isWhitespace(text.charAt(start))) start++;
    }
  }

  public void printText(String text)
  {
    printText(0, 0, text);
  }

  public void printOption(String longName, Character shortName, String arg,
                          String description)
  {
    int pos = _text.length();
    _text.append("  ");
    if (shortName != null)
    {
      _text.append('-').append(shortName.charValue());
      if (arg != null)
      {
        _text.append(' ').append(arg);
      }
    }
    if (longName != null)
    {
      if (shortName != null)
      {
        _text.append(", ");
      }
      _text.append("--").append(longName);
      if (arg != null)
      {
        _text.append('=').append(arg);
      }
    }
    if (description != null)
      printText(30, _text.length() - pos, description);
    else
      _text.append(NL);
  }

}
