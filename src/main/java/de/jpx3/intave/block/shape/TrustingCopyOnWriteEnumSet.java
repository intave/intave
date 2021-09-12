package de.jpx3.intave.block.shape;

import org.jetbrains.annotations.NotNull;

import java.util.*;

// stolen & adopted from JumboEnumSet in the JRE
public final class TrustingCopyOnWriteEnumSet<E extends Enum<E>> extends AbstractSet<E> {
  /**
   * Bit vector representation of this set.  The ith bit of the jth
   * element of this array represents the  presence of universe[64*j +i]
   * in this set.
   */
  private long[] elements;
  private int size = 0;

  private final Class<E> elementType;
  private final Enum<?>[] universe;

  TrustingCopyOnWriteEnumSet(Class<E> elementType, Enum<?>[] universe) {
    this.elementType = elementType;
    this.universe = universe;
    elements = new long[(universe.length + 63) >>> 6];
  }

  public void addAll() {
    Arrays.fill(elements, -1);
    long[] elements = Arrays.copyOf(this.elements, this.elements.length);
    elements[elements.length - 1] >>>= -universe.length;
    this.elements = elements;
    size = universe.length;
  }

  public void complement() {
    long[] elements = Arrays.copyOf(this.elements, this.elements.length);
    for (int i = 0; i < elements.length; i++)
      elements[i] = ~elements[i];
    elements[elements.length - 1] &= (-1L >>> -universe.length);
    this.elements = elements;
    size = universe.length - size;
  }

  /**
   * Returns an iterator over the elements contained in this set.  The
   * iterator traverses the elements in their <i>natural order</i> (which is
   * the order in which the enum constants are declared). The returned
   * Iterator is a "weakly consistent" iterator that will never throw {@link
   * ConcurrentModificationException}.
   *
   * @return an iterator over the elements contained in this set
   */
  public @NotNull Iterator<E> iterator() {
    return new EnumSetIterator<>();
  }

  private class EnumSetIterator<T extends Enum<T>> implements Iterator<T> {
    /**
     * A bit vector representing the elements in the current "word"
     * of the set not yet returned by this iterator.
     */
    long unseen;

    /**
     * The index corresponding to unseen in the elements array.
     */
    int unseenIndex = 0;

    /**
     * The bit representing the last element returned by this iterator
     * but not removed, or zero if no such element exists.
     */
    long lastReturned = 0;

    /**
     * The index corresponding to lastReturned in the elements array.
     */
    int lastReturnedIndex = 0;

    EnumSetIterator() {
      unseen = elements[0];
    }

    @Override
    public boolean hasNext() {
      while (unseen == 0 && unseenIndex < elements.length - 1)
        unseen = elements[++unseenIndex];
      return unseen != 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T next() {
      if (!hasNext())
        throw new NoSuchElementException();
      lastReturned = unseen & -unseen;
      lastReturnedIndex = unseenIndex;
      unseen -= lastReturned;
      return (T) universe[(lastReturnedIndex << 6)
        + Long.numberOfTrailingZeros(lastReturned)];
    }

    @Override
    public void remove() {
      if (lastReturned == 0)
        throw new IllegalStateException();
      long[] elements = Arrays.copyOf(TrustingCopyOnWriteEnumSet.this.elements, TrustingCopyOnWriteEnumSet.this.elements.length);
      final long oldElements = elements[lastReturnedIndex];
      elements[lastReturnedIndex] &= ~lastReturned;
      if (oldElements != elements[lastReturnedIndex]) {
        size--;
      }
      TrustingCopyOnWriteEnumSet.this.elements = elements;
      lastReturned = 0;
    }
  }

  /**
   * Returns the number of elements in this set.
   *
   * @return the number of elements in this set
   */
  public int size() {
    return size;
  }

  /**
   * Returns <tt>true</tt> if this set contains no elements.
   *
   * @return <tt>true</tt> if this set contains no elements
   */
  public boolean isEmpty() {
    return size == 0;
  }

  /**
   * Returns <tt>true</tt> if this set contains the specified element.
   *
   * @param e element to be checked for containment in this collection
   * @return <tt>true</tt> if this set contains the specified element
   */
  public boolean contains(Object e) {
    int eOrdinal = ((Enum<?>)e).ordinal();
    return (elements[eOrdinal >>> 6] & (1L << eOrdinal)) != 0;
  }

  // Modification Operations

  /**
   * Adds the specified element to this set if it is not already present.
   *
   * @param e element to be added to this set
   * @return <tt>true</tt> if the set changed as a result of the call
   *
   * @throws NullPointerException if <tt>e</tt> is null
   */
  public boolean add(E e) {
    int eOrdinal = e.ordinal();
    int eWordNum = eOrdinal >>> 6;
    long[] elements = Arrays.copyOf(this.elements, this.elements.length);
    long oldElements = elements[eWordNum];
    elements[eWordNum] |= (1L << eOrdinal);
    boolean result = (elements[eWordNum] != oldElements);
    this.elements = elements;
    if (result)
      size++;
    return result;
  }

