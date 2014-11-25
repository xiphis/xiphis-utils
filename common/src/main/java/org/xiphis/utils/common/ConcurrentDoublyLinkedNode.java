/*
 Copyright (c) 2014, Xiphis
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 Neither the name of the Xiphis nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package org.xiphis.utils.common;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Linked Nodes. As a minor efficiency hack, this class opportunistically
 * inherits from AtomicReference, with the atomic ref used as the "next"
 * link.
 *
 * Nodes are in doubly-linked lists. There are three kinds of special nodes,
 * distinguished by: * The list header has a null prev link * The list
 * trailer has a null next link * A deletion marker has a prev link pointing
 * to itself. All three kinds of special nodes have null element fields.
 *
 * Regular nodes have non-null element, next, and prev fields. To avoid
 * visible inconsistencies when deletions overlap element replacement,
 * replacements are done by replacing the node, not just setting the
 * element.
 *
 * Nodes can be traversed by read-only ConcurrentLinkedDeque class
 * operations just by following raw next pointers, so long as they ignore
 * any special nodes seen along the way. (This is automated in method
 * forward.) However, traversal using prev pointers is not guaranteed to see
 * all live nodes since a prev pointer of a deleted node can become
 * unrecoverably stale.
 */

class ConcurrentDoublyLinkedNode<E> extends AtomicReference<ConcurrentDoublyLinkedNode<E>>
{
  private volatile ConcurrentDoublyLinkedNode<E> prev;

  final Object element;

  /** Creates a node with given contents */
  ConcurrentDoublyLinkedNode(Object element, ConcurrentDoublyLinkedNode<E> next, ConcurrentDoublyLinkedNode<E> prev) {
    super(next);
    this.prev = prev;
    this.element = element;
  }

  /** Creates a marker node with given successor */
  ConcurrentDoublyLinkedNode(ConcurrentDoublyLinkedNode<E> next) {
    super(next);
    this.prev = this;
    this.element = null;
  }

  /**
   * Gets next link (which is actually the value held as atomic
   * reference).
   */
  private ConcurrentDoublyLinkedNode<E> getNext() {
    return get();
  }

  /**
   * Sets next link
   *
   * @param n
   *            the next node
   */
  void setNext(ConcurrentDoublyLinkedNode<E> n) {
    set(n);
  }

  /**
   * compareAndSet next link
   */
  private boolean casNext(ConcurrentDoublyLinkedNode<E> cmp, ConcurrentDoublyLinkedNode<E> val) {
    return compareAndSet(cmp, val);
  }

  /**
   * Gets prev link
   */
  private ConcurrentDoublyLinkedNode<E> getPrev() {
    return prev;
  }

  /**
   * Sets prev link
   *
   * @param b
   *            the previous node
   */
  void setPrev(ConcurrentDoublyLinkedNode<E> b) {
    prev = b;
  }

  /**
   * Returns true if this is a header, trailer, or marker node
   */
  boolean isSpecial() {
    return element == null;
  }

  /**
   * Returns true if this is a trailer node
   */
  boolean isTrailer() {
    return getNext() == null;
  }

  /**
   * Returns true if this is a header node
   */
  boolean isHeader() {
    return getPrev() == null;
  }

  /**
   * Returns true if this is a marker node
   */
  boolean isMarker() {
    return getPrev() == this;
  }

  /**
   * Returns true if this node is followed by a marker, meaning that it is
   * deleted.
   *
   * @return true if this node is deleted
   */
  boolean isDeleted() {
    ConcurrentDoublyLinkedNode<E> f = getNext();
    return f != null && f.isMarker();
  }

  /**
   * Returns next node, ignoring deletion marker
   */
  private ConcurrentDoublyLinkedNode<E> nextNonmarker() {
    ConcurrentDoublyLinkedNode<E> f = getNext();
    return (f == null || !f.isMarker()) ? f : f.getNext();
  }

  /**
   * Returns the next non-deleted node, swinging next pointer around any
   * encountered deleted nodes, and also patching up successor''s prev
   * link to point back to this. Returns null if this node is trailer so
   * has no successor.
   *
   * @return successor, or null if no such
   */
  ConcurrentDoublyLinkedNode<E> successor() {
    ConcurrentDoublyLinkedNode<E> f = nextNonmarker();
    for (;;) {
      if (f == null)
        return null;
      if (!f.isDeleted()) {
        if (f.getPrev() != this && !isDeleted())
          f.setPrev(this); // relink f's prev
        return f;
      }
      ConcurrentDoublyLinkedNode<E> s = f.nextNonmarker();
      if (f == getNext())
        casNext(f, s); // unlink f
      f = s;
    }
  }

