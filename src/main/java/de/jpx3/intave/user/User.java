package de.jpx3.intave.user;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.permission.PermissionCache;
import de.jpx3.intave.reflect.Reflection;
import de.jpx3.intave.tools.AccessHelper;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class User {
  private final Map<Class<? extends UserCustomCheckMeta>, UserCustomCheckMeta> customMetaPool = new ConcurrentHashMap<>();

  private final WeakReference<Player> playerRef;
  private final WeakReference<Object> nmsEntity;
  private final UserMeta userMeta;
  private final PermissionCache permissionCache;
  private final boolean hasPlayer;
  private boolean ignoreNextPacket;

  private User(Player player) {
    this.playerRef = new WeakReference<>(player);
    this.nmsEntity = new WeakReference<>(Reflection.resolveEntityNMSHandle(player));
    this.hasPlayer = player != null;
    this.userMeta = new UserMeta(player, this);
    this.permissionCache = new PermissionCache();
  }

  public UserMeta meta() {
    return this.userMeta;
  }

  public Object playerHandle() {
    return nmsEntity.get();
  }

  public Player player() {
    Player player = playerRef.get();
    if (player == null) {
      throw new IntaveInternalException("Unable to reference player through service repo: Fallback user lacks reference");
    }

    return player;
  }

  public boolean hasOnlinePlayer() {
    Player player = playerRef.get();
    return player != null && AccessHelper.isOnline(player);
  }

  public UserCustomCheckMeta customMeta(Class<? extends UserCustomCheckMeta> classTarget) {
    UserCustomCheckMeta userCustomCheckMeta = customMetaPool.get(classTarget);
    if (userCustomCheckMeta == null) {
      try {
        customMetaPool.put(classTarget, userCustomCheckMeta = classTarget.newInstance());
      } catch (InstantiationException | IllegalAccessException e) {
        e.printStackTrace();
      }
    }
    return userCustomCheckMeta;
  }

  public PermissionCache permissionCache() {
    return permissionCache;
  }

  public boolean shouldIgnoreNextPacket() {
    return ignoreNextPacket;
  }

  public void ignoreNextPacket() {
    this.ignoreNextPacket = true;
  }

  public void receiveNextPacket() {
    this.ignoreNextPacket = false;
  }

  public static User empty() {
    return new User(null);
  }

  public static User userFor(Player player) {
    return new User(player);
  }

  public static final class UserMeta {
    private final UserMetaViolationLevelData violationLevelData;
    private final UserMetaMovementData movementData;
    private final UserMetaAbilityData abilityData;
    private final UserMetaPotionData potionData;
    private final UserMetaClientData clientData;
    private final UserMetaSynchronizeData synchronizeData;
    private final UserMetaInventoryData inventoryData;
    private final UserMetaAttackData attackData;

    public UserMeta(Player player, User user) {
      this.violationLevelData = new UserMetaViolationLevelData();
      this.clientData = new UserMetaClientData(player);
      this.abilityData = new UserMetaAbilityData(player);
      this.potionData = new UserMetaPotionData();
      this.inventoryData = new UserMetaInventoryData(player);
      this.synchronizeData = new UserMetaSynchronizeData();
      this.movementData = new UserMetaMovementData(player, user);
      this.attackData = new UserMetaAttackData(player);
    }

    public UserMetaViolationLevelData violationLevelData() {
      return violationLevelData;
    }

    public UserMetaMovementData movementData() {
      return movementData;
    }

    public UserMetaInventoryData inventoryData() {
      return inventoryData;
    }

    public UserMetaAbilityData abilityData() {
      return abilityData;
    }

    public UserMetaPotionData potionData() {
      return potionData;
    }

    public UserMetaSynchronizeData synchronizeData() {
      return synchronizeData;
    }

    public UserMetaClientData clientData() {
      return clientData;
    }

    public UserMetaAttackData attackData() {
      return attackData;
    }
  }
}