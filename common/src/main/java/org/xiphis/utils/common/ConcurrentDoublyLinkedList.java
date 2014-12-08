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

/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A concurrent linked-list implementation of a {@link java.util.Deque} (double-ended
 * queue). Concurrent insertion, removal, and access operations execute safely
 * across multiple threads. Iterators are <i>weakly consistent</i>, returning
 * elements reflecting the state of the deque at some point at or since the
 * creation of the iterator. They do <em>not</em> throw {@link
 * java.util.ConcurrentModificationException}, and may proceed concurrently with other
 * operations.
 *
 * <p>
 * This class and its iterators implement all of the <em>optional</em> methods
 * of the {@link java.util.Collection} and {@link java.util.Iterator} interfaces. Like most other
 * concurrent collection implementations, this class does not permit the use of
 * <tt>null</tt> elements. because some null arguments and return values
 * cannot be reliably distinguished from the absence of elements. Arbitrarily,
 * the {@link java.util.Collection#remove} method is mapped to
 * <tt>removeFirstOccurrence</tt>, and {@link java.util.Collection#add} is mapped to
 * <tt>addLast</tt>.
 *
 * <p>
 * Beware that, unlike in most collections, the <tt>size</tt> method is
 * <em>NOT</em> a constant-time operation. Because of the asynchronous nature
 * of these deques, determining the current number of elements requires a
 * traversal of the elements.
 *
 * <p>
 * This class is <tt>Serializable</tt>, but relies on default serialization
 * mechanisms. Usually, it is a better idea for any serializable class using a
 * <tt>ConcurrentLinkedDeque</tt> to instead serialize a snapshot of the
 * elements obtained by method <tt>toArray</tt>.
 *
 * @author Doug Lea
 * @param <E>
 *            the type of elements held in this collection
 */

public class ConcurrentDoublyLinkedList<E> extends AbstractCollection<E>
    implements java.io.Serializable {

  /*
   * This is an adaptation of an algorithm described in Paul Martin's "A
   * Practical Lock-Free Doubly-Linked List". Sun Labs Tech report. The basic
   * idea is to primarily rely on next-pointers to ensure consistency.
   * Prev-pointers are in part optimistic, reconstructed using forward
   * pointers as needed. The main forward list uses a variant of HM-list
   * algorithm similar to the one used in ConcurrentSkipListMap class, but a
   * little simpler. It is also basically similar to the approach in Edya
   * Ladan-Mozes and Nir Shavit "An Optimistic Approach to Lock-Free FIFO
   * Queues" in DISC04.
   *
   * Quoting a summary in Paul Martin's tech report:
   *
   * All cleanups work to maintain these invariants: (1) forward pointers are
   * the ground truth. (2) forward pointers to dead nodes can be improved by
   * swinging them further forward around the dead node. (2.1) forward
   * pointers are still correct when pointing to dead nodes, and forward
   * pointers from dead nodes are left as they were when the node was deleted.
   * (2.2) multiple dead nodes may point forward to the same node. (3)
   * backward pointers were correct when they were installed (3.1) backward
   * pointers are correct when pointing to any node which points forward to
   * them, but since more than one forward pointer may point to them, the live
   * one is best. (4) backward pointers that are out of date due to deletion
   * point to a deleted node, and need to point further back until they point
   * to the live node that points to their source. (5) backward pointers that
   * are out of date due to insertion point too far backwards, so shortening
   * their scope (by searching forward) fixes them. (6) backward pointers from
   * a dead node cannot be "improved" since there may be no live node pointing
   * forward to their origin. (However, it does no harm to try to improve them
   * while racing with a deletion.)
   *
   *
   * Notation guide for local variables n, b, f : a node, its predecessor, and
   * successor s : some other successor
   */

  // Minor convenience utilities

  /**
   * Returns true if given reference is non-null and isn't a header, trailer,
   * or marker.
   *
   * @param n
   *            (possibly null) node
   * @return true if n exists as a user node
   */
  private static boolean usable(ConcurrentDoublyLinkedNode<?> n) {
    return n != null && !n.isSpecial();
  }

  /**
   * Throws NullPointerException if argument is null
   *
   * @param v
   *            the element
   */
  private static void checkNullArg(Object v) {
    if (v == null)
      throw new NullPointerException();
  }

  /**
   * Returns element unless it is null, in which case throws
   * NoSuchElementException.
   *
   * @param v
   *            the element
   * @return the element
   */
  private E screenNullResult(E v) {
    if (v == null)
      throw new NoSuchElementException();
    return v;
  }

  /**
   * Creates an array list and fills it with elements of this list. Used by
   * toArray.
   *
   * @return the arrayList
   */
  private ArrayList<E> toArrayList() {
    ArrayList<E> c = new ArrayList<E>();
    for (ConcurrentDoublyLinkedNode<E> n = header.forward(); n != null; n = n.forward())
      c.add(n.getElement());
    return c;
  }

  // Fields and constructors

  private static final long serialVersionUID = 876323262645176354L;

  /**
   * List header. First usable node is at header.forward().
   */
  private final ConcurrentDoublyLinkedNode<E> header;

  /**
   * List trailer. Last usable node is at trailer.back().
   */
  private final ConcurrentDoublyLinkedNode<E> trailer;

  /**
   * Constructs an empty deque.
   */
  @SuppressWarnings("unchecked")
  public ConcurrentDoublyLinkedList() {
    ConcurrentDoublyLinkedNode h = new ConcurrentDoublyLinkedNode(null, null, null);
    ConcurrentDoublyLinkedNode t = new ConcurrentDoublyLinkedNode(null, null, h);
    h.setNext(t);
    header = h;
    trailer = t;
  }

  /**
   * Constructs a deque containing the elements of the specified collection,
   * in the order they are returned by the collection's iterator.
   *
   * @param c
   *            the collection whose elements are to be placed into this
   *            deque.
   * @throws NullPointerException
   *             if <tt>c</tt> or any element within it is <tt>null</tt>
   */
  public ConcurrentDoublyLinkedList(Collection<? extends E> c) {
    this();
    addAll(c);
  }

  /**
   * Prepends the given element at the beginning of this deque.
   *
   * @param o
   *            the element to be inserted at the beginning of this deque.
   * @throws NullPointerException
   *             if the specified element is <tt>null</tt>
   */
  public void addFirst(E o) {
    checkNullArg(o);
    while (header.append(o) == null)
      ;
  }

  /**
   * Appends the given element to the end of this deque. This is identical in
   * function to the <tt>add</tt> method.
   *
   * @param o
   *            the element to be inserted at the end of this deque.
   * @throws NullPointerException
   *             if the specified element is <tt>null</tt>
   */
  public void addLast(E o) {
    checkNullArg(o);
    while (trailer.prepend(o) == null)
      ;
  }

  public ConcurrentDoublyLinkedNode<E> addLastNode(E o) {
    checkNullArg(o);
    ConcurrentDoublyLinkedNode<E> node;
    while ((node = trailer.prepend(o)) == null)
      ;
    return node;
  }

  /**
   * Prepends the given element at the beginning of this deque.
   *
   * @param o
   *            the element to be inserted at the beginning of this deque.
   * @return <tt>true</tt> always
   * @throws NullPointerException
   *             if the specified element is <tt>null</tt>
   */
  public boolean offerFirst(E o) {
    addFirst(o);
    return true;
  }

  /**
   * Appends the given element to the end of this deque. (Identical in
   * function to the <tt>add</tt> method; included only for consistency.)
   *
   * @param o
   *            the element to be inserted at the end of this deque.
   * @return <tt>true</tt> always
   * @throws NullPointerException
   *             if the specified element is <tt>null</tt>
   */
  public boolean offerLast(E o) {
    addLast(o);
    return true;
  }

  /**
   * Retrieves, but does not remove, the first element of this deque, or
   * returns null if this deque is empty.
   *
   * @return the first element of this queue, or <tt>null</tt> if empty.
   */
  public E peekFirst() {
    ConcurrentDoublyLinkedNode<E> n = header.successor();
    return (n == null) ? null : n.getElement();
  }

  /**
   * Retrieves, but does not remove, the last element of this deque, or
   * returns null if this deque is empty.
   *
   * @return the last element of this deque, or <tt>null</tt> if empty.
   */
  public E peekLast() {
    ConcurrentDoublyLinkedNode<E> n = trailer.predecessor();
    return (n == null) ? null : n.getElement();
  }

  /**
   * Returns the first element in this deque.
   *
   * @return the first element in this deque.
   * @throws java.util.NoSuchElementException
   *             if this deque is empty.
   */
  public E getFirst() {
    return screenNullResult(peekFirst());
  }

  /**
   * Returns the last element in this deque.
   *
   * @return the last element in this deque.
   * @throws java.util.NoSuchElementException
   *             if this deque is empty.
   */
  public E getLast() {
    return screenNullResult(peekLast());
  }

  /**
   * Retrieves and removes the first element of this deque, or returns null if
   * this deque is empty.
   *
   * @return the first element of this deque, or <tt>null</tt> if empty.
   */
  public E pollFirst() {
    for (;;) {
      ConcurrentDoublyLinkedNode<E> n = header.successor();
      if (!usable(n))
        return null;
      if (n.delete())
        return n.getElement();
    }
  }

  /**
   * Retrieves and removes the last element of this deque, or returns null if
   * this deque is empty.
   *
   * @return the last element of this deque, or <tt>null</tt> if empty.
   */
  public E pollLast() {
    for (;;) {
      ConcurrentDoublyLinkedNode<E> n = trailer.predecessor();
      if (!usable(n))
        return null;
      if (n.delete())
        return n.getElement();
    }
  }

  /**
   * Removes and returns the first element from this deque.
   *
   * @return the first element from this deque.
   * @throws java.util.NoSuchElementException
   *             if this deque is empty.
   */
  public E removeFirst() {
    return screenNullResult(pollFirst());
  }

  /**
   * Removes and returns the last element from this deque.
   *
   * @return the last element from this deque.
   * @throws java.util.NoSuchElementException
   *             if this deque is empty.
   */
  public E removeLast() {
    return screenNullResult(pollLast());
  }

  // *** Queue and stack methods ***
  public boolean offer(E e) {
    return offerLast(e);
  }

  public boolean add(E e) {
    return offerLast(e);
  }

  public E poll() {
    return pollFirst();
  }

  public E remove() {
    return removeFirst();
  }

  public E peek() {
    return peekFirst();
  }

  public E element() {
    return getFirst();
  }

  public void push(E e) {
    addFirst(e);
  }

  public E pop() {
    return removeFirst();
  }

  /**
   * Removes the first element <tt>e</tt> such that <tt>o.equals(e)</tt>,
   * if such an element exists in this deque. If the deque does not contain
   * the element, it is unchanged.
   *
   * @param o
   *            element to be removed from this deque, if present.
   * @return <tt>true</tt> if the deque contained the specified element.
   * @throws NullPointerException
   *             if the specified element is <tt>null</tt>
   */
  public boolean removeFirstOccurrence(Object o) {
    checkNullArg(o);
    for (;;) {
      ConcurrentDoublyLinkedNode<E> n = header.forward();
      for (;;) {
        if (n == null)
          return false;
        if (o.equals(n.element)) {
          if (n.delete())
            return true;
          else
            break; // restart if interference
        }
        n = n.forward();
      }
    }
  }

  /**
   * Removes the last element <tt>e</tt> such that <tt>o.equals(e)</tt>,
   * if such an element exists in this deque. If the deque does not contain
   * the element, it is unchanged.
   *
   * @param o
   *            element to be removed from this deque, if present.
   * @return <tt>true</tt> if the deque contained the specified element.
   * @throws NullPointerException
   *             if the specified element is <tt>null</tt>
   */
  public boolean removeLastOccurrence(Object o) {
    checkNullArg(o);
    for (;;) {
      ConcurrentDoublyLinkedNode<E> s = trailer;
      for (;;) {
        ConcurrentDoublyLinkedNode<E> n = s.back();
        if (s.isDeleted() || (n != null && n.successor() != s))
          break; // restart if pred link is suspect.
        if (n == null)
          return false;
        if (o.equals(n.element)) {
          if (n.delete())
            return true;
          else
            break; // restart if interference
        }
        s = n;
      }
    }
  }

  /**
   * Returns <tt>true</tt> if this deque contains at least one element
   * <tt>e</tt> such that <tt>o.equals(e)</tt>.
   *
   * @param o
   *            element whose presence in this deque is to be tested.
   * @return <tt>true</tt> if this deque contains the specified element.
   */
  public boolean contains(Object o) {
    if (o == null)
      return false;
    for (ConcurrentDoublyLinkedNode<E> n = header.forward(); n != null; n = n.forward())
      if (o.equals(n.element))
        return true;
    return false;
  }

  /**
   * Returns <tt>true</tt> if this collection contains no elements.
   * <p>
   *
   * @return <tt>true</tt> if this collection contains no elements.
   */
  public boolean isEmpty() {
    return !usable(header.successor());
  }

  /**
   * Returns the number of elements in this deque. If this deque contains more
   * than <tt>Integer.MAX_VALUE</tt> elements, it returns
   * <tt>Integer.MAX_VALUE</tt>.
   *
   * <p>
   * Beware that, unlike in most collections, this method is <em>NOT</em> a
   * constant-time operation. Because of the asynchronous nature of these
   * deques, determining the current number of elements requires traversing
   * them all to count them. Additionally, it is possible for the size to
   * change during execution of this method, in which case the returned result
   * will be inaccurate. Thus, this method is typically not very useful in
   * concurrent applications.
   *
   * @return the number of elements in this deque.
   */
  public int size() {
    long count = 0;
    for (ConcurrentDoublyLinkedNode<E> n = header.forward(); n != null; n = n.forward())
      ++count;
    return (count >= Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) count;
  }

  /**
   * Removes the first element <tt>e</tt> such that <tt>o.equals(e)</tt>,
   * if such an element exists in this deque. If the deque does not contain
   * the element, it is unchanged.
   *
   * @param o
   *            element to be removed from this deque, if present.
   * @return <tt>true</tt> if the deque contained the specified element.
   * @throws NullPointerException
   *             if the specified element is <tt>null</tt>
   */
  public boolean remove(Object o) {
    return removeFirstOccurrence(o);
  }

  /**
   * Appends all of the elements in the specified collection to the end of
   * this deque, in the order that they are returned by the specified
   * collection's iterator. The behavior of this operation is undefined if the
   * specified collection is modified while the operation is in progress.
   * (This implies that the behavior of this call is undefined if the
   * specified Collection is this deque, and this deque is nonempty.)
   *
   * @param c
   *            the elements to be inserted into this deque.
   * @return <tt>true</tt> if this deque changed as a result of the call.
   * @throws NullPointerException
   *             if <tt>c</tt> or any element within it is <tt>null</tt>
   */
  public boolean addAll(Collection<? extends E> c) {
    Iterator<? extends E> it = c.iterator();
    if (!it.hasNext())
      return false;
    do {
      addLast(it.next());
    } while (it.hasNext());
    return true;
  }

  /**
   * Removes all of the elements from this deque.
   */
  public void clear() {
    while (pollFirst() != null)
      ;
  }

  /**
   * Returns an array containing all of the elements in this deque in the
   * correct order.
   *
   * @return an array containing all of the elements in this deque in the
   *         correct order.
   */
  public Object[] toArray() {
    return toArrayList().toArray();
  }

  /**
   * Returns an array containing all of the elements in this deque in the
   * correct order; the runtime type of the returned array is that of the
   * specified array. If the deque fits in the specified array, it is returned
   * therein. Otherwise, a new array is allocated with the runtime type of the
   * specified array and the size of this deque.
   * <p>
   *
   * If the deque fits in the specified array with room to spare (i.e., the
   * array has more elements than the deque), the element in the array
   * immediately following the end of the collection is set to null. This is
   * useful in determining the length of the deque <i>only</i> if the caller
   * knows that the deque does not contain any null elements.
   *
   * @param a
   *            the array into which the elements of the deque are to be
   *            stored, if it is big enough; otherwise, a new array of the
   *            same runtime type is allocated for this purpose.
   * @return an array containing the elements of the deque.
   * @throws ArrayStoreException
   *             if the runtime type of a is not a supertype of the runtime
   *             type of every element in this deque.
   * @throws NullPointerException
   *             if the specified array is null.
   */
  public <T> T[] toArray(T[] a) {
    return toArrayList().toArray(a);
  }

  /**
   * Returns a weakly consistent iterator over the elements in this deque, in
   * first-to-last order. The <tt>next</tt> method returns elements
   * reflecting the state of the deque at some point at or since the creation
   * of the iterator. The method does <em>not</em> throw
   * {@link java.util.ConcurrentModificationException}, and may proceed concurrently
   * with other operations.
   *
   * @return an iterator over the elements in this deque
   */
  public Iterator<E> iterator() {
    return new CLDIterator();
  }

  final class CLDIterator implements Iterator<E> {
    ConcurrentDoublyLinkedNode<E> last;

    ConcurrentDoublyLinkedNode<E> next = header.forward();

    public boolean hasNext() {
      return next != null;
    }

    public E next() {
      ConcurrentDoublyLinkedNode<E> l = last = next;
      if (l == null)
        throw new NoSuchElementException();
      next = next.forward();
      return l.getElement();
    }

    public void remove() {
      ConcurrentDoublyLinkedNode<E> l = last;
      if (l == null)
        throw new IllegalStateException();
      while (!l.delete() && !l.isDeleted())
        ;
    }
  }

}