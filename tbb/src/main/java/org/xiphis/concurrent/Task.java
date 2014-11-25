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

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiphis.utils.common.ConcurrentIdentityHashMap;
import org.xiphis.utils.common.Factory;
import org.xiphis.utils.common.Utils;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * The task scheduler is the engine that powers the {@link Parallel} constructions.
 *
 * <h2>Blocking Style with Children</h2>
 * <pre>{@code
 * class T extends Task
 * {
 *   public Task execute() throws Exception
 *   {
 *     if (not recursing any further)
 *     {
 *       ...
 *     }
 *     else
 *     {
 *       setRefCount(k + 1);
 *       Task tk = allocateChild(taskFactoryk, args...); spawn(tk);
 *       ...
 *       Task t1 = allocateChild(taskFactory1, args...); spawnAndWaitForAll(t1);
 *     }
 *     return null;
 *   }
 * }
 * }</pre>
 *
 * <h2>Recycling the parent as the continuation</h2>
 * <pre>{@code
 * class T extends Task
 * {
 *   public Task execute() throws Exception
 *   {
 *     if (not recursing any further)
 *     {
 *       ...
 *       return null;
 *     }
 *     else
 *     {
 *       recycleAsContinuation();
 *       setRefCount(k);
 *       Task tk = allocateChild(taskFactoryk, args...); spawn(tk);
 *       ...
 *       Task t1 = allocateChild(taskFactory1, args...);
 *       return t1;
 *     }
 *   }
 * }
 * }</pre>
 *
 * <h2>Recycling the parent as a child</h2>
 * <pre>{@code
 * class T extends Task
 * {
 *   public Task execute() throws Exception
 *   {
 *     if (not recursing any further)
 *     {
 *       ...
 *       return null;
 *     }
 *     else
 *     {
 *       setRefCount(k);
 *       Task c = allocateContinuation(EmptyTask.FACTORY);
 *       Task tk = c.allocateChild(taskFactoryk, args...); spawn(tk);
 *       ...
 *       Task t2 = c.allocateChild(taskFactory2, args...); spawn(t2);
 *       recycleAsChildOf(c);
 *       ... update fields of this t state subproblem to be solved by t1.
 *       return this;
 *     }
 *   }
 * }
 * }</pre>
 *
 * @author atcurtis
 * @since 2014-8-23
 */
public abstract class Task
{
  private static final Logger LOG = LoggerFactory.getLogger(Task.class);

  private static class Prefix
  {
    private Prefix parent;
    private Task task;
    private volatile int refCount;
    private int depth;
  }

  private static final long refCountOffset;
  private static final long singleThreadEventExecutorTaskQueue;
  private static final long singleThreadEventExecutorDelayedTaskQueue;
  static
  {
    try
    {
      refCountOffset = Utils.getUnsafe().objectFieldOffset(Prefix.class.getDeclaredField("refCount"));

      singleThreadEventExecutorTaskQueue =
          Utils.getUnsafe().objectFieldOffset(SingleThreadEventExecutor.class.getDeclaredField("taskQueue"));
      singleThreadEventExecutorDelayedTaskQueue =
          Utils.getUnsafe().objectFieldOffset(SingleThreadEventExecutor.class.getDeclaredField("delayedTaskQueue"));
    }
    catch (NoSuchFieldException e)
    {
      throw new RuntimeException(e);
    }
  }

  private Group context;
  private Prefix prefix;
  private Scheduler owner;
  private State state;
  private int affinity;
  private boolean stolen;

  private static volatile int statgen;

  /**
   *
   * @return
   * @throws Exception
   */
  protected abstract Task execute() throws Exception;


  private static final ReferenceQueue<Scheduler[]> referenceQueue = new ReferenceQueue<>();
  private static final ConcurrentIdentityHashMap<Reference<?>,Scheduler> map = new ConcurrentIdentityHashMap<>();
  private static final ThreadLocal<Scheduler[]> SCHEDULER = new ThreadLocal<>();

