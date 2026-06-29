package de.jpx3.intave.user.storage;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class LongTermViolationStorage implements Storage {
  private static final long VIOLATION_UPDATE_CHECK_TIMEOUT = TimeUnit.MINUTES.toMillis(3);
  private static final long VIOLATION_INSERT_CHECK_COOLDOWN = TimeUnit.MINUTES.toMillis(10);
  private static final long VIOLATION_ALLOWED_LIFETIME = TimeUnit.DAYS.toMillis(7);
  private static final long VIOLATION_OVERALL_LIMIT = 256;
  public static boolean USE_AUTO_STORAGE = false;

  public static void setup() {
//    USE_AUTO_STORAGE = false;//(boolean) IntavePlugin.singletonInstance().settings().get("storage.auto-logs", true);
  }

  private StorageViolationEvents interestingViolations = new StorageViolationEvents();

  public void noteViolation(String violation, int vl) {
    if (USE_AUTO_STORAGE) {
      return;
    }
    Optional<StorageViolationEvent> lastViolationEvent = lastViolationOfCheck(violation);
    long lastViolationOfCheck = lastViolationEvent.map(StorageViolationEvent::timePassedSince).orElseGet(System::currentTimeMillis);
    if (lastViolationOfCheck > VIOLATION_INSERT_CHECK_COOLDOWN) {
      if (interestingViolations.size() < VIOLATION_OVERALL_LIMIT) {
        StorageViolationEvent event = new StorageViolationEvent(
          violation.toLowerCase(Locale.ROOT),
          IntavePlugin.fullVersion().toLowerCase(Locale.ROOT),
          vl, System.currentTimeMillis()
        );
        addViolationEvent(event);
      }
    } else if (lastViolationOfCheck < VIOLATION_UPDATE_CHECK_TIMEOUT && lastViolationEvent.isPresent()) {
      StorageViolationEvent storageViolationEvent = lastViolationEvent.get();
      if (vl > storageViolationEvent.violationLevel()) {
        storageViolationEvent.setViolationLevel(vl);
        storageViolationEvent.setTimestamp(System.currentTimeMillis());
      }
    }
  }

  public void noteViolation(ViolationContext violationContext) {
    if (!USE_AUTO_STORAGE) {
      return;
    }
    Violation violation = violationContext.violation();
    String checkName = violation.check().name();
    String details = violation.details();
    int violationLevelAfter = (int) violationContext.violationLevelAfter();
    if (interestingViolation(checkName, details, violationLevelAfter)) {
      Optional<StorageViolationEvent> lastViolationEvent = lastViolationOfCheck(checkName);
      long lastViolationOfCheck = lastViolationEvent.map(StorageViolationEvent::timePassedSince).orElseGet(System::currentTimeMillis);
      if (lastViolationOfCheck > VIOLATION_INSERT_CHECK_COOLDOWN) {
        if (interestingViolations.size() < VIOLATION_OVERALL_LIMIT) {
          StorageViolationEvent event = new StorageViolationEvent(
            checkName.toLowerCase(Locale.ROOT),
            details.toLowerCase(Locale.ROOT),
            IntavePlugin.fullVersion().toLowerCase(Locale.ROOT),
            violationLevelAfter,
            System.currentTimeMillis()
          );
          addViolationEvent(event);
        }
      } else if (lastViolationOfCheck < VIOLATION_UPDATE_CHECK_TIMEOUT && lastViolationEvent.isPresent()) {
        StorageViolationEvent storageViolationEvent = lastViolationEvent.get();
        if (violationLevelAfter > storageViolationEvent.violationLevel()) {
          storageViolationEvent.setViolationLevel(violationLevelAfter);
          storageViolationEvent.setTimestamp(System.currentTimeMillis());
        }
      }
    }
  }

  public boolean interestingViolation(String checkName, String description, int vl) {
    switch (checkName.toLowerCase(Locale.ROOT)) {
      case "attackraytrace":
        return vl > 100;
      case "heuristics":
        return description.contains("!");
      case "physics":
      case "placementanalysis":
        return vl > 500;
      default:
        return false;
    }
  }

  private Optional<StorageViolationEvent> lastViolationOfCheck(String check) {
    return interestingViolations.stream()
      .filter(event -> event.checkName().equalsIgnoreCase(check))
      .max(Comparator.comparingLong(StorageViolationEvent::timestamp));
  }

  private void addViolationEvent(StorageViolationEvent event) {
    interestingViolations.add(event);
  }

  @Override
  public void writeTo(ByteArrayDataOutput output) {
    clearOldViolations();
    interestingViolations.writeTo(output);
  }

  @Override
  public void readFrom(ByteArrayDataInput input) {
    interestingViolations.readFrom(input);
    clearOldViolations();
  }

  private void clearOldViolations() {
    interestingViolations = interestingViolations.withoutViolationsOlderThan(
      VIOLATION_ALLOWED_LIFETIME, TimeUnit.MILLISECONDS
    );
  }

  @Override
  public int id() {
    return 1;
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public boolean sameContentsAs(Storage other) {
    if (!(other instanceof LongTermViolationStorage)) {
      return false;
    }
    LongTermViolationStorage otherStorage = (LongTermViolationStorage) other;
    return otherStorage.interestingViolations.sameContentsAs(interestingViolations);
  }

  public StorageViolationEvents violations() {
    return interestingViolations;
  }
}
