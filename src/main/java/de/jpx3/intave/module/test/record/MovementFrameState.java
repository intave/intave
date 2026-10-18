/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.test.record;


import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.check.movement.physics.environment.MoveMetric;
import de.jpx3.intave.check.movement.physics.simulator.BoatSimulator;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.EffectMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import io.netty.buffer.ByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Movement-relevant state sampled alongside one movement packet.
 *
 * <p>This is embedded in {@link MoveFrame}'s versioned binary layout. Frames from older recording
 * versions decode with no snapshot, so existing PTRs continue to replay with their historical
 * defaults.
 */
public final class MovementFrameState {
	private static final StreamCodec<ByteBuf, ByteBuf, EntityState> NULLABLE_ENTITY_CODEC =
		EntityState.STREAM_CODEC.nullable(ByteBufStreamCodecs.BOOLEAN);
	private static final StreamCodec<ByteBuf, ByteBuf, List<EntityState>> ENTITY_LIST_CODEC =
		ByteBufStreamCodecs.listCodecOf(EntityState.STREAM_CODEC);

	public static final StreamCodec<ByteBuf, ByteBuf, MovementFrameState> STREAM_CODEC =
		ByteBufStreamCodecs.smartReflectionCodecBuilder(MovementFrameState.class)
			.field("abilities", AbilityState.STREAM_CODEC, AbilityState::empty)
			.field("effects", EffectState.STREAM_CODEC, EffectState::empty)
			.field("inventory", InventoryState.STREAM_CODEC, InventoryState::empty)
			.field("fireworkRocketsPower", ByteBufStreamCodecs.INTEGER, () -> 1)
			.field("activeFireworkRockets", ByteBufStreamCodecs.INTEGER, () -> 0)
			.field("vehicle", NULLABLE_ENTITY_CODEC, () -> null)
			.field("tracedEntities", ENTITY_LIST_CODEC, LinkedList::new)
			.field("boatStatus", ByteBufStreamCodecs.STRING, () -> BoatSimulator.Status.ON_LAND.name())
			.field("previousBoatStatus", ByteBufStreamCodecs.STRING, () -> BoatSimulator.Status.ON_LAND.name())
			.field("boatGlide", ByteBufStreamCodecs.FLOAT, () -> 0.0F)
			.field("boatWaterLevel", ByteBufStreamCodecs.DOUBLE, () -> 0.0D)
			.field("reduceTicks", ByteBufStreamCodecs.INTEGER, () -> 0)
			.field("attackReduceTicksPast", ByteBufStreamCodecs.INTEGER, () -> 100)
			.field("entityUseTicksPast", ByteBufStreamCodecs.INTEGER, () -> 100)
			.build();

	private static final MovementFrameState EMPTY = new MovementFrameState(
		AbilityState.empty(), EffectState.empty(), InventoryState.empty(),
		1, 0, null, Collections.emptyList(),
		BoatSimulator.Status.ON_LAND.name(), BoatSimulator.Status.ON_LAND.name(),
		0.0F, 0.0D, 0, 100, 100
	);

	private final AbilityState abilities;
	private final EffectState effects;
	private final InventoryState inventory;
	private final int fireworkRocketsPower;
	private final int activeFireworkRockets;
	private final @Nullable EntityState vehicle;
	private final List<EntityState> tracedEntities;
	private final String boatStatus;
	private final String previousBoatStatus;
	private final float boatGlide;
	private final double boatWaterLevel;
	private final int reduceTicks;
	private final int attackReduceTicksPast;
	private final int entityUseTicksPast;

	public MovementFrameState(
		AbilityState abilities,
		EffectState effects,
		InventoryState inventory,
		int fireworkRocketsPower,
		int activeFireworkRockets,
		@Nullable EntityState vehicle,
		List<EntityState> tracedEntities,
		String boatStatus,
		String previousBoatStatus,
		float boatGlide,
		double boatWaterLevel,
		int reduceTicks,
		int attackReduceTicksPast,
		int entityUseTicksPast
	) {
		this.abilities = Objects.requireNonNull(abilities, "abilities");
		this.effects = Objects.requireNonNull(effects, "effects");
		this.inventory = Objects.requireNonNull(inventory, "inventory");
		this.fireworkRocketsPower = fireworkRocketsPower;
		this.activeFireworkRockets = activeFireworkRockets;
		this.vehicle = vehicle;
		this.tracedEntities = new ArrayList<>(Objects.requireNonNull(tracedEntities, "tracedEntities"));
		this.boatStatus = Objects.requireNonNull(boatStatus, "boatStatus");
		this.previousBoatStatus = Objects.requireNonNull(previousBoatStatus, "previousBoatStatus");
		this.boatGlide = boatGlide;
		this.boatWaterLevel = boatWaterLevel;
		this.reduceTicks = reduceTicks;
		this.attackReduceTicksPast = attackReduceTicksPast;
		this.entityUseTicksPast = entityUseTicksPast;
	}