  public static void printStats()
  {
    statgen++;
  }

  private static Scheduler getOrCreateScheduler()
  {
    Scheduler[] r = SCHEDULER.get();
    if (r == null)
    {
      Reference<? extends Scheduler[]> ref = referenceQueue.poll();
      Scheduler s;
      if (ref == null || (s = map.remove(ref)) == null)
      {
        s = new Scheduler();
        if (LOG.isDebugEnabled())
          LOG.debug("Created new scheduler for " + Thread.currentThread().getName());
      }
      else
      {
        if (LOG.isDebugEnabled())
          LOG.debug("Recycling a scheduler for " + Thread.currentThread().getName());
      }
      map.put(new PhantomReference<>(r = new Scheduler[] {s}, referenceQueue), s);
      SCHEDULER.set(r);
    }
    return r[0];
  }

  private static Scheduler getScheduler()
  {
    return SCHEDULER.get()[0];
  }

  /**
   *
   * @return
   */
  public static Task currentTask()
  {
    return getScheduler().currentTask();
  }

  /**
   *
   * @return
   */
  public static TaskGroupContext currentContext() {
    Scheduler[] r = SCHEDULER.get();
    return r != null && r[0] != null ? r[0].currentContext() : null;
  }

  /**
   *
   * @return
   */
  public final TaskGroupContext context()
  {
    return context.self();
  }

  /**
   *
   * @return
   */
  public final Task parent()
  {
    return prefix.parent != null ? prefix.parent.task : null;
  }

  /**
   *
   * @return
   */
  public final int depth()
  {
    return prefix.depth;
  }

  /**
   *
   * @return
   */
  protected final boolean isStolenTask()
  {
    return stolen;
  }

  /**
   *
   * @param refCount
   */
  public final void setRefCount(int refCount)
  {
    assert refCount >= 0 : "count must not be negative";
    Prefix p = prefix;
    assert p.refCount == 1 && state == State.allocated && p.task == this : "refCount race detected";
    p.refCount = refCount;
  }

  public final int decrementRefCount()
  {
    int k = Utils.getUnsafe().getAndAddInt(prefix, refCountOffset, -1);
    assert k >= 1 : "task's reference count underflowed";
    return k-1;
  }

  private static void setContext(Task task, TaskGroupContext context)
  {
    if (task == null || task.context != null || task.prefix != null)
      throw new IllegalStateException("Bad Factory");
    (task.prefix = new Prefix()).task = task;
    task.context = context;
    task.state = State.allocated;
  }

  /**
   *
   * @param factoryObject
   * @param args
   * @param <T> Generic type of Task {@link Factory}
   * @return
   */
  public static <T extends Task> T allocateRoot(Factory<T> factoryObject, Object... args)
  {
    TaskGroupContext context = new TaskGroupContext();
    T t = factoryObject.construct(args);
    setContext(t, context);
    return t;
  }

  /**
   *
   * @param context
   * @param factoryObject
   * @param args
   * @param <T> Generic type of Task {@link Factory}
   * @return
   */
  public static <T extends Task> T allocateRoot(TaskGroupContext context, Factory<T> factoryObject, Object... args)
  {
    if (context == null)
      throw new NullPointerException("context");
    T t = factoryObject.construct(args);
    setContext(t, context);
    return t;
  }

  /**
   *
   * @param tasks
   */
  public static void spawnRootAndWait(Task... tasks)
  {
    if (tasks.length == 0)
      return;
    spawnRootAndWait0(new LinkedList<>(Arrays.asList(tasks)));
  }

  /**
   *
   * @param tasks
   */
  public static void spawnRootAndWait(List<Task> tasks)
  {
    if (tasks.isEmpty()) return;
    spawnRootAndWait0(new LinkedList<>(tasks));
  }