  /**
   * Removes the specified element from this set if it is present.
   *
   * @param e element to be removed from this set, if present
   * @return <tt>true</tt> if the set contained the specified element
   */
  public boolean remove(Object e) {
    long[] elements = Arrays.copyOf(this.elements, this.elements.length);
    int eOrdinal = ((Enum<?>)e).ordinal();
    int eWordNum = eOrdinal >>> 6;

    long oldElements = elements[eWordNum];
    elements[eWordNum] &= ~(1L << eOrdinal);
    boolean result = (elements[eWordNum] != oldElements);
    if (result)
      size--;
    this.elements = elements;
    return result;
  }

  // Bulk Operations

  /**
   * Returns <tt>true</tt> if this set contains all of the elements
   * in the specified collection.
   *
   * @param c collection to be checked for containment in this set
   * @return <tt>true</tt> if this set contains all of the elements
   *        in the specified collection
   * @throws NullPointerException if the specified collection is null
   */
  public boolean containsAll(@NotNull Collection<?> c) {
    if (!(c instanceof TrustingCopyOnWriteEnumSet))
      return super.containsAll(c);
    TrustingCopyOnWriteEnumSet<?> es = (TrustingCopyOnWriteEnumSet<?>)c;
    if (es.elementType != elementType)
      return es.isEmpty();
    for (int i = 0; i < elements.length; i++)
      if ((es.elements[i] & ~elements[i]) != 0)
        return false;
    return true;
  }

  /**
   * Adds all of the elements in the specified collection to this set.
   *
   * @param c collection whose elements are to be added to this set
   * @return <tt>true</tt> if this set changed as a result of the call
   * @throws NullPointerException if the specified collection or any of
   *     its elements are null
   */
  public boolean addAll(@NotNull Collection<? extends E> c) {
    if (!(c instanceof TrustingCopyOnWriteEnumSet))
      return super.addAll(c);
    TrustingCopyOnWriteEnumSet<?> es = (TrustingCopyOnWriteEnumSet<?>)c;
    long[] elements = Arrays.copyOf(this.elements, this.elements.length);
    for (int i = 0; i < elements.length; i++)
      elements[i] |= es.elements[i];
    this.elements = elements;
    return recalculateSize();
  }

  /**
   * Removes from this set all of its elements that are contained in
   * the specified collection.
   *
   * @param c elements to be removed from this set
   * @return <tt>true</tt> if this set changed as a result of the call
   * @throws NullPointerException if the specified collection is null
   */
  public boolean removeAll(Collection<?> c) {
    if (!(c instanceof TrustingCopyOnWriteEnumSet))
      return super.removeAll(c);
    TrustingCopyOnWriteEnumSet<?> es = (TrustingCopyOnWriteEnumSet<?>)c;
    long[] elements = Arrays.copyOf(this.elements, this.elements.length);
    for (int i = 0; i < elements.length; i++)
      elements[i] &= ~es.elements[i];
    this.elements = elements;
    return recalculateSize();
  }

  /**
   * Retains only the elements in this set that are contained in the
   * specified collection.
   *
   * @param c elements to be retained in this set
   * @return <tt>true</tt> if this set changed as a result of the call
   * @throws NullPointerException if the specified collection is null
   */
  public boolean retainAll(@NotNull Collection<?> c) {
    if (!(c instanceof TrustingCopyOnWriteEnumSet))
      return super.retainAll(c);
    TrustingCopyOnWriteEnumSet<?> es = (TrustingCopyOnWriteEnumSet<?>)c;
    long[] elements = Arrays.copyOf(this.elements, this.elements.length);
    for (int i = 0; i < elements.length; i++)
      elements[i] &= es.elements[i];
    this.elements = elements;
    return recalculateSize();
  }

  /**
   * Removes all of the elements from this set.
   */
  public void clear() {
    Arrays.fill(elements, 0);
    size = 0;
  }

  /**
   * Compares the specified object with this set for equality.  Returns
   * <tt>true</tt> if the given object is also a set, the two sets have
   * the same size, and every member of the given set is contained in
   * this set.
   *
   * @param o object to be compared for equality with this set
   * @return <tt>true</tt> if the specified object is equal to this set
   */
  public boolean equals(Object o) {
    if (!(o instanceof TrustingCopyOnWriteEnumSet))
      return super.equals(o);
    TrustingCopyOnWriteEnumSet<?> es = (TrustingCopyOnWriteEnumSet<?>)o;
    if (es.elementType != elementType)
      return size == 0 && es.size == 0;
    return Arrays.equals(es.elements, elements);
  }

  /**
   * Recalculates the size of the set.  Returns true if it's changed.
   */
  private boolean recalculateSize() {
    int oldSize = size;
    size = 0;
    for (long elt : elements)
      size += Long.bitCount(elt);
    return size != oldSize;
  }

  public TrustingCopyOnWriteEnumSet<E> clone() {
    try {
      //noinspection unchecked
      TrustingCopyOnWriteEnumSet<E> result = (TrustingCopyOnWriteEnumSet<E>) super.clone();
      result.elements = result.elements.clone();
      return result;
    } catch (Exception exception) {
      throw new Error(exception);
    }
  }

  public static <K extends Enum<K>> TrustingCopyOnWriteEnumSet<K> of(Class<K> kClass) {
    K[] universe = kClass.getEnumConstants();
    return new TrustingCopyOnWriteEnumSet<>(kClass, universe);
  }
}
