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
import de.jpx3.intave.packet.wire.ability.inbound.AbilityInWirePacket;
import de.jpx3.intave.packet.wire.ability.outbound.AbilityOutWirePacket;
import de.jpx3.intave.packet.wire.block.acknowledge.BlockBreakAckWirePacket;
import de.jpx3.intave.packet.wire.block.acknowledge.BlockChangedAckWirePacket;
import de.jpx3.intave.packet.wire.block.change.SingleBlockChangeWirePacket;
import de.jpx3.intave.packet.wire.block.dig.BlockDigWirePacket;
import de.jpx3.intave.packet.wire.block.interaction.BlockInteractionWirePacket;
import de.jpx3.intave.packet.wire.block.multichange.MultiBlockChangeWirePacket;
import de.jpx3.intave.packet.wire.block.sign.SignUpdateWirePacket;
import de.jpx3.intave.packet.wire.chat.message.ChatWirePacket;
import de.jpx3.intave.packet.wire.chunk.batch.ChunkBatchFinishedWirePacket;
import de.jpx3.intave.packet.wire.chunk.batch.ChunkBatchReceivedWirePacket;
import de.jpx3.intave.packet.wire.chunk.batch.ChunkBatchStartWirePacket;
import de.jpx3.intave.packet.wire.chunk.biomes.ChunksBiomesWirePacket;
import de.jpx3.intave.packet.wire.chunk.bulk.MapChunkBulkWirePacket;
import de.jpx3.intave.packet.wire.chunk.data.MapChunkWirePacket;
import de.jpx3.intave.packet.wire.chunk.light.LightUpdateWirePacket;
import de.jpx3.intave.packet.wire.chunk.unload.UnloadChunkWirePacket;
import de.jpx3.intave.packet.wire.client.command.ClientCommandWirePacket;
import de.jpx3.intave.packet.wire.client.settings.ClientSettingsWirePacket;
import de.jpx3.intave.packet.wire.client.tickend.ClientTickEndWirePacket;
import de.jpx3.intave.packet.wire.connection.bundle.BundleDelimiterWirePacket;
import de.jpx3.intave.packet.wire.connection.handshake.HandshakeWirePacket;
import de.jpx3.intave.packet.wire.connection.keepalive.KeepAliveWirePacket;
import de.jpx3.intave.packet.wire.connection.ping.PingWirePacket;
import de.jpx3.intave.packet.wire.connection.pong.PongWirePacket;
import de.jpx3.intave.packet.wire.cookie.request.CookieRequestWirePacket;
import de.jpx3.intave.packet.wire.cookie.response.CookieResponseWirePacket;
import de.jpx3.intave.packet.wire.cookie.store.StoreCookieWirePacket;
import de.jpx3.intave.packet.wire.entity.animation.EntityAnimationWirePacket;
import de.jpx3.intave.packet.wire.entity.attach.AttachEntityWirePacket;
import de.jpx3.intave.packet.wire.entity.attack.AttackEntityWirePacket;
import de.jpx3.intave.packet.wire.entity.attributes.EntityAttributesWirePacket;
import de.jpx3.intave.packet.wire.entity.camera.CameraWirePacket;
import de.jpx3.intave.packet.wire.entity.destroy.EntityDestroyWirePacket;
import de.jpx3.intave.packet.wire.entity.effect.EntityEffectWirePacket;
import de.jpx3.intave.packet.wire.entity.effect.RemoveEntityEffectWirePacket;
import de.jpx3.intave.packet.wire.entity.metadata.EntityMetadataWirePacket;
import de.jpx3.intave.packet.wire.entity.mount.MountEntityWirePacket;
import de.jpx3.intave.packet.wire.entity.position.EntityPositionSyncWirePacket;
import de.jpx3.intave.packet.wire.entity.position.EntityPositionWirePacket;
import de.jpx3.intave.packet.wire.entity.relativemove.EntityRelativeMoveWirePacket;
import de.jpx3.intave.packet.wire.entity.relativemovelook.EntityRelativeMoveLookWirePacket;
import de.jpx3.intave.packet.wire.entity.status.EntityStatusWirePacket;
import de.jpx3.intave.packet.wire.entity.use.EntityUseWirePacket;
import de.jpx3.intave.packet.wire.entity.use.InteractEntityWirePacket;
import de.jpx3.intave.packet.wire.entity.velocity.EntityVelocityWirePacket;
import de.jpx3.intave.packet.wire.item.carriedslot.CarriedItemSlotWirePacket;
import de.jpx3.intave.packet.wire.item.heldslot.HeldItemSlotWirePacket;
import de.jpx3.intave.packet.wire.item.pick.PickItemWirePacket;
import de.jpx3.intave.packet.wire.item.use.UseItemWirePacket;
import de.jpx3.intave.packet.wire.payload.inbound.PayloadInWirePacket;
import de.jpx3.intave.packet.wire.player.action.PlayerActionWirePacket;
import de.jpx3.intave.packet.wire.player.animation.ArmAnimationWirePacket;
import de.jpx3.intave.packet.wire.player.bed.BedUseWirePacket;
import de.jpx3.intave.packet.wire.player.experience.ExperienceWirePacket;
import de.jpx3.intave.packet.wire.player.ground.PlayerGroundWirePacket;
import de.jpx3.intave.packet.wire.player.health.UpdateHealthWirePacket;
import de.jpx3.intave.packet.wire.player.inforemove.PlayerInfoRemoveWirePacket;
import de.jpx3.intave.packet.wire.player.input.PlayerInputWirePacket;
import de.jpx3.intave.packet.wire.player.position.PlayerPositionWirePacket;
import de.jpx3.intave.packet.wire.player.positionrotation.PlayerPositionRotationWirePacket;
import de.jpx3.intave.packet.wire.player.rotation.PlayerRotationWirePacket;
import de.jpx3.intave.packet.wire.player.teleport.PlayerTeleportWirePacket;
import de.jpx3.intave.packet.wire.player.teleportaccept.TeleportAcceptWirePacket;
import de.jpx3.intave.packet.wire.recipe.place.PlaceRecipeWirePacket;
import de.jpx3.intave.packet.wire.scoreboard.display.ScoreboardDisplayWirePacket;
import de.jpx3.intave.packet.wire.scoreboard.objective.ScoreboardObjectiveWirePacket;
import de.jpx3.intave.packet.wire.scoreboard.score.ResetScoreWirePacket;
import de.jpx3.intave.packet.wire.scoreboard.score.ScoreboardScoreWirePacket;
import de.jpx3.intave.packet.wire.scoreboard.team.ScoreboardTeamWirePacket;
import de.jpx3.intave.packet.wire.tabcomplete.request.TabCompleteRequestWirePacket;
import de.jpx3.intave.packet.wire.tabcomplete.response.TabCompleteResponseWirePacket;
import de.jpx3.intave.packet.wire.vehicle.move.VehicleMoveWirePacket;
import de.jpx3.intave.packet.wire.window.button.MenuButtonWirePacket;
import de.jpx3.intave.packet.wire.window.click.LegacyWindowClickWirePacket;
import de.jpx3.intave.packet.wire.window.click.WindowClickWirePacket;
import de.jpx3.intave.packet.wire.window.close.WindowCloseWirePacket;
import de.jpx3.intave.packet.wire.window.horseopen.HorseWindowOpenWirePacket;
import de.jpx3.intave.packet.wire.window.items.WindowItemsWirePacket;
import de.jpx3.intave.packet.wire.window.open.OpenScreenWirePacket;
import de.jpx3.intave.packet.wire.window.setslot.WindowSetSlotWirePacket;
import de.jpx3.intave.packet.wire.window.transaction.TransactionWirePacket;
import de.jpx3.intave.packet.wire.world.border.*;
import de.jpx3.intave.packet.wire.world.explosion.ExplosionWirePacket;
import de.jpx3.intave.packet.wire.world.gamestate.GameStateChangeWirePacket;
import de.jpx3.intave.packet.wire.world.respawn.RespawnWirePacket;
import de.jpx3.intave.packet.wire.world.time.TimeUpdateWirePacket;

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
