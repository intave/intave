package de.jpx3.intave.module.violation;

import com.google.common.collect.ImmutableList;
import de.jpx3.intave.module.violation.placeholder.ViolationPlaceholderContext;

import java.util.ArrayList;
import java.util.List;

public final class ViolationContext {
  private final Violation initialViolation;
  private boolean counterThreat;
  private String threatAssessment;

  private double violationLevelBefore;
  private double violationLevelAfter;
  private double preventionActivation;

  private boolean meetsThresholds;
  private List<String> executedCommands = new ArrayList<>();

  private boolean processCompleted;

  private ViolationContext(Violation initialViolation) {
    this.initialViolation = initialViolation;
  }

  public Violation violation() {
    return initialViolation;
  }

  public boolean shouldCounterThreat() {
    return counterThreat;
  }

  public ViolationContext counterThreatBecause(String reason) {
    if (processCompleted) {
      throw new UnsupportedOperationException();
    }
    this.threatAssessment = reason;
    return this.counterThreat();
  }

  public ViolationContext counterThreat() {
    if (processCompleted) {
      throw new UnsupportedOperationException();
    }
    this.counterThreat = true;
    return this;
  }

  public ViolationContext ignoreThreatBecause(String reason) {
    if (processCompleted) {
      throw new UnsupportedOperationException();
    }
    this.threatAssessment = reason;
    return this.ignoreThreat();
  }

  public ViolationContext ignoreThreat() {
    if (processCompleted) {
      throw new UnsupportedOperationException();
    }
    this.counterThreat = false;
    return this;
  }

  public String threatAssessment() {
    return threatAssessment;
  }

  public double violationLevelBefore() {
    return violationLevelBefore;
  }

  public void setViolationLevelBefore(double violationLevelBefore) {
    if (processCompleted) {
      throw new UnsupportedOperationException();
    }
    this.violationLevelBefore = violationLevelBefore;
  }

  public double violationLevelAfter() {
    return violationLevelAfter;
  }

  public void setViolationLevelAfter(double violationLevelAfter) {
    if (processCompleted) {
      throw new UnsupportedOperationException();
    }
    this.violationLevelAfter = violationLevelAfter;
  }

  public double preventionActivation() {
    return preventionActivation;
  }

  public void setPreventionActivation(double preventionActivation) {
    if (processCompleted) {
      throw new UnsupportedOperationException();
    }
    this.preventionActivation = preventionActivation;
  }

  public boolean violationLevelPassedPreventionActivation() {
    return violationLevelAfter > preventionActivation;
  }

  public boolean meetsThresholds() {
    return meetsThresholds;
  }

  public void setMeetsThresholds(boolean meetsThresholds) {
    if (processCompleted) {
      throw new UnsupportedOperationException();
    }
    this.meetsThresholds = meetsThresholds;
  }

  public List<String> commands() {
    return executedCommands;
  }

  public void addCommand(String command) {
    if (processCompleted) {
      throw new UnsupportedOperationException();
    }
    this.executedCommands.add(command);
  }

  public void setCommands(List<String> commands) {
    if (processCompleted) {
      throw new UnsupportedOperationException();
    }
    this.executedCommands = commands;
  }

  public ViolationContext complete() {
    this.processCompleted = true;
    this.executedCommands = ImmutableList.copyOf(executedCommands);
    return this;
  }

  public boolean completed() {
    return this.processCompleted;
  }

  public ViolationPlaceholderContext placeholderContextOf(
    ViolationPlaceholderContext.DetailScope type
  ) {
    boolean fullMessage = /*enterprise && */type == ViolationPlaceholderContext.DetailScope.FULL;
    return new ViolationPlaceholderContext(
      initialViolation.check().name(),
      initialViolation.message(),
      fullMessage ? initialViolation.details() : "",
      violationLevelBefore,
      violationLevelAfter
    );
  }

  public static ViolationContext of(Violation initialViolation) {
    return new ViolationContext(initialViolation);
  }
}