  /**
   * Returns the apparent predecessor of target by searching forward for
   * it starting at this node, patching up pointers while traversing. Used
   * by predecessor().
   *
   * @return target's predecessor, or null if not found
   */
  private ConcurrentDoublyLinkedNode<E> findPredecessorOf(ConcurrentDoublyLinkedNode<E> target) {
    ConcurrentDoublyLinkedNode<E> n = this;
    for (;;) {
      ConcurrentDoublyLinkedNode<E> f = n.successor();
      if (f == target)
        return n;
      if (f == null)
        return null;
      n = f;
    }
  }

  /**
   * Returns the previous non-deleted node, patching up pointers as
   * needed. Returns null if this node is header so has no successor. May
   * also return null if this node is deleted, so doesn't have a distinct
   * predecessor.
   *
   * @return predecessor or null if not found
   */
  ConcurrentDoublyLinkedNode<E> predecessor() {
    ConcurrentDoublyLinkedNode<E> n = this;
    for (;;) {
      ConcurrentDoublyLinkedNode<E> b = n.getPrev();
      if (b == null)
        return n.findPredecessorOf(this);
      ConcurrentDoublyLinkedNode<E> s = b.getNext();
      if (s == this)
        return b;
      if (s == null || !s.isMarker()) {
        ConcurrentDoublyLinkedNode<E> p = b.findPredecessorOf(this);
        if (p != null)
          return p;
      }
      n = b;
    }
  }

  /**
   * Returns the next node containing a nondeleted user element. Use for
   * forward list traversal.
   *
   * @return successor, or null if no such
   */
  ConcurrentDoublyLinkedNode<E> forward() {
    ConcurrentDoublyLinkedNode<E> f = successor();
    return (f == null || f.isSpecial()) ? null : f;
  }

  /**
   * Returns previous node containing a nondeleted user element, if
   * possible. Use for backward list traversal, but beware that if this
   * method is called from a deleted node, it might not be able to
   * determine a usable predecessor.
   *
   * @return predecessor, or null if no such could be found
   */
  ConcurrentDoublyLinkedNode<E> back() {
    ConcurrentDoublyLinkedNode<E> f = predecessor();
    return (f == null || f.isSpecial()) ? null : f;
  }

  /**
   * Tries to insert a node holding element as successor, failing if this
   * node is deleted.
   *
   * @param element
   *            the element
   * @return the new node, or null on failure.
   */
  ConcurrentDoublyLinkedNode<E> append(E element) {
    for (;;) {
      ConcurrentDoublyLinkedNode<E> f = getNext();
      if (f == null || f.isMarker())
        return null;
      ConcurrentDoublyLinkedNode<E> x = new ConcurrentDoublyLinkedNode<>(element, f, this);
      if (casNext(f, x)) {
        f.setPrev(x); // optimistically link
        return x;
      }
    }
  }

  /**
   * Tries to insert a node holding element as predecessor, failing if no
   * live predecessor can be found to link to.
   *
   * @param element
   *            the element
   * @return the new node, or null on failure.
   */
  ConcurrentDoublyLinkedNode<E> prepend(E element) {
    for (;;) {
      ConcurrentDoublyLinkedNode<E> b = predecessor();
      if (b == null)
        return null;
      ConcurrentDoublyLinkedNode<E> x = new ConcurrentDoublyLinkedNode<>(element, this, b);
      if (b.casNext(this, x)) {
        setPrev(x); // optimistically link
        return x;
      }
    }
  }

  /**
   * Tries to mark this node as deleted, failing if already deleted or if
   * this node is header or trailer
   *
   * @return true if successful
   */
  public boolean delete() {
    ConcurrentDoublyLinkedNode<E> b = getPrev();
    ConcurrentDoublyLinkedNode<E> f = getNext();
    if (b != null && f != null && !f.isMarker()
        && casNext(f, new ConcurrentDoublyLinkedNode<>(f))) {
      if (b.casNext(this, f))
        f.setPrev(b);
      return true;
    }
    return false;
  }

  /**
   * Tries to insert a node holding element to replace this node. failing
   * if already deleted.
   *
   * @param newElement
   *            the new element
   * @return the new node, or null on failure.
   */
  ConcurrentDoublyLinkedNode<E> replace(E newElement) {
    for (;;) {
      ConcurrentDoublyLinkedNode<E> b = getPrev();
      ConcurrentDoublyLinkedNode<E> f = getNext();
      if (b == null || f == null || f.isMarker())
        return null;
      ConcurrentDoublyLinkedNode<E> x = new ConcurrentDoublyLinkedNode<E>(newElement, f, b);
      if (casNext(f, new ConcurrentDoublyLinkedNode<>(x))) {
        b.successor(); // to relink b
        x.successor(); // to relink f
        return x;
      }
    }
  }

  @SuppressWarnings("unchecked")
  public final E getElement()
  {
    return (E) element;
  }
}
