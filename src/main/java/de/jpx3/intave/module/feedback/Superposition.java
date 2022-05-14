package de.jpx3.intave.module.feedback;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.user.User;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;

public final class Superposition<T> {
  private final User user;
  private final BiConsumer<User, T> apply;
  private final BiConsumer<User, @Nullable T> collapse;
  private final Consumer<User> reset;
  private final BinaryOperator<T> combine;
  private final int timeout;

  private final Map<T, Status> variationStatusMap = new LinkedHashMap<>();
  private final Map<T, Integer> variationTimeout = new LinkedHashMap<>();
  private final Set<T> collapsed = new HashSet<>();

  private T certainValue;
  private T optionalValue;
  private int variationCount;

  Superposition(User user, BiConsumer<User, T> apply, BiConsumer<User, @Nullable T> collapse, Consumer<User> reset, BinaryOperator<T> combine, int timeout) {
    this.user = user;
    this.apply = apply;
    this.collapse = collapse;
    this.reset = reset;
    this.combine = combine;
    this.timeout = timeout;
  }

  private final List<T> removalCache = new ArrayList<>();

  public void beginTick() {
    for (Map.Entry<T, Status> entry : variationStatusMap.entrySet()) {
      T value = entry.getKey();
      Status status = entry.getValue();
      int timeout = variationTimeout.getOrDefault(value, 0);
      if (status == Status.RUNNING && timeout >= this.timeout) {
        System.out.println(value + " timed out after " + timeout + " ticks");
        removalCache.add(value);
      }
    }
    for (T value : removalCache) {
      statusChange(value, Status.COMPLETED);
    }
    removalCache.clear();
  }

  public void computeVariations() {
    if (variationStatusMap.isEmpty()) {
      variationCount = 0;
      return;
    }
    T currentVariation = null;
    T optionalVariation = null;
    for (Map.Entry<T, Status> entry : variationStatusMap.entrySet()) {
      T value = entry.getKey();
      Status status = entry.getValue();
      switch (status) {
        case AWAIT_START:
        case COMPLETED:
          break;
        case RUNNING:
          // it is not possible to have multiple running variations
          variationTimeout.put(value, variationTimeout.getOrDefault(value, 0) + 1);
//          System.out.println("Timeout for " + value + " is now " + variationTimeout.get(value));
//          if (variationTimeout.get(value) <= timeout && !collapsed.contains(value)) {
            optionalVariation = value;
//          }
          break;
        case RECEIVE_CONFIRMED:
          currentVariation = combine == null ? value : combine.apply(currentVariation, value);
          break;
      }
    }
    certainValue = currentVariation;
    optionalValue = optionalVariation;
    // certain and optional -> compute certain and optional, 2 runs
    // certain, but no optional -> compute certain, 1 runs
    // no certain, but optional -> compute nothing and optional, 2 runs
    // no certain, no optional -> compute nothing and no optional, 0 runs
    if (certainValue != null) {
      variationCount = optionalValue != null ? 2 : 1;
    } else {
      variationCount = optionalValue != null ? 2 : 0;
    }
  }

  public void completeTick() {
    for (Map.Entry<T, Status> entry : variationStatusMap.entrySet()) {
      T value = entry.getKey();
      Status status = entry.getValue();
      if (status == Status.RECEIVE_CONFIRMED) {
        removalCache.add(value);
      }
    }
    for (T value : removalCache) {
      statusChange(value, Status.COMPLETED);
    }
    removalCache.clear();
    certainValue = null;
    optionalValue = null;
  }

  public int variationsCount() {
    return variationCount;
  }

  public void applyVariation(int selector) {
    if (variationCount == 0) {
      return;
    }
    T value = valueFrom(selector);
    if (value != null && !collapsed.contains(value) && apply != null) {
      apply.accept(user, value);
    }
  }

  public void resetVariation(int selector) {
    if (variationCount == 0) {
      return;
    }
    T value = valueFrom(selector);
    if (value != null && !collapsed.contains(value) && reset != null) {
      reset.accept(user);
    }
  }

  public void collapseVariation(int selector) {
    if (variationCount == 0) {
      return;
    }
    T value = valueFrom(selector);
    collapseVariation(value);
  }

  public void collapseVariation(T value) {
    if (variationCount == 0) {
      return;
    }
    if (!statusFrom(value).expectPacket()) {
      return;
    }
    if (collapsed.contains(value)) {
      return;
    } else if (value != null) {
      collapsed.add(value);
    }
//    user.player().sendMessage("Collapsed " + value);
//    System.out.println(value + " collapsed after " + variationTimeout.get(value) + " ticks (status: " + statusFrom(value) + ") collapsed " + collapsed);
//    Thread.dumpStack();
    if (apply != null && value != null) {
      apply.accept(user, value);
    }
    if (collapse != null) {
      collapse.accept(user, value);
    }
  }

