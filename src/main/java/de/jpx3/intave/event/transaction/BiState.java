package de.jpx3.intave.event.transaction;

import java.util.function.Consumer;

public final class BiState<T> {
  T mainState;
  T optionalState;

  public BiState(T mainState) {
    this.mainState = mainState;
    this.optionalState = null;
  }

  public BiState(T mainState, T optionalState) {
    this.mainState = mainState;
    this.optionalState = optionalState;
  }

  public void flushState(T state) {
    mainState = state;
    optionalState = null;
  }

  public void appendState(T state) {
    optionalState = state;
  }

  public void exactApply(Consumer<T> applier) {
    if (optionalState != null) {
      applier.accept(optionalState);
    } else if (mainState != null) {
      applier.accept(mainState);
    }
  }

  public static <T> BiState<T> of(T defaultState) {
    return new BiState<>(defaultState);
  }
}