  /**
   *
   * @param context
   * @param tasks
   * @throws InterruptedException
   */
  public static void spawnRootAndWait(TaskGroupContext context, Task... tasks)
      throws InterruptedException
  {
    if (tasks.length == 0)
      return;
    spawnRootAndWait0(context, new LinkedList<>(Arrays.asList(tasks)));
  }

  /**
   *
   * @param context
   * @param tasks
   * @throws InterruptedException
   */
  public static void spawnRootAndWait(TaskGroupContext context, List<Task> tasks)
      throws InterruptedException
  {
    if (tasks.isEmpty()) return;
    spawnRootAndWait0(context, new LinkedList<>(tasks));
  }

  private static void spawnRootAndWait0(LinkedList<Task> tasks)
  {
    for (Task t : tasks)
      if (t.context == null || t.prefix.parent != null)
        throw new IllegalArgumentException("not a root task, or already running");
    getOrCreateScheduler().spawnRootAndWait(tasks);
  }

  private static void spawnRootAndWait0(TaskGroupContext context, LinkedList<Task> tasks)
      throws InterruptedException
  {
    for (Task t : tasks)
      if (t.context == null || t.prefix.parent != null)
        throw new IllegalArgumentException("not a root task, or already running");
    EventExecutor executor = context.eventExecutorGroup().next();
    executor.submit(() ->
    {
      getOrCreateScheduler().spawnRootAndWait(tasks);
    }).sync();
  }

  private static void resetExtraState(Task t)
  {

  }

  /**
   *
   * @param t task to destroy.
   */
  public static void destroy(Task t)
  {
    assert t.prefix == null || t.prefix.refCount <= 1 : "task being destroyed must not have children";
    //assert t.state == State.allocated : "illegal state for victim task";
    //Prefix parent = t.prefix != null ? t.prefix.parent : null;
    t.prefix.task = null;
    t.prefix = null;
    t.state = State.freed;
    //if (parent != null)
    //{
    //  assert parent.task != null && parent.task.state != State.freed && parent.task.state != State.ready
    //      : "attempt to destroy child of running or corrupted parent?";
    //  parent.task.decrementRefCount();
    //  // Even if the last reference to *parent is removed, it should not be spawned (documented behaviour).
    //}
  }

  /**
   *
   * @return
   */
  public final boolean isOwnedByCurrentThread()
  {
    return owner != null && getScheduler() == owner;
  }

  /**
   * Set scheduling depth to given value.
   * <p/>
   * The depth must be non-negative.
   *
   * @param new_depth
   */
  public final void setDepth(int new_depth)
  {
    assert state != State.ready : "cannot change depth of ready task";
    assert new_depth >= 0 : "depth cannot be negative";
    prefix.depth = new_depth;
  }

  /**
   * Change scheduling depth by given amount.
   * <p/>
   * The resulting depth must be non-negative.
   *
   * @param delta
   */
  public final void addToDepth(int delta)
  {
    assert state != State.ready : "cannot change depth of ready task";
    int new_depth = prefix.depth + delta;
    assert new_depth >= 0 : "depth cannot be negative";
    prefix.depth = new_depth;
  }

  /**
   *
   * @param child
   */
  public final void spawn(Task child)
  {
    assert isOwnedByCurrentThread() : "'this' not owned by current thread";
    child.context.enqueue(owner, child, null);
  }

  /**
   *
   * @param child
   */
  public final void spawn(Iterable<Task> child)
  {
    assert isOwnedByCurrentThread() : "'this' not owned by current thread";
    owner.spawn(child);
  }

  /**
   * Similar to spawn followed by waitForAll, but more efficient.
   *
   * @param child
   */
  public final void spawnAndWaitForAll(Task child)
  {
    assert isOwnedByCurrentThread() : "'this' not owned by current thread";
    owner.waitForAll(this, child);
  }

  private static void initTask(Task task, Task parent, int depth, Group context, Scheduler owner)
  {
    Prefix prefix = new Prefix();
    prefix.depth = depth;
    prefix.parent = parent.prefix;
    initTask(task, prefix, context, owner);
  }