  private T valueFrom(int selector) {
    if (selector < 0 || selector >= variationCount) {
      return null;
    }
    T value;
    if (variationCount == 1) {
      value = certainValue;
    } else {
      if (certainValue == null) {
        value = selector == 0 ? optionalValue : null;
      } else {
        value = selector == 0 ? certainValue : optionalValue;
      }
    }
    return value;
  }

  public void stateSynchronize(PacketEvent packet, T newState) {
    statusChange(newState, Status.AWAIT_START);
    Modules.feedback().doubleSynchronize(
      user.player(),
      packet,
      newState,
      (player, target) -> statusChange(target, Status.RUNNING),
      (player, target) -> statusChange(target, Status.RECEIVE_CONFIRMED)
    );
  }

  private void statusChange(T target, Status status) {
    Status lastStatus = statusFrom(target);

    // if the status is older than the last status, ignore it
    if (status.ordinal() <= lastStatus.ordinal() && status != Status.AWAIT_START) {
//      System.out.println("Ignoring status change " + status + " for " + target + " because it is older than " + lastStatus);
      return;
    }

    // if the status is too far, ignore it
    if (status.ordinal() > lastStatus.ordinal() + 1 && status != Status.COMPLETED) {
//      System.out.println("Ignoring status change " + status + " for " + target + " because it is too far");
      return;
    }

    switch (status) {
      case AWAIT_START:
        variationStatusMap.put(target, status);
        variationTimeout.put(target, 0);
        break;
      case RUNNING:
      case RECEIVE_CONFIRMED:
        variationStatusMap.put(target, status);
        break;
      case COMPLETED:
        if (!collapsed.contains(target)) {
          collapseVariation(target);
        }
        variationStatusMap.put(target, status);
        variationStatusMap.remove(target);
        variationTimeout.remove(target);
        collapsed.remove(target);
        if (certainValue == target) {
          certainValue = null;
        } else if (optionalValue == target) {
          optionalValue = null;
        }
        break;
    }
  }

  private Status statusFrom(T target) {
    return variationStatusMap.getOrDefault(target, Status.AWAIT_START);
  }

  @KeepEnumInternalNames
  public enum Status {
    AWAIT_START(false),
    RUNNING(true),
    RECEIVE_CONFIRMED(true),
    COMPLETED(false);

    private final boolean expectPacket;

    Status(boolean expectPacket) {
      this.expectPacket = expectPacket;
    }

    public boolean expectPacket() {
      return expectPacket;
    }
  }

  public static <T> Builder<T> builderFor(Class<T> clazz) {
    return new Builder<>();
  }

  public static class Builder<T> {
    private User user;
    private BiConsumer<User, T> apply;
    private BiConsumer<User, @Nullable T> collapse;
    private Consumer<User> reset;
    private BinaryOperator<T> combiner;
    private int timeout;

    public Builder<T> user(User user) {
      this.user = user;
      return this;
    }

    public Builder<T> apply(BiConsumer<User, T> apply) {
      this.apply = apply;
      return this;
    }

    public Builder<T> collapse(BiConsumer<User, @Nullable T> collapse) {
      this.collapse = collapse;
      return this;
    }

    public Builder<T> reset(Consumer<User> reset) {
      this.reset = reset;
      return this;
    }

    public Builder<T> merge(BinaryOperator<T> combiner) {
      this.combiner = combiner;
      return this;
    }

    public Builder<T> keepOnMerge() {
      this.combiner = (a, b) -> a;
      return this;
    }

    public Builder<T> overrideMerge() {
      this.combiner = (a, b) -> b;
      return this;
    }

    public Builder<T> timeout(int timeout) {
      this.timeout = timeout;
      return this;
    }

    public Superposition<T> build() {
      if (user == null) {
        throw new IllegalStateException("User must be set");
      }
      if (apply == null) {
        throw new IllegalStateException("Applier must be set");
      }
      if (reset == null) {
        throw new IllegalStateException("Resetter must be set");
      }
      if (combiner == null) {
        overrideMerge();
      }
      if (timeout == 0) {
        timeout = Integer.MAX_VALUE;
      }
      return new Superposition<>(
        user,
        apply,
        collapse,
        reset,
        combiner,
        timeout
      );
    }
  }
}