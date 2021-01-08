package de.jpx3.intave.world.collision;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.logging.IntaveLogger;

import static de.jpx3.intave.adapter.ProtocolLibAdapter.AQUATIC_UPDATE;

public final class CollisionEngine {
  private static AbstractCollisionDefaultResolver collisionResolver;

  public static void setup() {
    IntaveLogger logger = IntavePlugin.singletonInstance().logger();
    MinecraftVersion version = ProtocolLibAdapter.serverVersion();
    logger.info("Generating block collisions");

    try {
      if (version.isAtLeast(ProtocolLibAdapter.BEE_UPDATE)) {
        collisionResolver = new CollisionResolverBeeUpdate();
      } else if (version.isAtLeast(AQUATIC_UPDATE)) {
        collisionResolver = new CollisionResolverVoxelShapes();
      } else {
        collisionResolver = new CollisionResolverLegacy();
      }
      collisionResolver.setup();

      logger.info("Generated successfully");
    } catch (Exception e) {
      logger.error("An error occurred while resolving block collisions");
      e.printStackTrace();
    }
  }

  public static AbstractCollisionDefaultResolver collisionResolver() {
    return collisionResolver;
  }
}