	public static MovementFrameState capture(User user) {
		MovementMetadata movement = user.meta().movement();
		List<EntityState> tracedEntities = new ArrayList<>();
		for (Entity entity : user.meta().connection().tracedEntities()) {
			tracedEntities.add(EntityState.capture(entity));
		}
		return new MovementFrameState(
			AbilityState.capture(user),
			EffectState.capture(user),
			InventoryState.capture(user),
			movement.fireworkRocketsPower(),
			movement.activeFireworkRockets(),
			movement.vehicle() == null ? null : EntityState.capture(movement.vehicle()),
			tracedEntities,
			movement.boatStatus().name(),
			movement.previousBoatStatus().name(),
			movement.boatGlide(),
			movement.boatWaterLevel(),
			movement.reduceTicks(),
			movement.ticksPast(MoveMetric.ATTACK_REDUCE),
			movement.ticksPast(MoveMetric.ENTITY_USE)
		);
	}

	public static MovementFrameState empty() {
		return EMPTY;
	}

	/** Applies the snapshot to the mutable state used by the recording replay. */
	public void applyTo(User user, PlayerInventory playerInventory) {
		applyTo(user, playerInventory, true);
	}

	/** Applies stable frame state while retaining item-use state reconstructed from packets. */
	public void applyToPreservingItemUsePackets(
		User user, PlayerInventory playerInventory
	) {
		applyTo(user, playerInventory, false);
	}

	private void applyTo(
		User user, PlayerInventory playerInventory, boolean restoreItemUseState
	) {
		abilities.applyTo(user.meta().abilities());
		effects.applyTo(user.meta().potions());
		inventory.applyTo(
			playerInventory, user.meta().inventory(), restoreItemUseState
		);

		MovementMetadata movement = user.meta().movement();
		movement.restoreRecordedFireworkState(fireworkRocketsPower, activeFireworkRockets);
		movement.restoreRecordedVehicle(vehicle == null ? null : vehicle.toEntity());
		movement.setBoatStatus(statusOf(boatStatus));
		movement.setPreviousBoatStatus(statusOf(previousBoatStatus));
		movement.setBoatGlide(boatGlide);
		movement.setBoatWaterLevel(boatWaterLevel);
		movement.restoreRecordedCombatState(
			reduceTicks, attackReduceTicksPast, entityUseTicksPast
		);

		List<Entity> replayEntities = user.meta().connection().tracedEntities();
		replayEntities.clear();
		for (EntityState entity : tracedEntities) {
			replayEntities.add(entity.toEntity());
		}
	}

	private static BoatSimulator.Status statusOf(String value) {
		try {
			return BoatSimulator.Status.valueOf(value);
		} catch (IllegalArgumentException ignored) {
			return BoatSimulator.Status.ON_LAND;
		}
	}

	public AbilityState abilities() {
		return abilities;
	}

	public EffectState effects() {
		return effects;
	}

	public InventoryState inventory() {
		return inventory;
	}

	public @Nullable EntityState vehicle() {
		return vehicle;
	}

	public List<EntityState> tracedEntities() {
		return new ArrayList<>(tracedEntities);
	}

