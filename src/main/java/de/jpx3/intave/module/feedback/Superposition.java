package de.jpx3.intave.module.feedback;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.user.User;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;

public final class Superposition<T> {
  private final User user;
  private final BiConsumer<User, T> applier;
  private final BiConsumer<User, @Nullable T> collapseApplier;
  private final Consumer<User> resetter;
  private final BinaryOperator<T> combiner;
  private final int timeout;

  private final Map<T, Status> variationStatus = new LinkedHashMap<>();
  private final Map<T, Integer> variationTimeout = new LinkedHashMap<>();
  private final List<T> collapsed = new ArrayList<>();

  private T certainValue;
  private T optionalValue;
  private int variationCount;

  Superposition(User user, BiConsumer<User, T> applier, BiConsumer<User, @Nullable T> collapseApplier, Consumer<User> resetter, BinaryOperator<T> combiner, int timeout) {
    this.user = user;
    this.applier = applier;
    this.collapseApplier = collapseApplier;
    this.resetter = resetter;
    this.combiner = combiner;
    this.timeout = timeout;
  }

  private final List<T> removalCache = new ArrayList<>();

  public void completeTick() {
    for (Map.Entry<T, Status> entry : variationStatus.entrySet()) {
      T value = entry.getKey();
      Status status = entry.getValue();
      if (status == Status.RECEIVE_CONFIRMED || variationTimeout.get(value) > timeout) {
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

  public void computeVariations() {
    if (variationStatus.isEmpty()) {
      variationCount = 0;
      return;
    }
    T currentVariation = null;
    T optionalVariation = null;
    for (Map.Entry<T, Status> entry : variationStatus.entrySet()) {
      T value = entry.getKey();
      Status status = entry.getValue();
      switch (status) {
        case AWAIT_START:
        case COMPLETED:
          break;
        case RUNNING:
          // it is not possible to have multiple running variations
          variationTimeout.put(value, variationTimeout.get(value) + 1);
          if (variationTimeout.get(value) <= timeout) {
            optionalVariation = value;
          }
          break;
        case RECEIVE_CONFIRMED:
          currentVariation = combiner == null ? value : combiner.apply(currentVariation, value);
          break;
      }
    }
    certainValue = currentVariation;
    optionalValue = optionalVariation;
    // certain and optional -> compute certain and optional, 2 runs
    // certain, but no optional -> compute certain, 1 runs (done)
    // no certain, but optional -> compute nothing and optional, 2 runs
    // no certain, no optional -> compute nothing and no optional, 0 runs (done)
    if (certainValue != null) {
      variationCount = optionalValue != null ? 2 : 1;
    } else {
      variationCount = optionalValue != null ? 2 : 0;
    }
  }

  public int variationsCount() {
    return variationCount;
  }

  public void applyVariation(int selector) {
    if (variationCount == 0) {
      return;
    }
    T value = valueFrom(selector);
    if (value != null && !collapsed.contains(value) && applier != null) {
      applier.accept(user, value);
    }
  }

  public void resetVariation(int selector) {
    if (variationCount == 0) {
      return;
    }
    T value = valueFrom(selector);
    if (value != null && !collapsed.contains(value) && resetter != null) {
      resetter.accept(user);
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
    if (collapsed.contains(value)) {
      return;
    } else if (value != null) {
      collapsed.add(value);
    }
    if (applier != null && value != null) {
      applier.accept(user, value);
    }
    if (collapseApplier != null) {
      collapseApplier.accept(user, value);
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
    switch (status) {
      case AWAIT_START:
        variationStatus.put(target, status);
        variationTimeout.put(target, 0);
        break;
      case RUNNING:
      case RECEIVE_CONFIRMED:
        variationStatus.put(target, status);
        break;
      case COMPLETED:
        if (!collapsed.contains(target)) {
          collapseVariation(target);
        }
        variationStatus.remove(target);
        variationTimeout.remove(target);
        collapsed.remove(target);
        break;
    }
  }

  public enum Status {
    AWAIT_START(false),
    RUNNING(true),
    RECEIVE_CONFIRMED(true),
    COMPLETED(false);

    private final boolean accountPacket;

    Status(boolean accountPacket) {
      this.accountPacket = accountPacket;
    }

    public boolean accountPacket() {
      return accountPacket;
    }
  }

  public static <T> Builder<T> builderFor(Class<T> clazz) {
    return new Builder<>();
  }

  public static class Builder<T> {
    private User user;
    private BiConsumer<User, T> applier;
    private BiConsumer<User, @Nullable T> collapseApplier;
    private Consumer<User> resetter;
    private BinaryOperator<T> combiner;
    private int timeout;

    public Builder<T> user(User user) {
      this.user = user;
      return this;
    }

    public Builder<T> apply(BiConsumer<User, T> applier) {
      this.applier = applier;
      return this;
    }

    public Builder<T> collapse(BiConsumer<User, @Nullable T> collapseApplier) {
      this.collapseApplier = collapseApplier;
      return this;
    }

    public Builder<T> reset(Consumer<User> resetter) {
      this.resetter = resetter;
      return this;
    }

    public Builder<T> combiner(BinaryOperator<T> combiner) {
      this.combiner = combiner;
      return this;
    }

    public Builder<T> mergeSelectCurrent() {
      this.combiner = (a, b) -> a;
      return this;
    }

    public Builder<T> mergeSelectOverride() {
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
      if (applier == null) {
        throw new IllegalStateException("Applier must be set");
      }
      if (resetter == null) {
        throw new IllegalStateException("Resetter must be set");
      }
      if (combiner == null) {
        mergeSelectOverride();
      }
      if (timeout == 0) {
        timeout = Integer.MAX_VALUE;
      }
      return new Superposition<>(
        user,
        applier,
        collapseApplier,
        resetter,
        combiner,
        timeout
      );
    }
  }
}
