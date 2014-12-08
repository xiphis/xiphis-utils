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

import org.xiphis.utils.common.Factory;

import java.util.ArrayList;
import java.util.List;

public class ParallelWhile<T>
{
  private final Factory<WhileGroupTask<T>> _whileGroupTaskFactory;
  private Body<T> _body;
  private EmptyTask _barrier;

  /**
   *
   */
  public ParallelWhile()
  {
    _whileGroupTaskFactory = arguments -> new WhileGroupTask<>(_body);
  }

  @SuppressWarnings("unchecked")
  private static <T> T[] newArray(int size)
  {
    return (T[]) new Object[size];
  }

  /**
   * @param stream stream source
   * @param body body
   */
  public void run(final Stream<T> stream, Body<T> body)
  {
    EmptyTask barrier = Task.allocateRoot(EmptyTask.FACTORY);
    _body = body;
    _barrier = barrier;
    _barrier.setRefCount(2);
    WhileTask<T> w = _barrier.allocateChild(arguments -> new WhileTask<>(stream, _whileGroupTaskFactory, _barrier));
    _barrier.spawnAndWaitForAll(w);
    _barrier.destroy(_barrier);
    _barrier = null;
    _body = null;

  }

  /**
   * @param item item
   */
  public void add(final T item)
  {
    assert _barrier != null : "attempt to add to parallel_while that is not running";
    Task t = Task.currentTask();
    IterationTask<T> i = t.allocateAdditionalChildOf(_barrier, arguments -> new IterationTask<>(item, _body));
    t.spawn(i);
  }

  /**
   * @param <T> type of item
   * @author atcurtis
   */
  public interface Body<T>
  {
    /**
     * @param item item
     */
    void apply(T item);
  }

  /**
   * @param <T> type of item
   * @author atcurtis
   */
  public interface Stream<T>
  {
    /**
     * @param item holder for item
     * @return true if item available
     */
    boolean popIfPresent(T[] item);
  }

  private static class IterationTask<T> extends Task
  {
    private final Body<T> my_body;
    private final T my_value;

    public IterationTask(T item, Body<T> body)
    {
      my_body = body;
      my_value = item;
    }

    @Override
    public Task execute()
    {
      my_body.apply(my_value);
      return null;
    }
  }

  private static class WhileGroupTask<T> extends Task
  {
    public static final int max_arg_size = 4;
    public final T[] my_arg;
    private final Body<T> my_body;
    public int size;
    private int idx;

    public WhileGroupTask(Body<T> body)
    {
      my_body = body;
      my_arg = newArray(max_arg_size);
    }

    @Override
    public Task execute()
    {
      assert size > 0;
      List<Task> list = new ArrayList<>(size);
      Task t;
      idx = 0;
      for (Factory<IterationTask<T>> iterationTaskFactory = arguments -> new IterationTask<>(my_arg[idx], my_body); ; )
      {
        t = allocateChild(iterationTaskFactory);
        if (++idx == size)
        {
          break;
        }
        list.add(t);
      }
      setRefCount(idx + 1);
      spawn(list);
      spawnAndWaitForAll(t);
      return null;
    }
  }

  private static class WhileTask<T> extends Task
  {
    private final Stream<T> my_stream;
    private final EmptyTask my_barrier;
    private final Factory<WhileGroupTask<T>> whileGroupTaskFactory;
    private final T[] my_arg;

    public WhileTask(Stream<T> stream, Factory<WhileGroupTask<T>> factory, EmptyTask barrier)
    {
      my_stream = stream;
      whileGroupTaskFactory = factory;
      my_barrier = barrier;
      my_arg = newArray(1);
    }

    @Override
    public Task execute()
    {
      WhileGroupTask<T> t = allocateAdditionalChildOf(my_barrier, whileGroupTaskFactory);
      int k = 0;
      while (my_stream.popIfPresent(my_arg))
      {
        t.my_arg[k] = my_arg[0];
        if (++k == WhileGroupTask.max_arg_size)
        {
          // There might be more iterations.
          recycleToReexecute();
          break;
        }
      }
      if (k == 0)
      {
        destroy(t);
        return null;
      }
      else
      {
        t.size = k;
        return t;
      }
    }
  }
}