  private static void initTask(Task task, Prefix prefix, Group context, Scheduler owner)
  {
    prefix.task = task;
    task.context = context;
    task.owner = owner;
    task.prefix = prefix;
    task.state = State.allocated;
  }

  /**
   * Returns a child task of this.
   *
   * @param <T> Generic type of Task {@link Factory}
   * @param cls Instance of Task {@link Factory}. Lambda can be used here.
   * @return  a task initialized as a child.
   */
  public final <T extends Task> T allocateChild(Factory<T> cls, Object... arguments)
  {
    if (!isOwnedByCurrentThread())
      throw new IllegalStateException("thread does not own this");
    T t = cls.construct(arguments);
    initTask(t, this, prefix.depth + 1, context, owner);
    return t;
  }

  /**
   * Returns a continuation task of *this.
   * <p/>
   * The continuation's parent becomes the parent of this.
   *
   * @param <T> Generic type of Task {@link Factory}
   * @param cls Instance of Task {@link Factory}. Lambda can be used here.
   * @return a task initialized as a continuation.
   */
  protected final <T extends Task> T allocateContinuation(Factory<T> cls, Object... arguments)
  {
    if (!isOwnedByCurrentThread())
      throw new IllegalStateException("thread does not own this");
    T t = cls.construct(arguments);
    initTask(t, prefix, context, owner);
    this.prefix = null;
    return t;
  }

  /**
   * Like allocateChild, except that task's parent becomes "parent", not this.
   * <p/>
   * Typically used in conjunction with schedule_to_reexecute to implement
   * while loops.
   * <p/>
   * Atomically increments the reference count of t.parent()
   *
   * @param <T> Generic type of Task {@link Factory}
   * @param cls Instance of Task {@link Factory}. Lambda can be used here.
   * @param parent Parent.
   * @return a task initialized as a child.
   */
  protected final <T extends Task> T allocateAdditionalChildOf(Task parent, Factory<T> cls, Object... arguments)
  {
    T t = cls.construct(arguments);
    initTask(t, parent, parent.prefix.depth + 1, parent.context, parent.owner);
    return t;
  }

  // ------------------------------------------------------------------------
  // Recycling of tasks
  // ------------------------------------------------------------------------

  /**
   * Change this to be a continuation of its former self.
   * <p/>
   * The caller must guarantee that the task's refcount does not become zero
   * until after the method execute() returns. Typically, this is done by
   * having method execute() return a pointer to a child of the task. If the
   * guarantee cannot be made, use method recycleAsSafeContinuation instead.
   * <p/>
   * Because of the hazard, this method may be deprecated in the future.
   */
  protected final void recycleAsContinuation()
  {
    assert state == State.executing : "execute not running?";
    state = State.allocated;
  }

  /**
   * Recommended to use, safe variant of recycleAsContinuation.
   * <p/>
   * For safety, it requires additional increment of _refCount.
   */
  protected final void recycleAsSafeContinuation()
  {
    assert state == State.executing : "execute not running?";
    state = State.recycle;
  }

  /**
   * Change this to be a child of new_parent.
   *
   * @param new_parent
   */
  protected final void recycleAsChildOf(Task new_parent)
  {
    if (new_parent == null)
      throw new NullPointerException();
    assert state == State.executing || state == State.allocated : "execute not running, or already recycled";
    assert prefix == null || prefix.refCount == 0  : "no child tasks allowed when recycled as a child";
    assert prefix == null || prefix.parent == null : "parent must be null";
    assert new_parent.state != null && new_parent.state != State.freed : "parent already freed";
    state = State.allocated;
    if (prefix == null)
      prefix = new Prefix();
    prefix.parent = new_parent.prefix;
    prefix.depth = new_parent.prefix.depth + 1;
    context = new_parent.context;
  }

