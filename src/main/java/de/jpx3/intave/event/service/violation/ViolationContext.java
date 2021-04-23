package de.jpx3.intave.event.service.violation;

import com.google.common.collect.ImmutableList;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.placeholder.ViolationPlaceholderContext;
import de.jpx3.intave.user.UserMetaClientData;

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
    this.counterThreat();
    this.threatAssessment = reason;
    return this;
  }

  public ViolationContext counterThreat() {
    if (processCompleted) {
      throw new UnsupportedOperationException();
    }
    this.counterThreat = true;
    return this;
  }

  public ViolationContext ignoreThreatBecause(String reason) {
    this.ignoreThreat();
    this.threatAssessment = reason;
    return this;
  }

  public ViolationContext ignoreThreat() {
    if (processCompleted) {
      throw new UnsupportedOperationException();
    }
    this.counterThreat = false;
    return this;
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

  @Native
  public ViolationPlaceholderContext placeholderContextOf(
    ViolationPlaceholderContext.DetailScope type
  ) {
    boolean enterprise = (UserMetaClientData.VERSION_DETAILS & 0x200) != 0;
    boolean partner = (UserMetaClientData.VERSION_DETAILS & 0x100) != 0;
    boolean fullMessage = enterprise && type == ViolationPlaceholderContext.DetailScope.FULL;
    return new ViolationPlaceholderContext(
      initialViolation.check().name(),
      initialViolation.message(),
      fullMessage ? initialViolation.details() : "",
      violationLevelBefore,
      violationLevelAfter
    );
  }

  public static ViolationContext newOf(Violation initialViolation) {
    return new ViolationContext(initialViolation);
  }
}
