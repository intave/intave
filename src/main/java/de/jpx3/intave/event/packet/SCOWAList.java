package de.jpx3.intave.event.packet;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;

import java.util.*;

// successfully copy-pasted from ProtocolLib

// SortedCopyOnWriteArray-List
public final class SCOWAList<T extends Comparable<T>> implements Collection<T>  {
  private volatile List<T> list;

  public SCOWAList() {
    this.list = new ArrayList<>();
  }

  public SCOWAList(Collection<T> wrapped) {
    this.list = new ArrayList<>(wrapped);
  }

  public SCOWAList(Collection<T> wrapped, boolean sort) {
    this.list = new ArrayList<>(wrapped);
    if (sort) {
      Collections.sort(this.list);
    }
  }

  public synchronized boolean add(T value) {
    if (value == null) {
      throw new IllegalArgumentException("value cannot be NULL");
    } else {
      List<T> copy = new ArrayList<>();
      T element;
      for(Iterator<T> var3 = this.list.iterator(); var3.hasNext(); copy.add(element)) {
        element = var3.next();
        if (value != null && value.compareTo(element) < 0) {
          copy.add(value);
          value = null;
        }
      }

      if (value != null) {
        copy.add(value);
      }

      this.list = copy;
      return true;
    }
  }

  public synchronized boolean addAll(Collection<? extends T> values) {
    if (values == null) {
      throw new IllegalArgumentException("values cannot be NULL");
    } else if (values.size() == 0) {
      return false;
    } else {
      List<T> copy = new ArrayList<>();
      copy.addAll(this.list);
      copy.addAll(values);
      Collections.sort(copy);
      this.list = copy;
      return true;
    }
  }

  public synchronized boolean remove(Object value) {
    List<T> copy = new ArrayList<>();
    boolean result = false;

    for (T element : this.list) {
      if (!Objects.equal(value, element)) {
        copy.add(element);
      } else {
        result = true;
      }
    }

    this.list = copy;
    return result;
  }

  public boolean removeAll(Collection<?> values) {
    if (values == null) {
      throw new IllegalArgumentException("values cannot be NULL");
    } else if (values.size() == 0) {
      return false;
    } else {
      List<T> copy = new ArrayList<>(this.list);
      copy.removeAll(values);
      this.list = copy;
      return true;
    }
  }

  public boolean retainAll(Collection<?> values) {
    if (values == null) {
      throw new IllegalArgumentException("values cannot be NULL");
    } else if (values.size() == 0) {
      return false;
    } else {
      List<T> copy = new ArrayList<>(this.list);
      copy.removeAll(values);
      this.list = copy;
      return true;
    }
  }

  public synchronized void remove(int index) {
    List<T> copy = new ArrayList<>(this.list);
    copy.remove(index);
    this.list = copy;
  }

  public T get(int index) {
    return this.list.get(index);
  }

  public int size() {
    return this.list.size();
  }

  public Iterator<T> iterator() {
    return Iterables.unmodifiableIterable(this.list).iterator();
  }

  public void clear() {
    this.list = new ArrayList<>();
  }

  public boolean contains(Object value) {
    return this.list.contains(value);
  }

  public boolean containsAll(Collection<?> values) {
    return this.list.containsAll(values);
  }

  public boolean isEmpty() {
    return this.list.isEmpty();
  }

  public Object[] toArray() {
    return this.list.toArray();
  }

  public <X> X[] toArray(X[] a) {
    return this.list.toArray(a);
  }

  public String toString() {
    return this.list.toString();
  }
}
