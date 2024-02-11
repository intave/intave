package de.jpx3.intave.module.feedback;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

public class CircularArrayQueue<T> implements Queue<T> {
  private final T[] elements;
  private int head;
  private int tail;
  private int size;

  public CircularArrayQueue(int capacity) {
    elements = (T[]) new Object[capacity];
  }

  @Override
  public boolean add(T t) {
    if (size == elements.length) {
//      throw new IllegalStateException("Queue is full");
      size--;
    }
    elements[tail] = t;
    tail = (tail + 1) % elements.length;
    size++;
    return true;
  }

  @Override
  public boolean offer(T t) {
    if (size == elements.length) {
      size--;
    }
    elements[tail] = t;
    tail = (tail + 1) % elements.length;
    size++;
    return true;
  }

  @Override
  public T remove() {
    if (size == 0) {
      throw new IllegalStateException("Queue is empty");
    }
    T t = elements[head];
    elements[head] = null;
    head = (head + 1) % elements.length;
    size--;
    return t;
  }

  @Override
  public T poll() {
    if (size == 0) {
      return null;
    }
    T t = elements[head];
    elements[head] = null;
    head = (head + 1) % elements.length;
    size--;
    return t;
  }

  @Override
  public T element() {
    if (size == 0) {
      throw new IllegalStateException("Queue is empty");
    }
    return elements[head];
  }

  @Override
  public T peek() {
    if (size == 0) {
      return null;
    }
    return elements[head];
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @Override
  public boolean contains(Object o) {
    for (int i = 0; i < size; i++) {
      if (elements[(head + i) % elements.length].equals(o)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean remove(Object o) {
    for (int i = 0; i < size; i++) {
      if (elements[(head + i) % elements.length].equals(o)) {
        remove((head + i) % elements.length);
        return true;
      }
    }
    return false;
  }

  private void remove(int index) {
    for (int i = index; i < size - 1; i++) {
      elements[i] = elements[i + 1];
    }
    elements[(head + size - 1) % elements.length] = null;
    size--;
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    for (Object o : c) {
      if (!contains(o)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends T> c) {
    for (T t : c) {
      add(t);
    }
    return true;
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    boolean modified = false;
    for (Object o : c) {
      modified |= remove(o);
    }
    return modified;
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    boolean modified = false;
    for (int i = 0; i < size; i++) {
      if (!c.contains(elements[(head + i) % elements.length])) {
        remove((head + i) % elements.length);
        modified = true;
      }
    }
    return modified;
  }

  @Override
  public void clear() {
    for (int i = 0; i < size; i++) {
      elements[(head + i) % elements.length] = null;
    }
    head = 0;
    tail = 0;
    size = 0;
  }

  @Override
  public Iterator<T> iterator() {
    return new Iterator<T>() {
      private int index = 0;

      @Override
      public boolean hasNext() {
        return index < size;
      }

      @Override
      public T next() {
        return elements[(head + index++) % elements.length];
      }
    };
  }

  @Override
  public Object[] toArray() {
    Object[] array = new Object[size];
    for (int i = 0; i < size; i++) {
      array[i] = elements[(head + i) % elements.length];
    }
    return array;
  }

  @Override
  public <T1> T1[] toArray(@NotNull T1[] a) {
    if (a.length < size) {
      return (T1[]) toArray();
    }
    for (int i = 0; i < size; i++) {
      a[i] = (T1) elements[(head + i) % elements.length];
    }
    if (a.length > size) {
      a[size] = null;
    }
    return a;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("[");
    for (int i = 0; i < size; i++) {
      builder.append(elements[(head + i) % elements.length]);
      if (i < size - 1) {
        builder.append(", ");
      }
    }
    return builder.append("]").toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CircularArrayQueue<?> that = (CircularArrayQueue<?>) o;
    if (size != that.size) {
      return false;
    }
    for (int i = 0; i < size; i++) {
      if (!elements[(head + i) % elements.length].equals(that.elements[(that.head + i) % that.elements.length])) {
        return false;
      }
    }
    return true;
  }
}