  /**
   * Schedule this for reexecution after current execute() returns.
   * <p/>
   * Requires that this.execute() be running.
   */
  protected final void recycleToReexecute()
  {
    assert state == State.executing : "execute not running, or already recycled";
    assert prefix.refCount == 0 : "no child tasks allowed when recycled for reexecution";
    state = State.reexecute;
  }


  final static class Scheduler
  {
    private final Task workerTask = new EmptyTask();
    private final Deque<Task> localBypass = new ArrayDeque<>();
    private final ConcurrentLinkedQueue<Task> submitted = new ConcurrentLinkedQueue<>();
    private final ArrayList<Task> parents = new ArrayList<>();
    private final AtomicInteger runCount = new AtomicInteger();

    private EventExecutor eventExecutor;
    private Task current;
    private Group context;
    private int stat;

    private int innerStart;
    private int executeCount;
    private int continueCount;
    private int localBypassCount;
    private int submittedCount;
    private int pushedCount;
    private int innerExits;
    private int stolenCount;
    private int spinCount;

    public Task currentTask()
    {
      return current;
    }

    @SuppressWarnings("unchecked")
    public TaskGroupContext currentContext()
    {
      return (TaskGroupContext) context;
    }

    public void spawnRootAndWait(LinkedList<Task> tasks)
    {
      for (Task t : tasks)
        if (t.parent() != null)
          throw new IllegalStateException("non-root tasks");
      Task dummy = new EmptyTask();
      (dummy.prefix = new Prefix()).task = dummy;
      Task first = tasks.removeFirst();
      dummy.context = first.context;
      int n = 1;
      first.prefix.parent = dummy.prefix;
      for (Task t : tasks)
      {
        t.prefix.parent = dummy.prefix;
        n++;
      }
      dummy.prefix.refCount = n + 1;
      if (!tasks.isEmpty()) spawn(tasks);
      waitForAll(dummy, first);
    }

    private void waitForAll(Task parent, Task child)
    {
      if (parent.prefix.refCount < (child != null && child.prefix.parent == parent.prefix ? 2 : 1))
          throw new IllegalStateException("refCount is too small");
      executeLoop(parent, child);
    }

    private Task executeInner(Task t)
    {
      Task savedCurrent = current;
      Group savedContext = context;
      Task next = null;
      current = t;
      context = t.context;
      if (t.owner != null && t.owner != this) t.stolen = true;
      t.owner = this;
      t.state = State.executing;
      try
      {
        Thread currentThread = Thread.currentThread();
        if (!t.context.isGroupExecutionCancelled())
        {
          try
          {
            executeCount++;
            currentThread.isInterrupted(); // clear flag
            next = t.execute();
            if (currentThread.isInterrupted())
            {
              t.cancelGroupExecution();
            }
            if (next != null)
            {
              assert next.state == State.allocated;
              resetExtraState(next);
            }
          }
          catch (Throwable ex)
          {
            t.context.cancelGroupExecution(ex);
          }
          finally
          {
            assert current == t;
          }
        }
        switch (t.state)
        {
        case executing:
        {
          Prefix s = t.prefix.parent;
          assert t.prefix.refCount == 0 :
              "Task still has children after it has been executed";
          destroy(t);
          if (s != null)
            next = tallyCompletionOfPredecessor(s, next);
          break;
        }
        case recycle:
          t.state = State.allocated;
        case to_enqueue:
          assert next != t : "a task returned from execute() can not be recycled in another way";
          resetExtraState(t);
          next = tallyCompletionOfPredecessor(t.prefix, next);
          break;
        case reexecute:
          assert next != null : "reexecution requires that execute() return another task";
          assert next != t : "a task returned from execute() can not be recycled in another way";
          t.state = State.allocated;
          resetExtraState(t);
          spawn(t);
          break;
        case allocated:
          resetExtraState(t);
          break;
        default:
          break;
        }

        return next;
      }
      finally
      {
        current = savedCurrent;
        context = savedContext;
      }
    }

