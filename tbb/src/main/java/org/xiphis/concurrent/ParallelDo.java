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

import io.netty.util.concurrent.EventExecutorGroup;
import org.xiphis.utils.common.Factory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public abstract class ParallelDo<Item>
{

  private final static int BLOCK_SPLIT_SIZE = 4;

  private EmptyTask _barrier;

  public ParallelDo(EventExecutorGroup executorGroup)
  {
    _barrier = Task.allocateRoot(new TaskGroupContext(executorGroup), EmptyTask.FACTORY);
  }

  public ParallelDo(TaskGroupContext context)
  {
    _barrier = Task.allocateRoot(context, EmptyTask.FACTORY);
  }

  @Override
  protected void finalize()
      throws Throwable
  {
    try
    {
      Task.destroy(_barrier);
      _barrier = null;
    }
    finally
    {
      super.finalize();
    }
  }

  protected void add(final Item item)
  {
    IterationTask t = Task.currentTask().allocateAdditionalChildOf(_barrier, arguments -> new IterationTask(item));
    t.spawn(t);
  }

  private class IterationTask extends Task
  {
    private final Item _value;

    public IterationTask(Item item)
    {
      _value = item;
    }

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      operator(_value);
      return null;
    }

  }

  public void start(final Iterator<Item> iterator)
  {
    IteratorTask t = _barrier.allocateChild(arguments -> new IteratorTask(iterator));
    _barrier.setRefCount(2);
    _barrier.spawnAndWaitForAll(t);
  }

  public void start(final Item[] array)
  {
    ArrayTask t = _barrier.allocateChild(arguments -> new ArrayTask(array, 0, array.length));
    _barrier.setRefCount(2);
    _barrier.spawnAndWaitForAll(t);
  }

  public void start(final List<Item> list)
  {
    ListTask t = _barrier.allocateChild(arguments -> new ListTask(list));
    _barrier.setRefCount(2);
    _barrier.spawnAndWaitForAll(t);
  }

  private class IteratorTask extends Task
  {
    private final Iterator<Item> my_iterator;

    private final Factory<IterationTask> iterationTaskFactory;

    public IteratorTask(Iterator<Item> iterator)
    {
      this.my_iterator = iterator;
      iterationTaskFactory = arguments -> new IterationTask(my_iterator.next());
    }

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      if (my_iterator.hasNext())
      {
        EmptyTask c = allocateContinuation(EmptyTask.FACTORY);
        IterationTask t = c.allocateChild(iterationTaskFactory);
        recycleAsChildOf(c);
        t.setRefCount(2);
        return this;
      }
      return null;
    }
  }

  private class ListIterationFactory implements Factory<IterationTask>
  {
    private final ListIterator<Item> it;
    private int count;

    public ListIterationFactory(ListIterator<Item> it)
    {
      this.it = it;
      count = 0;
    }

    public IterationTask construct(Object... arguments)
    {
      ++count;
      return new IterationTask(it.next());
    }

    public boolean hasNext()
    {
      return it.hasNext();
    }

    public int getCount()
    {
      return count;
    }
  }

  private class ListTask extends Task
  {
    private List<Item> my_list;
    private final Factory<ListTask> listTaskFactory;

    public ListTask(List<Item> list)
    {
      my_list = list;
      listTaskFactory = arguments -> {
        // TODO Auto-generated method stub
        int size = my_list.size();
        int split = size / 2;
        List<Item> sublist = my_list.subList(size - split, size);
        my_list = my_list.subList(0, size - split);
        return new ListTask(sublist);
      };
    }

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      if (my_list.size() > BLOCK_SPLIT_SIZE)
      {
        EmptyTask c = allocateContinuation(EmptyTask.FACTORY);
        ListTask b = c.allocateChild(listTaskFactory);
        recycleAsChildOf(c);
        c.setRefCount(2);
        c.spawn(b);
        return this;
      }
      else if (!my_list.isEmpty())
      {
        ListIterationFactory listIterationFactory = new ListIterationFactory(my_list.listIterator());
        List<Task> list = new LinkedList<>();
        Task t;
        for (; ; )
        {
          t = allocateChild(listIterationFactory);
          if (!listIterationFactory.hasNext())
          {
            break;
          }
          list.add(t);
        }
        setRefCount(listIterationFactory.getCount() + 1);
        spawn(list);
        spawnAndWaitForAll(t);
      }
      return null;
    }

  }

  private class ArrayTask extends Task
  {
    private final Item[] array;
    private int my_first;
    private int my_length;
    private final Factory<ArrayTask> blockTaskFactory;

    public ArrayTask(Item[] arr, int first, int length)
    {
      this.array = arr;
      this.my_first = first;
      this.my_length = length;
      blockTaskFactory = arguments -> {
        int split = my_length / 2;
        int first1 = my_first + my_length - split;
        my_length -= split;
        return new ArrayTask(array, first1, split);
      };
    }

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      if (my_length > BLOCK_SPLIT_SIZE)
      {
        EmptyTask c = allocateContinuation(EmptyTask.FACTORY);
        ArrayTask b = c.allocateChild(blockTaskFactory);
        recycleAsChildOf(c);
        c.setRefCount(2);
        c.spawn(b);
        return this;
      }
      else if (my_length != 0)
      {
        Factory<IterationTask> iterationTaskFactory = arguments -> new IterationTask(array[my_first]);
        List<Task> list = new ArrayList<>(my_length);
        Task t;
        for (int k1 = 0; ; )
        {
          t = allocateChild(iterationTaskFactory);
          ++my_first;
          if (++k1 == my_length)
          {
            break;
          }
          list.add(t);
        }
        setRefCount(my_length + 1);
        spawn(list);
        spawnAndWaitForAll(t);
      }
      return null;
    }
  }

  protected abstract void operator(Item item);
}
