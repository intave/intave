package de.jpx3.intave.check.movement.physics.eval;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

final class Evaluators implements Iterable<Evaluator> {
  private final List<Evaluator> evaluators = new ArrayList<>();

  public Evaluators() {
    add(InitialEvaluator.class);
  }

  private void add(Class<? extends Evaluator> evaluatorClass) {
    try {
      add(evaluatorClass.newInstance());
    } catch (InstantiationException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private void add(Evaluator evaluator) {
    evaluators.add(evaluator);
  }

  @NotNull
  @Override
  public Iterator<Evaluator> iterator() {
    return evaluators.iterator();
  }

  @Override
  public void forEach(Consumer<? super Evaluator> action) {
    evaluators.forEach(action);
  }

  @Override
  public Spliterator<Evaluator> spliterator() {
    return evaluators.spliterator();
  }
}
