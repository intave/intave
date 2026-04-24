package de.jpx3.intave.check.world.interaction;

import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.MovingObjectPosition;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Interaction {
  private final long interactionId;
  private final PacketWrapper<?> packet;
  private final World world;
  private final Player player;
  private final BlockPosition targetBlock;
  private final int targetDirection;
  private InteractionType type;
  private final Material itemTypeInHand;
  private final ItemStack itemInHand;
  private final InteractionHand hand;
  private final DiggingAction digType;
  private final float facingX, facingY, facingZ;
  private boolean entered = false;

  private boolean sendPacket = true;
  private boolean hasBeenEmulated = false;
  private BlockPosition emulationPosition;

  private final int sequenceNumber;
  private boolean blockPlacementEmulation = false;

  private MovingObjectPosition raytraceResult;

  public Interaction(
    long interactionId, PacketWrapper<?> packet,
    World world, Player player,
    BlockPosition targetBlock, int targetDirection,
    InteractionType type,
    Material itemTypeInHand, ItemStack itemInHand,
    InteractionHand hand, DiggingAction digType,
    float facingX, float facingY, float facingZ,
    int sequenceNumber
  ) {
    this.interactionId = interactionId;
    this.packet = packet;
    this.world = world;
    this.player = player;
    this.targetBlock = targetBlock;
    this.targetDirection = targetDirection;
    this.type = type;
    this.itemTypeInHand = itemTypeInHand;
    this.itemInHand = itemInHand;
    this.hand = hand;
    this.digType = digType;
    this.facingX = facingX;
    this.facingY = facingY;
    this.facingZ = facingZ;
    this.sequenceNumber = sequenceNumber;
  }

  public long interactionId() {
    return interactionId;
  }

  public PacketWrapper<?> packet() {
    return packet;
  }

  public InteractionType type() {
    return type;
  }

  public void overrideType(InteractionType interactionType) {
    type = interactionType;
  }

  public World world() {
    return world;
  }

  public Player player() {
    return player;
  }

  public Material itemTypeInHand() {
    return itemTypeInHand;
  }

  public ItemStack itemInHand() {
    return itemInHand;
  }

  public InteractionHand hand() {
    return hand;
  }

  public BlockPosition targetBlock() {
    return targetBlock;
  }

  public de.jpx3.intave.share.BlockPosition targetBlockAsPosition() {
    return targetBlock;
  }

  public boolean hasTargetBlock() {
    return targetBlock != null && (targetBlock.getX() != -1 && targetBlock.getY() != -1 && targetBlock.getZ() != -1 && targetDirection != 255) ;
  }

  public Direction targetDirection() {
    return Direction.getFront(targetDirectionIndex());
  }

  public int targetDirectionIndex() {
    return targetDirection;
  }

  public DiggingAction digType() {
    return digType;
  }

  public boolean shouldSendPacket() {
    return sendPacket;
  }

  public void doNotSendPacket() {
    sendPacket = false;
  }

  public boolean hasBeenEmulated() {
    return hasBeenEmulated;
  }

  public void setEmulated() {
    hasBeenEmulated = true;
  }

  public boolean hasFacing() {
    return !(Float.isNaN(facingX) || Float.isNaN(facingY) || Float.isNaN(facingZ))
      && !(facingX == -1 && facingY == -1 && facingZ == -1);
  }

  public float facingX() {
    return facingX;
  }

  public float facingY() {
    return facingY;
  }

  public float facingZ() {
    return facingZ;
  }

  public MovingObjectPosition raytraceResult() {
    return raytraceResult;
  }

  public void setRaytraceResult(MovingObjectPosition rayTraceResult) {
    this.raytraceResult = rayTraceResult;
  }

  public boolean hasRaytraceResult() {
    return raytraceResult != null;
  }

  public int sequenceNumber() {
    return sequenceNumber;
  }

  public void enter() {
    entered = true;
  }

  public boolean entered() {
    return entered;
  }

  public boolean wasPlacementEmulated() {
    return blockPlacementEmulation;
  }

  public void markPlacementEmulated() {
    blockPlacementEmulation = true;
  }

  public void setEmulationPosition(BlockPosition blockPosition) {
    emulationPosition = blockPosition;
  }

  public BlockPosition emulationBlockPosition() {
    return emulationPosition;
  }
}
