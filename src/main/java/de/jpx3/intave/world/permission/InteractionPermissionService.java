package de.jpx3.intave.world.permission;

import de.jpx3.intave.access.BlockBreakPermissionCheck;
import de.jpx3.intave.access.BlockPlacePermissionCheck;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.patchy.PatchyLoadingInjector;

public final class InteractionPermissionService {
  private BlockPlacePermissionCheck blockPlacePermissionCheck;
  private BlockBreakPermissionCheck blockBreakPermissionCheck;

  public InteractionPermissionService() {
    setup();
  }

  public void setup() {

    // placement
    ClassLoader classLoader = InteractionPermissionService.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.world.permission.CustomCraftBlock");
    String className;
    if (ProtocolLibAdapter.COMBAT_UPDATE.atOrAbove()) {
      className = "de.jpx3.intave.world.permission.DualHandCBPlacePermissionResolver";
    } else {
      className = "de.jpx3.intave.world.permission.LegacyCBPlacePermissionResolver";
    }
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, className);
    blockPlacePermissionCheck = instanceOf(className);
    blockPlacePermissionCheck.open();

    // break
    blockBreakPermissionCheck = new CBBreakPermissionResolver();
    blockBreakPermissionCheck.open();
  }

  private <T> T instanceOf(String className) {
    try {
      return (T) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public BlockPlacePermissionCheck blockPlacePermissionCheck() {
    return blockPlacePermissionCheck;
  }

  public void setBlockPlacePermissionCheck(BlockPlacePermissionCheck blockPlacePermissionCheck) {
    this.blockPlacePermissionCheck.close();
    this.blockPlacePermissionCheck = blockPlacePermissionCheck;
    this.blockPlacePermissionCheck.open();
  }

  public BlockBreakPermissionCheck blockBreakPermissionCheck() {
    return blockBreakPermissionCheck;
  }

  public void setBlockBreakPermissionCheck(BlockBreakPermissionCheck blockBreakPermissionCheck) {
    this.blockBreakPermissionCheck.close();
    this.blockBreakPermissionCheck = blockBreakPermissionCheck;
    this.blockBreakPermissionCheck.open();
  }
}
