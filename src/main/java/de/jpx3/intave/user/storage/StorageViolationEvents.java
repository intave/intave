package de.jpx3.intave.user.storage;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StorageViolationEvents implements Storage, Iterable<StorageViolationEvent> {
  private final Collection<StorageViolationEvent> parent;

  public StorageViolationEvents() {
    this(new ArrayList<>());
  }

  public StorageViolationEvents(Collection<StorageViolationEvent> parent) {
    this.parent = new ArrayList<>(parent);
  }

  public int size() {
    return parent.size();
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  public StorageViolationEvent first() {
    Iterator<StorageViolationEvent> iterator = parent.iterator();
    return iterator.hasNext() ? iterator.next() : null;
  }

  public StorageViolationEvent newest() {
    return stream()
      .max(Comparator.comparing(StorageViolationEvent::timestamp))
      .orElse(null);
  }

  public StorageViolationEvents sortedByAge() {
    return new StorageViolationEvents(
      stream()
        .sorted(Comparator.comparing(StorageViolationEvent::timePassedSince))
        .collect(Collectors.toList())
    );
  }

  public StorageViolationEvents withoutViolationsOlderThan(
    long value, TimeUnit unit
  ) {
    return filter(
      event -> event.timePassedSince() < unit.toMillis(value)
    );
  }

  public double matchFactor(
    Predicate<? super StorageViolationEvent> predicate
  ) {
    return (double) numMatching(predicate) / size();
  }

  public long numMatching(
    Predicate<? super StorageViolationEvent> predicate
  ) {
    return stream().filter(predicate).count();
  }

  public StorageViolationEvents filter(
    Predicate<? super StorageViolationEvent> predicate
  ) {
    List<StorageViolationEvent> filtered = stream()
      .filter(predicate)
      .collect(Collectors.toList());
    return new StorageViolationEvents(filtered);
  }

  public Stream<StorageViolationEvent> stream() {
    return parent.stream();
  }

  @NotNull
  @Override
  public Iterator<StorageViolationEvent> iterator() {
    return parent.iterator();
  }

  @Override
  public void forEach(Consumer<? super StorageViolationEvent> action) {
    parent.forEach(action);
  }

  @Override
  public Spliterator<StorageViolationEvent> spliterator() {
    return parent.spliterator();
  }

  @Override
  public void writeTo(ByteArrayDataOutput output) {
    output.writeInt(size());
    for (StorageViolationEvent violation : this) {
      violation.writeTo(output);
    }
  }

  @Override
  public void readFrom(ByteArrayDataInput input) {
    int violations = input.readInt();
    for (int i = 0; i < violations; i++) {
      StorageViolationEvent violation = new StorageViolationEvent();
      violation.readFrom(input);
      add(violation);
    }
  }

  public void add(StorageViolationEvent event) {
    parent.add(event);
  }
}
