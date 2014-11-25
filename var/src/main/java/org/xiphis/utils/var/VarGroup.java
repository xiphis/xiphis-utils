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

import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * @author atcurtis
 * @since 2014-11-17
 */
public class VarGroup implements VarItem
{
  public static final VarGroup ROOT = new VarGroup();

  private final ConcurrentHashMap<String, VarItem> _children;

  private VarGroup()
  {
    _children = new ConcurrentHashMap<>();
  }

  public static void forEvery(BiConsumer<CharSequence, VarBase<?,?>> apply)
  {
    ROOT.forEach(apply);
  }

  public void forEach(BiConsumer<CharSequence, VarBase<?,?>> apply)
  {
    forEach(new StringBuilder(), apply);
  }

  private void forEach(StringBuilder nameBuilder, BiConsumer<CharSequence, VarBase<?,?>> apply)
  {
    _children.forEach(new BiConsumer<String, VarItem>()
    {
      final int _initialLength = nameBuilder.length();
      @Override
      public void accept(String s, VarItem varItem)
      {
        nameBuilder.setLength(_initialLength);
        nameBuilder.append(s);
        VarGroup group = varItem.asGroup();
        if (group != null)
        {
          nameBuilder.append('.');
          group.forEach(nameBuilder, apply);
        }
        else
        {
          apply.accept(nameBuilder, varItem.asVarBase());
        }
      }
    });
  }

  @Override
  public VarGroup asGroup()
  {
    return this;
  }

  public VarGroup getGroup(String name)
  {
    VarItem item = _children.get(name);
    if (item instanceof VarGroup)
      return (VarGroup) item;
    if (item != null)
      throw new ClassCastException();
    return null;
  }

  <Item extends VarItem> Item addItem(String name, Item child)
  {
    VarItem existing = _children.putIfAbsent(name, child);
    if (existing == null)
      return child;
    return child.cast(existing);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Item extends VarItem> Item cast(VarItem existing)
  {
    if (existing instanceof VarGroup)
      return (Item) existing;
    else
      throw new ClassCastException();
  }

  public VarGroup find(String path)
  {
    if (path.isEmpty())
      return this;
    Pair<VarGroup, CharSequence> pair = parsePath(path, false);
    VarGroup found = pair.first.getGroup(pair.second.toString());
    if (found == null)
      throw new NoSuchElementException();
    return found;
  }

  public VarItem findItem(String path)
  {
    if (path.isEmpty())
      return this;
    Pair<VarGroup, CharSequence> pair = parsePath(path, false);
    return pair.first._children.get(pair.second.toString());
  }

  public static void setStringValue(String path, String value)
  {
    VarItem item = ROOT.findItem(path);
    if (item != null && item.asVarBase() != null)
      item.asVarBase().setStringValue(value);
    else
      throw new NoSuchElementException();
  }

  Pair<VarGroup, CharSequence> parsePath(String path, boolean generate)
  {
    VarGroup group = this;
    int start = 0, pos = 0;
    while (pos < path.length())
    {
      if (start == pos)
      {
        if (Character.isJavaIdentifierStart(path.charAt(pos)))
        {
          pos++;
          continue;
        }
      }
      else
      {
        if ('.' == path.charAt(pos))
        {
          String name = path.substring(start, pos);
          VarGroup nextGroup = group.getGroup(name);
          if (nextGroup == null && generate)
            group = group.addItem(name, new VarGroup());
          else
          if (nextGroup == null)
              throw new NoSuchElementException();
          else
            group = nextGroup;
          start = ++pos;
          continue;
        }
        if (Character.isJavaIdentifierPart(path.charAt(pos)))
        {
          pos++;
          continue;
        }
      }
      throw new IllegalArgumentException("Bad character in path at position " + pos);
    }
    if (start == pos)
      throw new IllegalArgumentException("Missing name");
    return Pair.make(group, path.subSequence(start, pos));
  }
}
