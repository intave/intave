package de.jpx3.intave.check.world.interaction;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.MovingObjectPosition;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Interaction {
  private final PacketContainer thePacket;
  private final World world;
  private final Player player;
  private final BlockPosition targetBlock;
  private final int targetDirection;
  private final InteractionType type;
  private final Material itemTypeInHand;
  private final ItemStack itemInHand;
  private final EnumWrappers.Hand hand;
  private final EnumWrappers.PlayerDigType digType;
  private final float facingX, facingY, facingZ;
  private boolean entered = false;

  private boolean sendPacket = true;

  private MovingObjectPosition raytraceResult;

  public Interaction(
    PacketContainer thePacket,
    World world, Player player,
    BlockPosition targetBlock, int targetDirection,
    InteractionType type,
    Material itemTypeInHand, ItemStack itemInHand,
    EnumWrappers.Hand hand, EnumWrappers.PlayerDigType digType,
    float facingX, float facingY, float facingZ
  ) {
    this.thePacket = thePacket;
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
  }

  public PacketContainer thePacket() {
    return thePacket;
  }

  public InteractionType type() {
    return type;
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

  public EnumWrappers.Hand hand() {
    return hand;
  }

  public BlockPosition targetBlock() {
    return targetBlock;
  }

  public de.jpx3.intave.share.BlockPosition targetBlockAsPosition() {
    return new de.jpx3.intave.share.BlockPosition(targetBlock);
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

  public EnumWrappers.PlayerDigType digType() {
    return digType;
  }

  public boolean shouldSendPacket() {
    return sendPacket;
  }

  public void doNotSendPacket() {
    sendPacket = false;
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

  public void enter() {
    entered = true;
  }

  public boolean entered() {
    return entered;
  }
}
