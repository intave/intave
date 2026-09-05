package de.jpx3.intave.module.tracker.entity;

import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

public final class PeriodicEntityCoverageSelector {
  private final int ticks;
  private final double requiredDistance;
  private final int maxTracedEntities;
  private final int maxDoubleTracedEntities;

  private final BiConsumer<? super User, ? super Entity> entityAdditionListener;
  private final BiConsumer<? super User, ? super Entity> entityRemovalListener;

  private Task task;

  public PeriodicEntityCoverageSelector(
    int ticks, double requiredDistance,
    int maxTracedEntities, int maxDoubleTracedEntities,
    BiConsumer<? super User, ? super Entity> entityAdditionListener,
    BiConsumer<? super User, ? super Entity> entityRemovalListener
  ) {
    this.ticks = ticks;
    this.requiredDistance = requiredDistance;
    this.maxTracedEntities = maxTracedEntities;
    this.maxDoubleTracedEntities = maxDoubleTracedEntities;
    this.entityAdditionListener = entityAdditionListener;
    this.entityRemovalListener = entityRemovalListener;
  }

  public PeriodicEntityCoverageSelector(int ticks, double requiredDistance, int maxTracedEntities, int maxDoubleTracedEntities) {
    this.ticks = ticks;
    this.requiredDistance = requiredDistance;
    this.maxTracedEntities = maxTracedEntities;
    this.maxDoubleTracedEntities = maxDoubleTracedEntities;
    this.entityAdditionListener = (user, entity) -> {};
    this.entityRemovalListener = (user, entity) -> {};
  }

  public void enableTask() {
    task = Tasks.periodicNamed("PeriodicEntityCoverageSelector.select", () -> {
      UserRepository.applyOnOnlineUsers(user ->
        Synchronizer.synchronize(user, () -> selectEntitiesToTraceFor(user.player()))
      );
    }, ticks, ticks).startAsync();
  }

  public void disableTask() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  private void selectEntitiesToTraceFor(Player player) {
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    ConnectionMetadata connection = user.meta().connection();
    Vector playerLocation = player.getLocation().toVector();
    List<Entity> validEntities = new ArrayList<>();
    for (Entity entity : connection.entities()) {
      boolean firstSurvive = false;
      if (entity.typeData() != null && entity.typeData().isLivingEntity()) {
        double distance = entity.distanceTo(playerLocation);
        if (distance <= requiredDistance) {
          validEntities.add(entity);
          entity.distanceToPlayerCache = distance;
          entity.doubleVerification = false;
          firstSurvive = true;
        }
      }
      if (entity.tracingEnabled() && !firstSurvive) {
        entity.setResponseTracingEnabled(false);
      }
    }
    validEntities.sort(Comparator.comparingDouble(entity -> entity.distanceToPlayerCache * (entity.isPlayer ? 0.1 : 1)));
    int count = 0;
    List<Entity> traced = connection.tracedEntities();
    List<Entity> lastTraced = new ArrayList<>(traced);
    traced.clear();
    for (Entity entity : validEntities) {
      boolean trace = count < maxTracedEntities;
      if (trace) {
        traced.add(entity);
      }
      entity.setResponseTracingEnabled(trace);
      entity.doubleVerification = trace && count < maxDoubleTracedEntities;
      count++;
    }

    for (Entity entity : lastTraced) {
      if (!traced.contains(entity)) {
        entityRemovalListener.accept(user, entity);
        if (user.meta().connection().debugEntityTracing) {
          player.sendMessage(ChatColor.LIGHT_PURPLE + "Removed " + entity.entityName()+"/"+entity.entityId() + " " + entity.boundingBox() + " from " + user.player().getName());
        }
      }
    }

    for (Entity entity : traced) {
      if (!lastTraced.contains(entity)) {
        entityAdditionListener.accept(user, entity);
        if (user.meta().connection().debugEntityTracing) {
          player.sendMessage(ChatColor.LIGHT_PURPLE + "Added " + entity.entityName()+"/"+entity.entityId() + " " + entity.boundingBox() + " to " + user.player().getName());
        }
      }
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private int ticks;
    private double requiredDistance;
    private int maxTracedEntities;
    private int maxDoubleTracedEntities;
    private BiConsumer<? super User, ? super Entity> entityAdditionListener;
    private BiConsumer<? super User, ? super Entity> entityRemovalListener;

    public Builder withRefreshIntervalInTicks(int ticks) {
      this.ticks = ticks;
      return this;
    }

    public Builder withRefreshIntervalInSeconds(int seconds) {
      this.ticks = seconds * 20;
      return this;
    }

    public Builder withDistanceRequirement(double requiredDistance) {
      this.requiredDistance = requiredDistance;
      return this;
    }

    public Builder withMaxTracedEntities(int maxTracedEntities) {
      this.maxTracedEntities = maxTracedEntities;
      return this;
    }

    public Builder withMaxDoubleTracedEntities(int maxDoubleTracedEntities) {
      this.maxDoubleTracedEntities = maxDoubleTracedEntities;
      return this;
    }

    public Builder withEntityAdditionListener(BiConsumer<? super User, ? super Entity> entityAdditionListener) {
      this.entityAdditionListener = entityAdditionListener;
      return this;
    }

    public Builder withEntityRemovalListener(BiConsumer<? super User, ? super Entity> entityRemovalListener) {
      this.entityRemovalListener = entityRemovalListener;
      return this;
    }

    public PeriodicEntityCoverageSelector build() {
      if (ticks == 0) {
        throw new IllegalStateException("Refresh interval must be set");
      }
      if (requiredDistance == 0) {
        throw new IllegalStateException("Distance requirement must be set");
      }
      if (maxTracedEntities == 0) {
        throw new IllegalStateException("Max traced entities must be set");
      }
      if (maxDoubleTracedEntities == 0) {
        throw new IllegalStateException("Max double traced entities must be set");
      }
      if (entityAdditionListener == null) {
        entityAdditionListener = (user, entity) -> {};
      }
      if (entityRemovalListener == null) {
        entityRemovalListener = (user, entity) -> {};
      }
      return new PeriodicEntityCoverageSelector(
        ticks, requiredDistance, maxTracedEntities, maxDoubleTracedEntities,
        entityAdditionListener, entityRemovalListener
      );
    }
  }
}
