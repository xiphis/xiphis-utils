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

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author acurtis
 * @see io.netty.util.concurrent.DefaultEventExecutorGroup
 */
public class UnfairEventExecutorGroup extends DefaultEventExecutorGroup
{
  /**
   * {@inheritDoc}
   */
  public UnfairEventExecutorGroup(int nThreads)
  {
    super(nThreads);
  }

  /**
  /**
   * Create a new instance.
   *
   * @param nThreads      the number of threads that will be used by this instance.
   * @param threadFactory the ThreadFactory to use, or {@code null} if the default should be used.
   */
  public UnfairEventExecutorGroup(int nThreads, ThreadFactory threadFactory)
  {
    super(nThreads, threadFactory);
  }

  @Override
  protected EventExecutor newChild(
      ThreadFactory threadFactory, Object... args) throws Exception {
    return new SingleThreadEventExecutor(this, threadFactory, true)
    {
      @Override
      protected Queue<Runnable> newTaskQueue() {
        return new BlockingQueue<Runnable>()
        {
          private final BlockingQueue<Runnable> threadSafeQueue = new LinkedBlockingQueue<>();
          private final Deque<Runnable> fastQueue = new ArrayDeque<>();

          @Override
          public boolean add(Runnable runnable)
          {
            if (inEventLoop())
              return fastQueue.add(runnable);
            return threadSafeQueue.add(runnable);
          }

          @Override
          public boolean offer(Runnable runnable)
          {
            if (inEventLoop())
              return fastQueue.offer(runnable);
            return threadSafeQueue.offer(runnable);
          }

          @Override
          public Runnable remove()
          {
            if (inEventLoop() && !fastQueue.isEmpty())
              return fastQueue.remove();
            return threadSafeQueue.remove();
          }

          @Override
          public Runnable poll()
          {
            if (inEventLoop() && !fastQueue.isEmpty())
              return fastQueue.poll();
            return threadSafeQueue.poll();
          }

          @Override
          public Runnable element()
          {
            if (inEventLoop() && !fastQueue.isEmpty())
              return fastQueue.element();
            return threadSafeQueue.element();
          }

          @Override
          public Runnable peek()
          {
            if (inEventLoop() && !fastQueue.isEmpty())
              return fastQueue.peek();
            return threadSafeQueue.peek();
          }

          @Override
          public void put(Runnable runnable) throws InterruptedException
          {
            if (inEventLoop())
              fastQueue.add(runnable);
            else
              threadSafeQueue.put(runnable);
          }

          @Override
          public boolean offer(Runnable runnable, long timeout, TimeUnit unit) throws InterruptedException
          {
            if (inEventLoop())
              return fastQueue.offer(runnable);
            return threadSafeQueue.offer(runnable, timeout, unit);
          }

          @Override
          public Runnable take() throws InterruptedException
          {
            if (inEventLoop() && !fastQueue.isEmpty())
              return fastQueue.poll();
            return threadSafeQueue.take();
          }

          /**
           * Retrieves and removes the head of this queue, waiting up to the
           * specified wait time if necessary for an element to become available.
           *
           * @param timeout how long to wait before giving up, in units of
           *                {@code unit}
           * @param unit    a {@code TimeUnit} determining how to interpret the
           *                {@code timeout} parameter
           * @return the head of this queue, or {@code null} if the
           *         specified waiting time elapses before an element is available
           * @throws InterruptedException if interrupted while waiting
           */
          @Override
          public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException
          {
            if (inEventLoop() && !fastQueue.isEmpty())
              return fastQueue.poll();
            return threadSafeQueue.poll(timeout, unit);
          }

          /**
           * Returns the number of additional elements that this queue can ideally
           * (in the absence of memory or resource constraints) accept without
           * blocking, or {@code Integer.MAX_VALUE} if there is no intrinsic
           * limit.
           * <p/>
           * <p>Note that you <em>cannot</em> always tell if an attempt to insert
           * an element will succeed by inspecting {@code remainingCapacity}
           * because it may be the case that another thread is about to
           * insert or remove an element.
           *
           * @return the remaining capacity
           */
          @Override
          public int remainingCapacity()
          {
            return Integer.MAX_VALUE;
          }

          /**
           * Removes a single instance of the specified element from this queue,
           * if it is present.  More formally, removes an element {@code e} such
           * that {@code o.equals(e)}, if this queue contains one or more such
           * elements.
           * Returns {@code true} if this queue contained the specified element
           * (or equivalently, if this queue changed as a result of the call).
           *
           * @param o element to be removed from this queue, if present
           * @return {@code true} if this queue changed as a result of the call
           * @throws ClassCastException   if the class of the specified element
           *                              is incompatible with this queue
           *                              (<a href="../Collection.html#optional-restrictions">optional</a>)
           * @throws NullPointerException if the specified element is null
           *                              (<a href="../Collection.html#optional-restrictions">optional</a>)
           */
          @Override
          public boolean remove(Object o)
          {
            if (inEventLoop())
              return fastQueue.remove(o) || threadSafeQueue.remove(o);
            return threadSafeQueue.remove(o);
          }

          /**
           * Returns <tt>true</tt> if this collection contains all of the elements
           * in the specified collection.
           *
           * @param c collection to be checked for containment in this collection
           * @return <tt>true</tt> if this collection contains all of the elements
           *         in the specified collection
           * @throws ClassCastException   if the types of one or more elements
           *                              in the specified collection are incompatible with this
           *                              collection
           *                              (<a href="#optional-restrictions">optional</a>)
           * @throws NullPointerException if the specified collection contains one
           *                              or more null elements and this collection does not permit null
           *                              elements
           *                              (<a href="#optional-restrictions">optional</a>),
           *                              or if the specified collection is null.
           * @see #contains(Object)
           */
          @Override
          public boolean containsAll(Collection<?> c)
          {
            System.out.println("Not implemented!");
            return false;  //To change body of implemented methods use File | Settings | File Templates.
          }

          /**
           * Adds all of the elements in the specified collection to this collection
           * (optional operation).  The behavior of this operation is undefined if
           * the specified collection is modified while the operation is in progress.
           * (This implies that the behavior of this call is undefined if the
           * specified collection is this collection, and this collection is
           * nonempty.)
           *
           * @param c collection containing elements to be added to this collection
           * @return <tt>true</tt> if this collection changed as a result of the call
           * @throws UnsupportedOperationException if the <tt>addAll</tt> operation
           *                                       is not supported by this collection
           * @throws ClassCastException            if the class of an element of the specified
           *                                       collection prevents it from being added to this collection
           * @throws NullPointerException          if the specified collection contains a
           *                                       null element and this collection does not permit null elements,
           *                                       or if the specified collection is null
           * @throws IllegalArgumentException      if some property of an element of the
           *                                       specified collection prevents it from being added to this
           *                                       collection
           * @throws IllegalStateException         if not all the elements can be added at
           *                                       this time due to insertion restrictions
           * @see #add(Object)
           */
          @Override
          public boolean addAll(Collection<? extends Runnable> c)
          {
            if (inEventLoop())
              return fastQueue.addAll(c);
            return threadSafeQueue.addAll(c);
          }

          /**
           * Removes all of this collection's elements that are also contained in the
           * specified collection (optional operation).  After this call returns,
           * this collection will contain no elements in common with the specified
           * collection.
           *
           * @param c collection containing elements to be removed from this collection
           * @return <tt>true</tt> if this collection changed as a result of the
           *         call
           * @throws UnsupportedOperationException if the <tt>removeAll</tt> method
           *                                       is not supported by this collection
           * @throws ClassCastException            if the types of one or more elements
           *                                       in this collection are incompatible with the specified
           *                                       collection
           *                                       (<a href="#optional-restrictions">optional</a>)
           * @throws NullPointerException          if this collection contains one or more
           *                                       null elements and the specified collection does not support
           *                                       null elements
           *                                       (<a href="#optional-restrictions">optional</a>),
           *                                       or if the specified collection is null
           * @see #remove(Object)
           * @see #contains(Object)
           */
          @Override
          public boolean removeAll(Collection<?> c)
          {
            throw new UnsupportedOperationException();
          }

          /**
           * Retains only the elements in this collection that are contained in the
           * specified collection (optional operation).  In other words, removes from
           * this collection all of its elements that are not contained in the
           * specified collection.
           *
           * @param c collection containing elements to be retained in this collection
           * @return <tt>true</tt> if this collection changed as a result of the call
           * @throws UnsupportedOperationException if the <tt>retainAll</tt> operation
           *                                       is not supported by this collection
           * @throws ClassCastException            if the types of one or more elements
           *                                       in this collection are incompatible with the specified
           *                                       collection
           *                                       (<a href="#optional-restrictions">optional</a>)
           * @throws NullPointerException          if this collection contains one or more
           *                                       null elements and the specified collection does not permit null
           *                                       elements
           *                                       (<a href="#optional-restrictions">optional</a>),
           *                                       or if the specified collection is null
           * @see #remove(Object)
           * @see #contains(Object)
           */
          @Override
          public boolean retainAll(Collection<?> c)
          {
            throw new UnsupportedOperationException();
          }

          /**
           * Removes all of the elements from this collection (optional operation).
           * The collection will be empty after this method returns.
           *
           * @throws UnsupportedOperationException if the <tt>clear</tt> operation
           *                                       is not supported by this collection
           */
          @Override
          public void clear()
          {
            throw new UnsupportedOperationException();
          }

          /**
           * Returns the number of elements in this collection.  If this collection
           * contains more than <tt>Integer.MAX_VALUE</tt> elements, returns
           * <tt>Integer.MAX_VALUE</tt>.
           *
           * @return the number of elements in this collection
           */
          @Override
          public int size()
          {
            if (inEventLoop())
              return fastQueue.size() + threadSafeQueue.size();
            return threadSafeQueue.size();
          }

          /**
           * Returns <tt>true</tt> if this collection contains no elements.
           *
           * @return <tt>true</tt> if this collection contains no elements
           */
          @Override
          public boolean isEmpty()
          {
            if (inEventLoop())
              return fastQueue.isEmpty() && threadSafeQueue.isEmpty();
            return threadSafeQueue.isEmpty();
          }

          /**
           * Returns {@code true} if this queue contains the specified element.
           * More formally, returns {@code true} if and only if this queue contains
           * at least one element {@code e} such that {@code o.equals(e)}.
           *
           * @param o object to be checked for containment in this queue
           * @return {@code true} if this queue contains the specified element
           * @throws ClassCastException   if the class of the specified element
           *                              is incompatible with this queue
           *                              (<a href="../Collection.html#optional-restrictions">optional</a>)
           * @throws NullPointerException if the specified element is null
           *                              (<a href="../Collection.html#optional-restrictions">optional</a>)
           */
          @Override
          public boolean contains(Object o)
          {
            if (inEventLoop())
              return fastQueue.contains(o) || threadSafeQueue.contains(o);
            return threadSafeQueue.contains(o);
          }

          /**
           * Returns an iterator over the elements in this collection.  There are no
           * guarantees concerning the order in which the elements are returned
           * (unless this collection is an instance of some class that provides a
           * guarantee).
           *
           * @return an <tt>Iterator</tt> over the elements in this collection
           */
          @Override
          public Iterator<Runnable> iterator()
          {
            if (inEventLoop())
              return new Iterator<Runnable>()
              {
                Iterator<Runnable> current = fastQueue.iterator();
                Iterator<Runnable> next = threadSafeQueue.iterator();

                @Override
                public boolean hasNext()
                {
                  if (next != null)
                  {
                    if (current.hasNext())
                      return true;
                    current = next;
                    next = null;
                  }
                  return current.hasNext();
                }

                @Override
                public Runnable next()
                {
                  return current.next();
                }
              };
            return threadSafeQueue.iterator();
          }

          /**
           * Returns an array containing all of the elements in this collection.
           * If this collection makes any guarantees as to what order its elements
           * are returned by its iterator, this method must return the elements in
           * the same order.
           * <p/>
           * <p>The returned array will be "safe" in that no references to it are
           * maintained by this collection.  (In other words, this method must
           * allocate a new array even if this collection is backed by an array).
           * The caller is thus free to modify the returned array.
           * <p/>
           * <p>This method acts as bridge between array-based and collection-based
           * APIs.
           *
           * @return an array containing all of the elements in this collection
           */
          @Override
          public Object[] toArray()
          {
            ArrayList<Object> list = new ArrayList<>();
            if (inEventLoop())
              list.addAll(Arrays.asList(fastQueue.toArray()));
            list.addAll(Arrays.asList(threadSafeQueue.toArray()));
            return list.toArray();
          }

          /**
           * Returns an array containing all of the elements in this collection;
           * the runtime type of the returned array is that of the specified array.
           * If the collection fits in the specified array, it is returned therein.
           * Otherwise, a new array is allocated with the runtime type of the
           * specified array and the size of this collection.
           * <p/>
           * <p>If this collection fits in the specified array with room to spare
           * (i.e., the array has more elements than this collection), the element
           * in the array immediately following the end of the collection is set to
           * <tt>null</tt>.  (This is useful in determining the length of this
           * collection <i>only</i> if the caller knows that this collection does
           * not contain any <tt>null</tt> elements.)
           * <p/>
           * <p>If this collection makes any guarantees as to what order its elements
           * are returned by its iterator, this method must return the elements in
           * the same order.
           * <p/>
           * <p>Like the {@link #toArray()} method, this method acts as bridge between
           * array-based and collection-based APIs.  Further, this method allows
           * precise control over the runtime type of the output array, and may,
           * under certain circumstances, be used to save allocation costs.
           * <p/>
           * <p>Suppose <tt>x</tt> is a collection known to contain only strings.
           * The following code can be used to dump the collection into a newly
           * allocated array of <tt>String</tt>:
           * <p/>
           * <pre>
           *     String[] y = x.toArray(new String[0]);</pre>
           *
           * Note that <tt>toArray(new Object[0])</tt> is identical in function to
           * <tt>toArray()</tt>.
           *
           * @param <T> the runtime type of the array to contain the collection
           * @param a   the array into which the elements of this collection are to be
           *            stored, if it is big enough; otherwise, a new array of the same
           *            runtime type is allocated for this purpose.
           * @return an array containing all of the elements in this collection
           * @throws ArrayStoreException  if the runtime type of the specified array
           *                              is not a supertype of the runtime type of every element in
           *                              this collection
           * @throws NullPointerException if the specified array is null
           */
          @Override
          @SuppressWarnings("unchecked")
          public <T> T[] toArray(T[] a)
          {
            ArrayList<T> list = new ArrayList<>();
            if (inEventLoop())
              ((List) list).addAll(Arrays.asList(fastQueue.toArray()));
            ((List) list).addAll(Arrays.asList(threadSafeQueue.toArray()));
            return list.toArray(a);
          }

          /**
           * Removes all available elements from this queue and adds them
           * to the given collection.  This operation may be more
           * efficient than repeatedly polling this queue.  A failure
           * encountered while attempting to add elements to
           * collection {@code c} may result in elements being in neither,
           * either or both collections when the associated exception is
           * thrown.  Attempts to drain a queue to itself result in
           * {@code IllegalArgumentException}. Further, the behavior of
           * this operation is undefined if the specified collection is
           * modified while the operation is in progress.
           *
           * @param c the collection to transfer elements into
           * @return the number of elements transferred
           * @throws UnsupportedOperationException if addition of elements
           *                                       is not supported by the specified collection
           * @throws ClassCastException            if the class of an element of this queue
           *                                       prevents it from being added to the specified collection
           * @throws NullPointerException          if the specified collection is null
           * @throws IllegalArgumentException      if the specified collection is this
           *                                       queue, or some property of an element of this queue prevents
           *                                       it from being added to the specified collection
           */
          @Override
          public int drainTo(Collection<? super Runnable> c)
          {
            int count = 0;
            if (inEventLoop())
            {
              Runnable task;
              while ((task = fastQueue.poll()) != null)
              {
                count++;
                c.add(task);
              }
            }
            count += threadSafeQueue.drainTo(c);
            return count;
          }

          /**
           * Removes at most the given number of available elements from
           * this queue and adds them to the given collection.  A failure
           * encountered while attempting to add elements to
           * collection {@code c} may result in elements being in neither,
           * either or both collections when the associated exception is
           * thrown.  Attempts to drain a queue to itself result in
           * {@code IllegalArgumentException}. Further, the behavior of
           * this operation is undefined if the specified collection is
           * modified while the operation is in progress.
           *
           * @param c           the collection to transfer elements into
           * @param maxElements the maximum number of elements to transfer
           * @return the number of elements transferred
           * @throws UnsupportedOperationException if addition of elements
           *                                       is not supported by the specified collection
           * @throws ClassCastException            if the class of an element of this queue
           *                                       prevents it from being added to the specified collection
           * @throws NullPointerException          if the specified collection is null
           * @throws IllegalArgumentException      if the specified collection is this
           *                                       queue, or some property of an element of this queue prevents
           *                                       it from being added to the specified collection
           */
          @Override
          public int drainTo(Collection<? super Runnable> c, int maxElements)
          {
            int count = 0;
            if (inEventLoop())
            {
              Runnable task;
              while (count < maxElements && (task = fastQueue.poll()) != null)
              {
                count++;
                c.add(task);
              }
            }
            if (count < maxElements)
              count += threadSafeQueue.drainTo(c, maxElements - count);
            return count;
          }
        };
      }

      @Override
      protected void run() {
        for (;;) {
          Runnable task = takeTask();
          if (task != null) {
            task.run();
            updateLastExecutionTime();
          }

          if (confirmShutdown()) {
            break;
          }
        }
      }
    };
  }
}