    private void executeLoop(final Task parent, Task t)
    {
      final Thread currentThread = Thread.currentThread();
      if (stat != statgen)
      {
        stat = statgen;
        System.out.println("Status for " + currentThread.getName() +
            " innerStart:" + innerStart +
            " executed:" + executeCount +
            " continuation:" + continueCount +
            " local:" + localBypassCount +
            " submitted:" + submittedCount +
            " pushed:" + pushedCount +
            " stolen:" + stolenCount +
            " innerExits:" + innerExits +
            " spins:" + spinCount);
        innerStart = 0;
        executeCount = 0;
        continueCount = 0;
        localBypassCount = 0;
        submittedCount = 0;
        pushedCount = 0;
        stolenCount = 0;
        innerExits = 0;
        spinCount = 0;
      }

      parents.add(parent);
      innerStart++;
      long idle = -1;
      try
      {
        loop:
        for (;;)
        {
          while (t != null)
          {
            idle = -1;
            if ((t = executeInner(t)) != null)
              continueCount++;
          }

          for (Task p : parents)
          {
            if (p != workerTask && p.prefix.refCount == 1)
            {
              innerExits++;

              if (parents.size() == 1)
              {
                if (!localBypass.isEmpty() || !submitted.isEmpty())
                {
                  ArrayList<Task> tasks = new ArrayList<>(localBypass);
                  localBypass.clear();
                  Task drain;
                  while ((drain = submitted.poll()) != null)
                    tasks.add(drain);
                  Iterator<Task> it = tasks.iterator();
                  Task first = it.next();
                  first.context.enqueue(this, first, it);
                }
              }

              break loop;
            }
          }

          if ((t = localBypass.pollFirst()) != null)
          {
            localBypassCount++;
            continue;
          }

          if ((t = submitted.poll()) != null)
          {
            int maxDrain = 8;
            int count = 0;
            Task drain;
            while (count < maxDrain && (drain = submitted.poll()) != null)
            {
              localBypass.add(drain);
              count++;
            }
            submittedCount += count + 1;
            continue;
          }

          // maybe we can steal a task?
          EventExecutor victimExecutor = parent.context.eventExecutors.next();
          Scheduler victim;
          if (!victimExecutor.inEventLoop(currentThread) &&
              (victim = parent.context.runMap.get(victimExecutor)) != null)
          {
            t = victim.submitted.poll();
            if (t != null)
            {
              stolenCount++;
              t.stolen = true;

              if (parent == workerTask || localBypass.isEmpty())
              {
                int maxSteals = victim.submitted.size() / 2 - 1;

                Task stolen;
                while (maxSteals-- > 0 && (stolen = victim.submitted.poll()) != null)
                {
                  stolenCount++;
                  stolen.stolen = true;
                  localBypass.addLast(stolen);
                }
              }
              continue;
            }
          }

          if (parent != workerTask)
          {
            if (!executeOneTask(eventExecutor))
              continue;

            spinCount++;
            continue;
          }

          if (idle < 0)
          {
            idle = System.nanoTime() + 10;
            spinCount++;
            continue;
          }
          if (idle > System.nanoTime())
          {
            spinCount++;
            Utils.Yield();
            continue;
          }

          break;
        }
      }
      finally
      {
        Task p = parents.remove(parents.size()-1);
        assert p == parent;
      }
    }

    private Task tallyCompletionOfPredecessor(Prefix s, Task bypass)
    {

      if (Utils.getUnsafe().getAndAddInt(s, refCountOffset, -1) > 1)
        return bypass;

      if (s.task.state == State.to_enqueue)
      {
        s.task.context.enqueue(this, s.task, null);
      }
      else
      if (bypass == null)
      {
        bypass = s.task;
      }
      else
      {
        localBypass.addFirst(s.task);
      }
      return bypass;
    }


    private void spawn(Task task)
    {
      if (context == task.context && localBypass.isEmpty())
      {
        localBypass.addFirst(task);
      }
      else
        task.context.enqueue(this, task, null);
    }

