package de.jpx3.intave.access;

import java.io.PrintStream;
import java.util.List;

public interface IntaveInternalAccess {
  void setTrustFactorResolver(TrustFactorResolver resolver);
  void setDefaultTrustFactor(TrustFactor defaultTrustFactor);

  void overrideBreakPermissionCheck(BlockBreakPermissionCheck check);
  void overridePlacePermissionCheck(BlockPlacePermissionCheck check);
  void overrideInteractPermissionCheck(BlockInteractionPermissionCheck check);
  void overrideBucketActionPermissionCheck(BucketActionPermissionCheck check);

  void subscribeOutputStream(PrintStream stream);
  void unsubscribeOutputStream(PrintStream stream);

  IntaveCheckAccess accessCheck(String checkName) throws UnknownCheckException;
  List<String> loadedCheckNames();
  void restart();
}