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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A Semaphore with 1 permit may be used as a kind of mutual exclusion.
 *
 * Use would be something like:
 *
 * <pre>{@code
 *   if (!semAcquired)
 *   {
 *     Task c = allocateAsContinuation(EmptyTask.FACTORY);
 *     c.setRefCount(1);
 *     recycleAsChildOf(c);
 *     Task acquire = allocateAsChild(semaphore.acquire());
 *     setRefCount(1);
 *     semAcquired = true;
 *     return acquire;
 *   }
 * }</pre>
 *
 *
 * Will spin a lot of CPU if there is one permit available and all waiting tasks are requesting more than one permit.
 *
 * @author atcurtis
 * @since 2014-08-30
 */
public final class Semaphore
{
  private final TaskGroupContext semaphoreContext;
  private final AtomicInteger availablePermits;
  private final ConcurrentLinkedQueue<AcquireTask> waiting = new ConcurrentLinkedQueue<>();

  private class AcquireTask extends Task
  {
    private final int permits;

    private AcquireTask(int permits)
    {
      if (currentTask() == null || currentTask().context() != semaphoreContext)
        throw new IllegalStateException("Invalid task context");
      this.permits = permits;
    }

    @Override
    protected Task execute()
        throws Exception
    {
      int available = availablePermits();
      while (available >= permits)
      {
        if (availablePermits.compareAndSet(available, available - permits))
        {
          if (available > permits)
          {
            return waiting.poll();
          }

          return null;
        }
      }

      recycleToReexecute();

      if (available > 0)
      {
        AcquireTask poll = waiting.poll();
        if (poll != null && poll.permits <= available)
        {
          waiting.add(this);
          return poll;
        }

        spawn(poll);
      }

      waiting.add(this);

      return null;
    }
  }

  public Semaphore(int permits, TaskGroupContext semaphoreContext)
  {
    this.semaphoreContext = semaphoreContext;
    this.availablePermits = new AtomicInteger(permits);
  }

  public Factory<Task> acquire()
  {
    return acquire(1);
  }

  public Factory<Task> acquire(int permits)
  {
    if (permits <= 0)
      throw new IllegalArgumentException();
    return arguments -> new AcquireTask(permits);
  }

  public int availablePermits()
  {
    return availablePermits.get();
  }

  public boolean hasWaiters()
  {
    return !waiting.isEmpty();
  }

  public void release()
  {
    release(1);
  }

  public void release(int permits)
  {
    if (permits <= 0)
      throw new IllegalArgumentException();
    if (availablePermits.getAndAdd(permits) == 0)
    {
      Task currentTask = Task.currentTask();
      if (currentTask != null)
      {
        AcquireTask poll = waiting.poll();
        if (poll != null)
        {
          currentTask.spawn(poll);
        }
      }
      else
      {
        try
        {
          Task.spawnRootAndWait(semaphoreContext, (Task) Task.allocateRoot(semaphoreContext, arguments -> new Task()
          {
            @Override
            protected Task execute()
                throws Exception
            {
              return waiting.poll();
            }
          }));
        }
        catch (InterruptedException ignored)
        {
        }
      }
    }
  }

  public boolean tryAcquire()
  {
    return tryAcquire(1);
  }

  public boolean tryAcquire(int permits)
  {
    if (permits <= 0)
      throw new IllegalArgumentException();

    int available = availablePermits();
    while (available >= permits)
    {
      if (availablePermits.compareAndSet(available, available - permits))
      {
        return true;
      }
    }
    return false;
  }
}
