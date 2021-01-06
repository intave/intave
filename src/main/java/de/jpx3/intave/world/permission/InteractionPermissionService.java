package de.jpx3.intave.world.permission;

import de.jpx3.patchy.PatchyLoadingInjector;
import de.jpx3.intave.access.BlockPlacePermissionCheck;

public final class InteractionPermissionService {

  private BlockPlacePermissionCheck blockPlacePermissionCheck;

  public InteractionPermissionService() {
    setup();
  }

  public void setup() {
    ClassLoader classLoader = InteractionPermissionService.class.getClassLoader();

    // class load
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.world.permission.CustomCraftBlock");
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.world.permission.CraftBukkitPlacePermissionResolver");

    // initialize
    blockPlacePermissionCheck = instanceOf("de.jpx3.intave.world.permission.CraftBukkitPlacePermissionResolver");
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
    this.blockPlacePermissionCheck = blockPlacePermissionCheck;
  }
}