	public int reduceTicks() {
		return reduceTicks;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof MovementFrameState)) return false;
		MovementFrameState other = (MovementFrameState) obj;
		return fireworkRocketsPower == other.fireworkRocketsPower
			&& activeFireworkRockets == other.activeFireworkRockets
			&& reduceTicks == other.reduceTicks
			&& attackReduceTicksPast == other.attackReduceTicksPast
			&& entityUseTicksPast == other.entityUseTicksPast
			&& Float.compare(boatGlide, other.boatGlide) == 0
			&& Double.compare(boatWaterLevel, other.boatWaterLevel) == 0
			&& abilities.equals(other.abilities)
			&& effects.equals(other.effects)
			&& inventory.equals(other.inventory)
			&& Objects.equals(vehicle, other.vehicle)
			&& tracedEntities.equals(other.tracedEntities)
			&& boatStatus.equals(other.boatStatus)
			&& previousBoatStatus.equals(other.previousBoatStatus);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			abilities, effects, inventory, fireworkRocketsPower, activeFireworkRockets,
			vehicle, tracedEntities, boatStatus, previousBoatStatus, boatGlide, boatWaterLevel,
			reduceTicks, attackReduceTicksPast, entityUseTicksPast
		);
	}

	public static final class AbilityState {
		public static final StreamCodec<ByteBuf, ByteBuf, AbilityState> STREAM_CODEC =
			ByteBufStreamCodecs.smartReflectionCodecBuilder(AbilityState.class)
				.field("flying", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("allowFlying", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("disabledFlying", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("startedFlying", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("flySpeed", ByteBufStreamCodecs.FLOAT, () -> 0.1F)
				.field("gameMode", ByteBufStreamCodecs.STRING, () -> GameMode.SURVIVAL.name())
				.build();

		private final boolean flying;
		private final boolean allowFlying;
		private final boolean disabledFlying;
		private final boolean startedFlying;
		private final float flySpeed;
		private final String gameMode;

		public AbilityState(
			boolean flying, boolean allowFlying, boolean disabledFlying, boolean startedFlying,
			float flySpeed, String gameMode
		) {
			this.flying = flying;
			this.allowFlying = allowFlying;
			this.disabledFlying = disabledFlying;
			this.startedFlying = startedFlying;
			this.flySpeed = flySpeed;
			this.gameMode = Objects.requireNonNull(gameMode, "gameMode");
		}

		static AbilityState capture(User user) {
			AbilityMetadata abilities = user.meta().abilities();
			GameMode gameMode = user.player().getGameMode();
			return new AbilityState(
				abilities.flying(), abilities.allowFlying(), abilities.disabledFlying,
				abilities.startedFlying(),
				abilities.flySpeed(), gameMode == null ? GameMode.SURVIVAL.name() : gameMode.name()
			);
		}

		public static AbilityState empty() {
			// Matches FakePlayerFactory's historical 0.2F getFlySpeed response after
			// AbilityMetadata applies its client-speed division.
			return new AbilityState(false, false, false, false, 0.1F, GameMode.SURVIVAL.name());
		}

		void applyTo(AbilityMetadata abilities) {
			abilities.setAllowFlying(allowFlying);
			abilities.setFlying(flying);
			abilities.disabledFlying = disabledFlying;
			abilities.setStartedFlying(startedFlying);
			abilities.setFlySpeed(flySpeed);
			AbilityTracker.GameMode replayGameMode = AbilityTracker.GameMode.fromBukkit(gameMode());
			abilities.setGameMode(replayGameMode);
			abilities.setPendingGameMode(replayGameMode);
		}

		public boolean flying() {
			return flying;
		}

		public boolean allowFlying() {
			return allowFlying;
		}

		public float flySpeed() {
			return flySpeed;
		}

		public GameMode gameMode() {
			try {
				return GameMode.valueOf(gameMode);
			} catch (IllegalArgumentException ignored) {
				return GameMode.SURVIVAL;
			}
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof AbilityState)) return false;
			AbilityState other = (AbilityState) obj;
			return flying == other.flying && allowFlying == other.allowFlying
				&& disabledFlying == other.disabledFlying
				&& startedFlying == other.startedFlying
				&& Float.compare(flySpeed, other.flySpeed) == 0
				&& gameMode.equals(other.gameMode);
		}

		@Override
		public int hashCode() {
			return Objects.hash(flying, allowFlying, disabledFlying, startedFlying, flySpeed, gameMode);
		}
	}

	public static final class EffectState {
		private static final StreamCodec<ByteBuf, ByteBuf, List<EffectInstance>> EFFECT_LIST_CODEC =
			ByteBufStreamCodecs.listCodecOf(EffectInstance.STREAM_CODEC);
		public static final StreamCodec<ByteBuf, ByteBuf, EffectState> STREAM_CODEC =
			ByteBufStreamCodecs.smartReflectionCodecBuilder(EffectState.class)
				.field("speedAmplifier", ByteBufStreamCodecs.INTEGER, () -> 0)
				.field("speedDuration", ByteBufStreamCodecs.INTEGER, () -> 0)
				.field("slownessAmplifier", ByteBufStreamCodecs.INTEGER, () -> 0)
				.field("slownessDuration", ByteBufStreamCodecs.INTEGER, () -> 0)
				.field("jumpAmplifier", ByteBufStreamCodecs.INTEGER, () -> 0)
				.field("jumpDuration", ByteBufStreamCodecs.INTEGER, () -> 0)
				.field("activeEffects", EFFECT_LIST_CODEC, LinkedList::new)
				.build();

		private final int speedAmplifier;
		private final int speedDuration;
		private final int slownessAmplifier;
		private final int slownessDuration;
		private final int jumpAmplifier;
		private final int jumpDuration;
		private final List<EffectInstance> activeEffects;

		public EffectState(
			int speedAmplifier, int speedDuration,
			int slownessAmplifier, int slownessDuration,
			int jumpAmplifier, int jumpDuration,
			List<EffectInstance> activeEffects
		) {
			this.speedAmplifier = speedAmplifier;
			this.speedDuration = speedDuration;
			this.slownessAmplifier = slownessAmplifier;
			this.slownessDuration = slownessDuration;
			this.jumpAmplifier = jumpAmplifier;
			this.jumpDuration = jumpDuration;
			this.activeEffects = new ArrayList<>(Objects.requireNonNull(activeEffects, "activeEffects"));
		}

		static EffectState capture(User user) {
			EffectMetadata effects = user.meta().potions();
			List<EffectInstance> activeEffects = new ArrayList<>();
			Collection<PotionEffect> playerEffects = user.player().getActivePotionEffects();
			if (playerEffects != null) {
				for (PotionEffect effect : playerEffects) {
					activeEffects.add(EffectInstance.capture(effect));
				}
			}
			return new EffectState(
				effects.potionEffectSpeedAmplifier(), effects.potionEffectSpeedDuration,
				effects.potionEffectSlownessAmplifier(), effects.potionEffectSlownessDuration,
				effects.potionEffectJumpAmplifier(), effects.potionEffectJumpDuration,
				activeEffects
			);
		}

		public static EffectState empty() {
			return new EffectState(0, 0, 0, 0, 0, 0, Collections.emptyList());
		}

		void applyTo(EffectMetadata effects) {
			effects.restoreRecordedState(
				speedAmplifier, speedDuration,
				slownessAmplifier, slownessDuration,
				jumpAmplifier, jumpDuration
			);
		}

		public Collection<PotionEffect> activePotionEffects() {
			List<PotionEffect> effects = new ArrayList<>();
			for (EffectInstance effect : activeEffects) {
				PotionEffect restored = effect.toPotionEffect();
				if (restored != null) {
					effects.add(restored);
				}
			}
			return effects;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof EffectState)) return false;
			EffectState other = (EffectState) obj;
			return speedAmplifier == other.speedAmplifier && speedDuration == other.speedDuration
				&& slownessAmplifier == other.slownessAmplifier && slownessDuration == other.slownessDuration
				&& jumpAmplifier == other.jumpAmplifier && jumpDuration == other.jumpDuration
				&& activeEffects.equals(other.activeEffects);
		}

		@Override
		public int hashCode() {
			return Objects.hash(
				speedAmplifier, speedDuration, slownessAmplifier, slownessDuration,
				jumpAmplifier, jumpDuration, activeEffects
			);
		}
	}

	public static final class EffectInstance {
		public static final StreamCodec<ByteBuf, ByteBuf, EffectInstance> STREAM_CODEC =
			ByteBufStreamCodecs.smartReflectionCodecBuilder(EffectInstance.class)
				.field("type", ByteBufStreamCodecs.STRING)
				.field("duration", ByteBufStreamCodecs.INTEGER)
				.field("amplifier", ByteBufStreamCodecs.INTEGER)
				.field("ambient", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.build();

		private final String type;
		private final int duration;
		private final int amplifier;
		private final boolean ambient;

		public EffectInstance(String type, int duration, int amplifier, boolean ambient) {
			this.type = Objects.requireNonNull(type, "type");
			this.duration = duration;
			this.amplifier = amplifier;
			this.ambient = ambient;
		}

		static EffectInstance capture(PotionEffect effect) {
			return new EffectInstance(
				effect.getType().getName(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient()
			);
		}

		@Nullable PotionEffect toPotionEffect() {
			PotionEffectType effectType = PotionEffectType.getByName(type);
			if (effectType == null) {
				return null;
			}
			return new PotionEffect(effectType, duration, amplifier, ambient);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof EffectInstance)) return false;
			EffectInstance other = (EffectInstance) obj;
			return duration == other.duration && amplifier == other.amplifier
				&& ambient == other.ambient && type.equals(other.type);
		}

		@Override
		public int hashCode() {
			return Objects.hash(type, duration, amplifier, ambient);
		}
	}

	public static final class InventoryState {
		private static final StreamCodec<ByteBuf, ByteBuf, ItemState> NULLABLE_ITEM_CODEC =
			ItemState.STREAM_CODEC.nullable(ByteBufStreamCodecs.BOOLEAN);
		private static final StreamCodec<ByteBuf, ByteBuf, List<ItemState>> ITEM_LIST_CODEC =
			ByteBufStreamCodecs.listCodecOf(NULLABLE_ITEM_CODEC);
		private static final StreamCodec<ByteBuf, ByteBuf, ItemState> NULLABLE_SINGLE_ITEM_CODEC =
			ItemState.STREAM_CODEC.nullable(ByteBufStreamCodecs.BOOLEAN);
		private static final StreamCodec<ByteBuf, ByteBuf, PlayerHand> NULLABLE_HAND_CODEC =
			ByteBufStreamCodecs.INTEGER.beforeAndAfter(
				ordinal -> ordinal < 0 ? null : PlayerHand.values()[ordinal],
				hand -> hand == null ? -1 : hand.ordinal()
			);

		public static final StreamCodec<ByteBuf, ByteBuf, InventoryState> STREAM_CODEC =
			ByteBufStreamCodecs.smartReflectionCodecBuilder(InventoryState.class)
				.field("storage", ITEM_LIST_CODEC, LinkedList::new)
				.field("armor", ITEM_LIST_CODEC, LinkedList::new)
				.field("offhand", NULLABLE_SINGLE_ITEM_CODEC, () -> null)
				.field("heldSlot", ByteBufStreamCodecs.INTEGER, () -> 0)
				.field("handActive", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("handActiveTicks", ByteBufStreamCodecs.INTEGER, () -> 0)
				.field("pastHandActiveTicks", ByteBufStreamCodecs.INTEGER, () -> 100)
				.field("pastItemUsageTransition", ByteBufStreamCodecs.INTEGER, () -> 0)
				.field("inventoryOpen", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("activeItemType", ByteBufStreamCodecs.MATERIAL, () -> Material.AIR)
				.field("foodItem", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("releaseItemNextTick", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("releaseItemType", ByteBufStreamCodecs.MATERIAL, () -> Material.AIR)
				.field("activatedItemThisTick", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("deactivatedItemThisTick", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("activeHand", NULLABLE_HAND_CODEC, () -> null)
				.field("activeItemAllowsSprinting", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("activeItemUseSpeedMultiplier", ByteBufStreamCodecs.FLOAT, () -> 0.2F)
				.build();

		private final List<ItemState> storage;
		private final List<ItemState> armor;
		private final @Nullable ItemState offhand;
		private final int heldSlot;
		private final boolean handActive;
		private final int handActiveTicks;
		private final int pastHandActiveTicks;
		private final int pastItemUsageTransition;
		private final boolean inventoryOpen;
		private final Material activeItemType;
		private final boolean foodItem;
		private final boolean releaseItemNextTick;
		private final Material releaseItemType;
		private final boolean activatedItemThisTick;
		private final boolean deactivatedItemThisTick;
		private final @Nullable PlayerHand activeHand;
		private final boolean activeItemAllowsSprinting;
		private final float activeItemUseSpeedMultiplier;

		public InventoryState(
			List<ItemState> storage, List<ItemState> armor, @Nullable ItemState offhand,
			int heldSlot, boolean handActive, int handActiveTicks, int pastHandActiveTicks,
			int pastItemUsageTransition, boolean inventoryOpen, Material activeItemType,
			boolean foodItem, boolean releaseItemNextTick, Material releaseItemType,
			boolean activatedItemThisTick, boolean deactivatedItemThisTick,
			@Nullable PlayerHand activeHand, boolean activeItemAllowsSprinting,
			float activeItemUseSpeedMultiplier
		) {
			this.storage = new ArrayList<>(Objects.requireNonNull(storage, "storage"));
			this.armor = new ArrayList<>(Objects.requireNonNull(armor, "armor"));
			this.offhand = offhand;
			this.heldSlot = heldSlot;
			this.handActive = handActive;
			this.handActiveTicks = handActiveTicks;
			this.pastHandActiveTicks = pastHandActiveTicks;
			this.pastItemUsageTransition = pastItemUsageTransition;
			this.inventoryOpen = inventoryOpen;
			this.activeItemType = Objects.requireNonNull(activeItemType, "activeItemType");
			this.foodItem = foodItem;
			this.releaseItemNextTick = releaseItemNextTick;
			this.releaseItemType = Objects.requireNonNull(releaseItemType, "releaseItemType");
			this.activatedItemThisTick = activatedItemThisTick;
			this.deactivatedItemThisTick = deactivatedItemThisTick;
			this.activeHand = activeHand;
			this.activeItemAllowsSprinting = activeItemAllowsSprinting;
			this.activeItemUseSpeedMultiplier = activeItemUseSpeedMultiplier;
		}

		static InventoryState capture(User user) {
			PlayerInventory inventory = user.player().getInventory();
			InventoryMetadata metadata = user.meta().inventory();
			return new InventoryState(
				captureItems(storageContents(inventory)),
				captureItems(inventory.getArmorContents()),
				ItemState.capture(MinecraftVersions.VER1_9_0.atOrAbove() ? inventory.getItemInOffHand() : null),
				metadata.handSlot(), metadata.handActive(), metadata.handActiveTicks,
				metadata.pastHandActiveTicks, metadata.pastItemUsageTransition,
				metadata.inventoryOpen(), metadata.activeItemType(), metadata.foodItem(),
				metadata.releaseItemNextTick, metadata.releaseItemType,
				metadata.activatedItemThisTick, metadata.deactivatedItemThisTick,
				metadata.handActive() ? metadata.activeHand() : null,
				metadata.activeItemAllowsSprinting(),
				metadata.activeItemUseSpeedMultiplier()
			);
		}

		private static ItemStack[] storageContents(PlayerInventory inventory) {
			if (MinecraftVersions.VER1_9_0.atOrAbove()) {
				return inventory.getStorageContents();
			}
			ItemStack[] contents = inventory.getContents();
			ItemStack[] storage = new ItemStack[Math.min(36, contents.length)];
			System.arraycopy(contents, 0, storage, 0, storage.length);
			return storage;
		}

		public static InventoryState empty() {
			return new InventoryState(
				Collections.emptyList(), Collections.emptyList(), null,
				0, false, 0, 100, 0, false, Material.AIR,
				false, false, Material.AIR, false, false,
				null, false, 0.2F
			);
		}

		private static List<ItemState> captureItems(ItemStack[] items) {
			List<ItemState> result = new ArrayList<>(items.length);
			for (ItemStack item : items) {
				result.add(ItemState.capture(item));
			}
			return result;
		}

		void applyTo(
			PlayerInventory inventory,
			InventoryMetadata metadata,
			boolean restoreItemUseState
		) {
			inventory.setStorageContents(toItems(storage));
			inventory.setArmorContents(toItems(armor));
			inventory.setItemInOffHand(offhand == null ? null : offhand.toItemStack());
			if (!restoreItemUseState) {
				return;
			}
			inventory.setHeldItemSlot(heldSlot);
			metadata.restoreRecordedState(
				heldSlot, handActive, handActiveTicks, pastHandActiveTicks,
				pastItemUsageTransition, inventoryOpen, activeItemType, foodItem,
				releaseItemNextTick, releaseItemType,
				activatedItemThisTick, deactivatedItemThisTick,
				activeHand, activeItemAllowsSprinting, activeItemUseSpeedMultiplier
			);
		}

		private static ItemStack[] toItems(List<ItemState> items) {
			ItemStack[] result = new ItemStack[items.size()];
			for (int index = 0; index < items.size(); index++) {
				ItemState item = items.get(index);
				result[index] = item == null ? null : item.toItemStack();
			}
			return result;
		}

		public boolean selectedMainHandItemChangedFrom(InventoryState previous) {
			Objects.requireNonNull(previous, "previous");
			return heldSlot == previous.heldSlot
				&& !Objects.equals(heldItem(), previous.heldItem());
		}

		public @Nullable ItemState heldItem() {
			return heldSlot >= 0 && heldSlot < storage.size()
				? storage.get(heldSlot) : null;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof InventoryState)) return false;
			InventoryState other = (InventoryState) obj;
			return heldSlot == other.heldSlot && handActive == other.handActive
				&& handActiveTicks == other.handActiveTicks
				&& pastHandActiveTicks == other.pastHandActiveTicks
				&& pastItemUsageTransition == other.pastItemUsageTransition
				&& inventoryOpen == other.inventoryOpen && foodItem == other.foodItem
				&& releaseItemNextTick == other.releaseItemNextTick
				&& activatedItemThisTick == other.activatedItemThisTick
				&& deactivatedItemThisTick == other.deactivatedItemThisTick
				&& activeHand == other.activeHand
				&& activeItemAllowsSprinting == other.activeItemAllowsSprinting
				&& Float.compare(
					activeItemUseSpeedMultiplier, other.activeItemUseSpeedMultiplier
				) == 0
				&& storage.equals(other.storage) && armor.equals(other.armor)
				&& Objects.equals(offhand, other.offhand)
				&& activeItemType == other.activeItemType && releaseItemType == other.releaseItemType;
		}

		@Override
		public int hashCode() {
			return Objects.hash(
				storage, armor, offhand, heldSlot, handActive, handActiveTicks,
				pastHandActiveTicks, pastItemUsageTransition, inventoryOpen, activeItemType,
				foodItem, releaseItemNextTick, releaseItemType,
				activatedItemThisTick, deactivatedItemThisTick,
				activeHand, activeItemAllowsSprinting, activeItemUseSpeedMultiplier
			);
		}
	}

	public static final class ItemState {
		private static final StreamCodec<ByteBuf, ByteBuf, Map<String, Integer>> ENCHANTMENTS_CODEC =
			ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.STRING, ByteBufStreamCodecs.INTEGER);
		public static final StreamCodec<ByteBuf, ByteBuf, ItemState> STREAM_CODEC =
			ByteBufStreamCodecs.smartReflectionCodecBuilder(ItemState.class)
				.field("material", ByteBufStreamCodecs.MATERIAL)
				.field("amount", ByteBufStreamCodecs.INTEGER)
				.field("durability", ByteBufStreamCodecs.INTEGER, () -> 0)
				.field("enchantments", ENCHANTMENTS_CODEC, HashMap::new)
				.build();

		private final Material material;
		private final int amount;
		private final int durability;
		private final Map<String, Integer> enchantments;

		public ItemState(Material material, int amount, int durability, Map<String, Integer> enchantments) {
			this.material = Objects.requireNonNull(material, "material");
			this.amount = amount;
			this.durability = durability;
			this.enchantments = new HashMap<>(Objects.requireNonNull(enchantments, "enchantments"));
		}

		static @Nullable ItemState capture(@Nullable ItemStack item) {
			if (item == null || item.getAmount() <= 0 || item.getType() == Material.AIR) {
				return null;
			}
			Map<String, Integer> enchantments = new HashMap<>();
			for (Map.Entry<Enchantment, Integer> enchantment : item.getEnchantments().entrySet()) {
				Enchantment type = enchantment.getKey();
				enchantments.put(type.getName(), enchantment.getValue());
			}
			return new ItemState(item.getType(), item.getAmount(), item.getDurability(), enchantments);
		}

		ItemStack toItemStack() {
			int itemAmount = Math.max(1, amount);
			if (Bukkit.getServer() == null) {
				ItemStack item = new ItemStack(Material.AIR, itemAmount);
				item.setType(material);
				return item;
			}
			ItemStack item = new ItemStack(material, itemAmount);
			if (durability != 0) {
				item.setDurability((short) durability);
			}
			for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
				Enchantment enchantment = enchantmentOf(entry.getKey());
				if (enchantment != null) {
					item.addUnsafeEnchantment(enchantment, entry.getValue());
				}
			}
			return item;
		}

		public Material material() {
			return material;
		}

		private static @Nullable Enchantment enchantmentOf(String value) {
			return Enchantment.getByName(value);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof ItemState)) return false;
			ItemState other = (ItemState) obj;
			return amount == other.amount && durability == other.durability
				&& material == other.material && enchantments.equals(other.enchantments);
		}

		@Override
		public int hashCode() {
			return Objects.hash(material, amount, durability, enchantments);
		}
	}

	public static final class EntityState {
		public static final StreamCodec<ByteBuf, ByteBuf, EntityState> STREAM_CODEC =
			ByteBufStreamCodecs.smartReflectionCodecBuilder(EntityState.class)
				.field("entityId", ByteBufStreamCodecs.INTEGER)
				.field("hasTypeData", ByteBufStreamCodecs.BOOLEAN, () -> true)
				.field("typeName", ByteBufStreamCodecs.STRING, () -> "unknown")
				.field("typeId", ByteBufStreamCodecs.INTEGER, () -> -1)
				.field("living", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("creationId", ByteBufStreamCodecs.INTEGER, () -> 0)
				.field("width", ByteBufStreamCodecs.FLOAT, () -> 0.0F)
				.field("height", ByteBufStreamCodecs.FLOAT, () -> 0.0F)
				.field("player", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("tracingEnabled", ByteBufStreamCodecs.BOOLEAN, () -> false)
				.field("x", ByteBufStreamCodecs.DOUBLE, () -> 0.0D)
				.field("y", ByteBufStreamCodecs.DOUBLE, () -> 0.0D)
				.field("z", ByteBufStreamCodecs.DOUBLE, () -> 0.0D)
				.build();

		private final int entityId;
		private final boolean hasTypeData;
		private final String typeName;
		private final int typeId;
		private final boolean living;
		private final int creationId;
		private final float width;
		private final float height;
		private final boolean player;
		private final boolean tracingEnabled;
		private final double x;
		private final double y;
		private final double z;

		public EntityState(
			int entityId, boolean hasTypeData, String typeName, int typeId,
			boolean living, int creationId, float width, float height,
			boolean player, boolean tracingEnabled, double x, double y, double z
		) {
			this.entityId = entityId;
			this.hasTypeData = hasTypeData;
			this.typeName = Objects.requireNonNull(typeName, "typeName");
			this.typeId = typeId;
			this.living = living;
			this.creationId = creationId;
			this.width = width;
			this.height = height;
			this.player = player;
			this.tracingEnabled = tracingEnabled;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		static EntityState capture(Entity entity) {
			EntityTypeData type = entity.hasTypeData() ? entity.typeData() : null;
			return new EntityState(
				entity.entityId(), type != null,
				type == null ? "unknown" : type.name(),
				type == null ? -1 : type.typeId(),
				type != null && type.isLivingEntity(),
				type == null ? 0 : type.creationId,
				type == null ? 0.0F : type.size().width(),
				type == null ? 0.0F : type.size().height(),
				entity.isPlayer, entity.tracingEnabled(),
				entity.position.posX, entity.position.posY, entity.position.posZ
			);
		}

		public Entity toEntity() {
			EntityTypeData type = new EntityTypeData(
				typeName, HitboxSize.of(width, height), typeId, living, creationId
			);
			Entity entity = new Entity(entityId, type, player);
			if (!hasTypeData) {
				entity.setTypeData(null);
			}
			entity.setPosition(x, y, z);
			entity.setResponseTracingEnabled(tracingEnabled);
			return entity;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof EntityState)) return false;
			EntityState other = (EntityState) obj;
			return entityId == other.entityId && hasTypeData == other.hasTypeData
				&& typeId == other.typeId && living == other.living && creationId == other.creationId
				&& Float.compare(width, other.width) == 0 && Float.compare(height, other.height) == 0
				&& player == other.player && tracingEnabled == other.tracingEnabled
				&& Double.compare(x, other.x) == 0 && Double.compare(y, other.y) == 0
				&& Double.compare(z, other.z) == 0 && typeName.equals(other.typeName);
		}

		@Override
		public int hashCode() {
			return Objects.hash(
				entityId, hasTypeData, typeName, typeId, living, creationId,
				width, height, player, tracingEnabled, x, y, z
			);
		}
	}
}