    private void spawn(Iterable<Task> tasks)
    {
      Iterator<Task> it = tasks.iterator();
      if (it.hasNext())
      {
        Task task = it.next();
        if (!it.hasNext())
          it = null;
        task.context.enqueue(this, task, it);
      }
    }

    public void execute(EventExecutor executor, TaskGroupContext taskGroupContext, Task work)
    {
      Group group = taskGroupContext;
      if (group.runMap.putIfAbsent(executor, this) == null)
      {
        try
        {
          eventExecutor = executor;
          workerTask.context = group;
          executeLoop(workerTask, work);
        }
        finally
        {
          group.runMap.remove(executor);
          eventExecutor = null;
        }
      }
      else
      {
        localBypass.addFirst(work);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static boolean executeOneTask(EventExecutor executor)
  {
    Runnable task = null;
    if (executor instanceof SingleThreadEventExecutor)
    {
      Queue<ScheduledFuture<?>> delayedTaskQueue = (Queue<ScheduledFuture<?>>)
          Utils.getUnsafe().getObject(executor, singleThreadEventExecutorDelayedTaskQueue);
      ScheduledFuture<?> delayedTask = delayedTaskQueue.peek();
      if (delayedTask == null || delayedTask.getDelay(TimeUnit.NANOSECONDS) > 0)
      {
        Queue<Runnable> taskQueue = (Queue<Runnable>) Utils.getUnsafe().getObject(executor,
                                                                                  singleThreadEventExecutorTaskQueue);
        task = taskQueue.poll();
      }
      else
      {
        task = (Runnable) delayedTaskQueue.poll();
      }
    }
    if (task != null)
    {
      try
      {
        task.run();
      }
      catch (Throwable t)
      {
        LOG.warn("A task raised an exception.", t);
      }
      return true;
    }
    return false;
  }

  /**
   * Created by atcurtis on 8/23/14.
   */
  static abstract class Group
  {
    private final EventExecutorGroup eventExecutors;
    private volatile Throwable groupExecutionCancelled;
    private final ConcurrentIdentityHashMap<EventExecutor, Scheduler> runMap = new ConcurrentIdentityHashMap<>();

    Group()
    {
      this(getScheduler().context);
    }

    Group(Group context)
    {
      this(context.eventExecutors);
    }

    Group(EventExecutor eventExecutor)
    {
      this(eventExecutor.inEventLoop() && getOrCreateScheduler() != null ? eventExecutor.parent() : null);
      Scheduler scheduler = getScheduler();
      if (eventExecutor.inEventLoop() && scheduler.context == null)
        scheduler.context = self();
    }

    public Group(EventExecutorGroup eventExecutors)
    {
      if (eventExecutors == null || eventExecutors.next() == null)
        throw new IllegalArgumentException();

      this.eventExecutors = eventExecutors;
    }

    protected abstract TaskGroupContext self();

    public EventExecutorGroup eventExecutorGroup()
    {
      return eventExecutors;
    }

    public void prepare()
        throws InterruptedException
    {
      for (EventExecutor executor : eventExecutorGroup())
      {
        Task task = new EmptyTask();
        setContext(task, self());
        executor.submit(() -> send(executor, task)).sync();
      }
    }

    private void send(EventExecutor executor, Task work)
    {
      getOrCreateScheduler().execute(executor, self(), work);
    }

    private void enqueue(Scheduler scheduler, Task task, Iterator<Task> it)
    {
      if (task == null)
        throw new NullPointerException();
      if (task.state != State.allocated && task.state != State.to_enqueue)
        throw new IllegalStateException();

      EventExecutor executor = eventExecutors.next();
      boolean inEventLoop = executor.inEventLoop();
      final Scheduler nextScheduler;

      if (inEventLoop)
      {
        nextScheduler = getOrCreateScheduler();
        if (!nextScheduler.parents.isEmpty())
        {
          //if (nextScheduler.localBypass.isEmpty())
          //  nextScheduler.localBypass.add(task);
          //else
          //  nextScheduler.submitted.add(task);
          nextScheduler.localBypass.add(task);
          while (it != null && it.hasNext())
            nextScheduler.submitted.add(it.next());
          return;
        }
      }
      else
      {
        nextScheduler = runMap.get(executor);
      }

      if (nextScheduler == null)
      {
        while (it != null && it.hasNext())
        {
          Task pushed = it.next();
          scheduler.pushedCount++;
          pushed.stolen = true;
          nextScheduler.submitted.add(pushed);
        }
        scheduler.pushedCount++;
        task.stolen = true;
        executor.execute(() -> send(executor, task));
      }
      else
      {
        // we add the task to the local stealable queue and the wake up the other executor.
        scheduler.submitted.add(task);
        while (it != null && it.hasNext())
          scheduler.submitted.add(it.next());

        for (;;)
        {
          int count = nextScheduler.runCount.get();
          if (count < 1)
          {
            if (!nextScheduler.runCount.compareAndSet(count, count + 1)) continue;
            executor.execute(() -> {
              Task idle = new EmptyTask();
              setContext(idle, task.context.self());
              send(executor, idle);
              nextScheduler.runCount.getAndDecrement();
            });
            break;
          }
          break;
        }
      }
    }

    /**
     *
     * @return
     */
    public boolean isGroupExecutionCancelled()
    {
      return groupExecutionCancelled != null;
    }

    /**
     *
     * @return
     */
    public boolean cancelGroupExecution()
    {
      if (isGroupExecutionCancelled())
        return false;
      return cancelGroupExecution(new CancellationException());
    }

    private boolean cancelGroupExecution(Throwable cause)
    {
      if (cause == null)
        throw new NullPointerException();
      if (isGroupExecutionCancelled())
        return false;
      this.groupExecutionCancelled = cause;
      return true;
    }
  }

  /**
   * Set affinity for this task.
   *
   * @param id
   */
  public final void setAffinity(int id)
  {
    affinity = id;
  }

  /**
   * Current affinity of this task
   *
   * @return
   */
  public final int affinity()
  {
    return affinity;
  }

  /**
   * Invoked by scheduler to notify task that it ran on unexpected thread.
   * <p/>
   * Invoked before method execute() runs, if task is stolen, or task has
   * affinity but will be executed on another thread.
   * <p/>
   * The default action does nothing.
   */
  public void noteAffinity(int my_affinity_id)
  {
    // do nothing
  }

  // ------------------------------------------------------------------------
  // Affinity
  // ------------------------------------------------------------------------

  /**
   * Initiates cancellation of all tasks in this cancellation group and its
   * subordinate groups.
   * <p/>
   *
   * @return false if cancellation has already been requested, true otherwise.
   */
  public final boolean cancelGroupExecution()
  {
    return context().cancelGroupExecution();
  }

  // ------------------------------------------------------------------------
  // Task cancellation
  // ------------------------------------------------------------------------

  /**
   * Returns true if the context received cancellation request.
   *
   * @return
   */
  public final boolean isCancelled()
  {
    return context().isGroupExecutionCancelled();
  }

  /**
   * Enumeration of task states that the scheduler considers.
   *
   * @author atcurtis
   */
  public static enum State
  {
    /**
     * task is running, and will be destroyed after method execute()
     * completes.
     */
    executing,

    /**
     * task to be rescheduled.
     */
    reexecute,

    /**
     * task is in ready pool, or is going to be put there, or was just taken
     * off.
     */
    ready,

    /**
     * task object is freshly allocated or recycled.
     */
    allocated,

    /**
     * task object is on free list, or is going to be put there, or was just
     * taken off.
     */
    freed,

    /**
     * task to be recycled as continuation
     */
    recycle,

    /**
     *
     */
    to_enqueue
  }
}
