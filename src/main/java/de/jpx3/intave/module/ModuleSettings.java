package de.jpx3.intave.module;

import de.jpx3.intave.IntaveControl;

final class ModuleSettings {
  private final BootSegment bootSegment;
  private final Requirement requirement;
  private final boolean linkSubscriptions;

  private ModuleSettings(
    BootSegment bootSegment,
    Requirement requirement,
    boolean linkSubscriptions
  ) {
    this.bootSegment = bootSegment;
    this.requirement = requirement;
    this.linkSubscriptions = linkSubscriptions;
  }

  public BootSegment bootSegment() {
    return bootSegment;
  }

  public boolean requirementsFulfilled() {
    return requirement.fulfilled();
  }

  public boolean shouldLinkSubscriptions() {
    return linkSubscriptions;
  }

  public boolean readyToLoad(BootSegment segment) {
    return segment.equals(bootSegment()) && requirementsFulfilled();
  }

  public boolean readyForEnable(BootSegment segment) {
    return segment.equals(bootSegment());
  }

  public static ModuleSettings def() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private BootSegment bootSegment;
    private Requirement requirement;
    private boolean linkSubscriptions = true;

    public Builder bootBeforeIntave() {
      return bootAt(BootSegment.STAGE_3);
    }

    public Builder bootUsually() {
      return bootAt(BootSegment.STAGE_7);
    }

    public Builder bootAfterIntave() {
      return bootAt(BootSegment.STAGE_10);
    }

    public Builder requirePacketEvents() {
      return withRequirement(Requirements.packetEvents());
    }

    public Builder withRequirement(Requirement requirement) {
      this.requirement = requirement;
      return this;
    }

    public Builder andRequire(Requirement requirement) {
      if (this.requirement == null) {
        this.requirement = Requirements.none();
      }
      this.requirement = this.requirement.and(requirement);
      return this;
    }

    public Builder orRequire(Requirement requirement) {
      if (this.requirement == null) {
        throw new IllegalStateException("Can not have or operation on empty requirement");
      }
      this.requirement = this.requirement.or(requirement);
      return this;
    }

    public Builder bootAt(BootSegment bootSegment) {
      this.bootSegment = bootSegment;
      return this;
    }

    public Builder doNotLinkSubscriptions() {
      this.linkSubscriptions = false;
      return this;
    }

    public ModuleSettings build() {
      if (bootSegment == null) {
        bootUsually();
      }
      if (requirement == null) {
        requirement = Requirements.none();
      }
      return new ModuleSettings(bootSegment, requirement, linkSubscriptions);
    }
  }
}
