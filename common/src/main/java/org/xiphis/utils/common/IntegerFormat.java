/*
 Copyright (c) 2014, Xiphis
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 Neither the name of the Xiphis nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.xiphis.utils.common;

/**
 * @author atcurtis
 * @since 2014-11-20
 */
public enum IntegerFormat
{
  SIGNED
      {
        @Override
        public int parseInt(String value)
        {
          return Integer.parseInt(value);
        }

        @Override
        public long parseLong(String value)
        {
          return Long.parseLong(value);
        }

        @Override
        public String toString(int value)
        {
          return Integer.toString(value);
        }

        @Override
        public String toString(long value)
        {
          return Long.toString(value);
        }
      },
  UNSIGNED
      {
        @Override
        public int parseInt(String value)
        {
          return Integer.parseUnsignedInt(value);
        }

        @Override
        public long parseLong(String value)
        {
          return Long.parseUnsignedLong(value);
        }

        @Override
        public String toString(int value)
        {
          return Integer.toUnsignedString(value);
        }

        @Override
        public String toString(long value)
        {
          return Long.toUnsignedString(value);
        }
      },
  BINARY
      {
        @Override
        public int parseInt(String value)
        {
          return Integer.parseUnsignedInt(value, 2);
        }

        @Override
        public long parseLong(String value)
        {
          return Long.parseUnsignedLong(value, 2);
        }

        @Override
        public String toString(int value)
        {
          return Integer.toBinaryString(value);
        }

        @Override
        public String toString(long value)
        {
          return Long.toBinaryString(value);
        }
      },
  OCTAL
      {
        @Override
        public int parseInt(String value)
        {
          return Integer.parseUnsignedInt(value, 8);
        }

        @Override
        public long parseLong(String value)
        {
          return Long.parseUnsignedLong(value, 8);
        }

        @Override
        public String toString(int value)
        {
          return Integer.toOctalString(value);
        }

        @Override
        public String toString(long value)
        {
          return Long.toOctalString(value);
        }
      },
  HEX
      {
        @Override
        public int parseInt(String value)
        {
          return Integer.parseUnsignedInt(value, 16);
        }

        @Override
        public long parseLong(String value)
        {
          return Long.parseUnsignedLong(value, 16);
        }

        @Override
        public String toString(int value)
        {
          return Integer.toHexString(value);
        }

        @Override
        public String toString(long value)
        {
          return Long.toHexString(value);
        }
      };

  public abstract int parseInt(String value);
  public abstract long parseLong(String value);
  public abstract String toString(int value);
  public abstract String toString(long value);
}
