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

package de.jpx3.intave.packet.wire;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.wire.ability.AbilityInWirePacket;
import de.jpx3.intave.packet.wire.ability.AbilityOutWirePacket;
import de.jpx3.intave.packet.wire.block.*;
import de.jpx3.intave.packet.wire.chat.ChatWirePacket;
import de.jpx3.intave.packet.wire.chunk.*;
import de.jpx3.intave.packet.wire.client.ClientCommandWirePacket;
import de.jpx3.intave.packet.wire.client.ClientSettingsWirePacket;
import de.jpx3.intave.packet.wire.client.ClientTickEndWirePacket;
import de.jpx3.intave.packet.wire.connection.*;
import de.jpx3.intave.packet.wire.cookie.CookieRequestWirePacket;
import de.jpx3.intave.packet.wire.cookie.CookieResponseWirePacket;
import de.jpx3.intave.packet.wire.cookie.StoreCookieWirePacket;
import de.jpx3.intave.packet.wire.entity.*;
import de.jpx3.intave.packet.wire.item.CarriedItemSlotWirePacket;
import de.jpx3.intave.packet.wire.item.HeldItemSlotWirePacket;
import de.jpx3.intave.packet.wire.item.PickItemWirePacket;
import de.jpx3.intave.packet.wire.item.UseItemWirePacket;
import de.jpx3.intave.packet.wire.payload.PayloadInWirePacket;
import de.jpx3.intave.packet.wire.player.*;
import de.jpx3.intave.packet.wire.recipe.PlaceRecipeWirePacket;
import de.jpx3.intave.packet.wire.scoreboard.*;
import de.jpx3.intave.packet.wire.tabcomplete.TabCompleteRequestWirePacket;
import de.jpx3.intave.packet.wire.tabcomplete.TabCompleteResponseWirePacket;
import de.jpx3.intave.packet.wire.vehicle.VehicleMoveWirePacket;
import de.jpx3.intave.packet.wire.window.*;
import de.jpx3.intave.packet.wire.world.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class WirePacketCatalog {
	private WirePacketCatalog() {
	}

	static List<Class<? extends PacketContract>> packetClasses() {
		List<Class<?>> packets = new ArrayList<>(Arrays.<Class<?>>asList(
			BundleDelimiterWirePacket.class,
			ClientTickEndWirePacket.class,
			AbilityOutWirePacket.class,
			BlockChangedAckWirePacket.class,
			BlockBreakAckWirePacket.class,
			MapChunkBulkWirePacket.class,
			ChunkBatchStartWirePacket.class,
			ChunkBatchFinishedWirePacket.class,
			ChunkBatchReceivedWirePacket.class,
			HandshakeWirePacket.class,
			PingWirePacket.class,
			PongWirePacket.class,
			CookieRequestWirePacket.class,
			CookieResponseWirePacket.class,
			StoreCookieWirePacket.class,
			EntityAnimationWirePacket.class,
			CameraWirePacket.class,
			AttackEntityWirePacket.class,
			MountEntityWirePacket.class,
			EntityStatusWirePacket.class,
			InteractEntityWirePacket.class,
			CarriedItemSlotWirePacket.class,
			HeldItemSlotWirePacket.class,
			PickItemWirePacket.class,
			PayloadInWirePacket.class,
			BedUseWirePacket.class,
			ExperienceWirePacket.class,
			UpdateHealthWirePacket.class,
			PlayerInfoRemoveWirePacket.class,
			TransactionWirePacket.class,
			WorldBorderWirePacket.class,
			WorldBorderInitializeWirePacket.class,
			WorldBorderSetCenterWirePacket.class,
			WorldBorderSetSizeWirePacket.class,
			WorldBorderLerpSizeWirePacket.class,
			WorldBorderWarningDelayWirePacket.class,
			WorldBorderWarningDistanceWirePacket.class,
			GameStateChangeWirePacket.class
		));
		packets.addAll(AbilityInWirePacket.registeredClasses());
		packets.addAll(ArmAnimationWirePacket.registeredClasses());
		packets.addAll(AttachEntityWirePacket.registeredClasses());
		packets.addAll(EntityAttributesWirePacket.registeredClasses());
		packets.addAll(BlockDigWirePacket.registeredClasses());
		packets.addAll(BlockInteractionWirePacket.registeredClasses());
		packets.addAll(ChatWirePacket.registeredClasses());
		packets.addAll(ChunksBiomesWirePacket.registeredClasses());
		packets.addAll(ClientCommandWirePacket.registeredClasses());
		packets.addAll(ClientSettingsWirePacket.registeredClasses());
		packets.addAll(EntityDestroyWirePacket.registeredClasses());
		packets.addAll(EntityEffectWirePacket.registeredClasses());
		packets.addAll(EntityMetadataWirePacket.registeredClasses());
		packets.addAll(EntityPositionSyncWirePacket.registeredClasses());
		packets.addAll(EntityPositionWirePacket.registeredClasses());
		packets.addAll(EntityRelativeMoveLookWirePacket.registeredClasses());
		packets.addAll(EntityRelativeMoveWirePacket.registeredClasses());
		packets.addAll(EntityUseWirePacket.registeredClasses());
		packets.addAll(EntityVelocityWirePacket.registeredClasses());
		packets.addAll(ExplosionWirePacket.registeredClasses());
		packets.addAll(HorseWindowOpenWirePacket.registeredClasses());
		packets.addAll(KeepAliveWirePacket.registeredClasses());
		packets.addAll(LegacyWindowClickWirePacket.registeredClasses());
		packets.addAll(LightUpdateWirePacket.registeredClasses());
		packets.addAll(MapChunkWirePacket.registeredClasses());
		packets.addAll(MenuButtonWirePacket.registeredClasses());
		packets.addAll(MultiBlockChangeWirePacket.registeredClasses());
		packets.addAll(OpenScreenWirePacket.registeredClasses());
		packets.addAll(PlaceRecipeWirePacket.registeredClasses());
		packets.addAll(PlayerActionWirePacket.registeredClasses());
		packets.addAll(PlayerGroundWirePacket.registeredClasses());
		packets.addAll(PlayerInputWirePacket.registeredClasses());
		packets.addAll(PlayerPositionRotationWirePacket.registeredClasses());
		packets.addAll(PlayerPositionWirePacket.registeredClasses());
		packets.addAll(PlayerRotationWirePacket.registeredClasses());
		packets.addAll(PlayerTeleportWirePacket.registeredClasses());
		packets.addAll(RespawnWirePacket.registeredClasses());
		packets.addAll(RemoveEntityEffectWirePacket.registeredClasses());
		packets.addAll(ScoreboardDisplayWirePacket.registeredClasses());
		packets.addAll(ScoreboardObjectiveWirePacket.registeredClasses());
		packets.add(ResetScoreWirePacket.class);
		packets.addAll(ScoreboardScoreWirePacket.registeredClasses());
		packets.addAll(ScoreboardTeamWirePacket.registeredClasses());
		packets.addAll(SingleBlockChangeWirePacket.registeredClasses());
		packets.addAll(SignUpdateWirePacket.registeredClasses());
		packets.addAll(TabCompleteRequestWirePacket.registeredClasses());
		packets.addAll(TabCompleteResponseWirePacket.registeredClasses());
		packets.addAll(TeleportAcceptWirePacket.registeredClasses());
		packets.addAll(TimeUpdateWirePacket.registeredClasses());
		packets.addAll(UnloadChunkWirePacket.registeredClasses());
		packets.addAll(UseItemWirePacket.registeredClasses());
		packets.addAll(VehicleMoveWirePacket.registeredClasses());
		packets.addAll(WindowClickWirePacket.registeredClasses());
		packets.addAll(WindowCloseWirePacket.registeredClasses());
		packets.addAll(WindowItemsWirePacket.registeredClasses());
		packets.addAll(WindowSetSlotWirePacket.registeredClasses());
		IntaveLogger logger = IntaveLogger.logger();
		if (logger != null) {
			logger.info(packets.size() + " wire packet classes registered");
		}
		return packets.stream()
			.<Class<? extends PacketContract>>map(type -> type.asSubclass(PacketContract.class))
			.collect(Collectors.toList());
	}
}
