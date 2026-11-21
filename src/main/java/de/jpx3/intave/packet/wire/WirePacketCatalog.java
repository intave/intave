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

import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.wire.ability.inbound.AbilityInWirePacket;
import de.jpx3.intave.packet.wire.ability.inbound.AbilityInWithSpeedsWirePacket;
import de.jpx3.intave.packet.wire.ability.outbound.AbilityOutWirePacket;
import de.jpx3.intave.packet.wire.block.acknowledge.BlockChangedAckWirePacket;
import de.jpx3.intave.packet.wire.block.change.SingleBlockChangeLegacyPositionWirePacket;
import de.jpx3.intave.packet.wire.block.change.SingleBlockChangeWirePacket;
import de.jpx3.intave.packet.wire.block.dig.BlockDigLegacyPositionWirePacket;
import de.jpx3.intave.packet.wire.block.dig.BlockDigWirePacket;
import de.jpx3.intave.packet.wire.block.dig.BlockDigWithSequenceWirePacket;
import de.jpx3.intave.packet.wire.block.dig.BlockDigWithoutSequenceWirePacket;
import de.jpx3.intave.packet.wire.block.interaction.*;
import de.jpx3.intave.packet.wire.block.multichange.MultiBlockChangeChunkWirePacket;
import de.jpx3.intave.packet.wire.block.multichange.MultiBlockChangeWirePacket;
import de.jpx3.intave.packet.wire.block.multichange.MultiBlockChangeWithLightSuppressionWirePacket;
import de.jpx3.intave.packet.wire.chat.message.*;
import de.jpx3.intave.packet.wire.chunk.batch.ChunkBatchFinishedWirePacket;
import de.jpx3.intave.packet.wire.chunk.batch.ChunkBatchReceivedWirePacket;
import de.jpx3.intave.packet.wire.chunk.batch.ChunkBatchStartWirePacket;
import de.jpx3.intave.packet.wire.chunk.biomes.ChunksBiomesWirePacket;
import de.jpx3.intave.packet.wire.chunk.biomes.ChunksBiomesWithStorageLengthWirePacket;
import de.jpx3.intave.packet.wire.chunk.bulk.MapChunkBulkWirePacket;
import de.jpx3.intave.packet.wire.chunk.data.*;
import de.jpx3.intave.packet.wire.chunk.light.LightUpdateLegacyMasksWirePacket;
import de.jpx3.intave.packet.wire.chunk.light.LightUpdateLegacyMasksWithTrustEdgesWirePacket;
import de.jpx3.intave.packet.wire.chunk.light.LightUpdateWirePacket;
import de.jpx3.intave.packet.wire.chunk.light.LightUpdateWithTrustEdgesWirePacket;
import de.jpx3.intave.packet.wire.chunk.unload.UnloadChunkCoordinatePairWirePacket;
import de.jpx3.intave.packet.wire.chunk.unload.UnloadChunkWirePacket;
import de.jpx3.intave.packet.wire.client.command.ClientCommandWirePacket;
import de.jpx3.intave.packet.wire.client.settings.*;
import de.jpx3.intave.packet.wire.connection.bundle.BundleDelimiterWirePacket;
import de.jpx3.intave.packet.wire.connection.handshake.HandshakeWirePacket;
import de.jpx3.intave.packet.wire.connection.keepalive.KeepAliveVarIntIdWirePacket;
import de.jpx3.intave.packet.wire.connection.keepalive.KeepAliveWirePacket;
import de.jpx3.intave.packet.wire.connection.ping.PingWirePacket;
import de.jpx3.intave.packet.wire.connection.pong.PongWirePacket;
import de.jpx3.intave.packet.wire.cookie.request.CookieRequestWirePacket;
import de.jpx3.intave.packet.wire.cookie.response.CookieResponseWirePacket;
import de.jpx3.intave.packet.wire.cookie.store.StoreCookieWirePacket;
import de.jpx3.intave.packet.wire.entity.animation.EntityAnimationWirePacket;
import de.jpx3.intave.packet.wire.entity.attach.AttachEntityMountOrLeashWirePacket;
import de.jpx3.intave.packet.wire.entity.attach.AttachEntityWirePacket;
import de.jpx3.intave.packet.wire.entity.attack.AttackEntityWirePacket;
import de.jpx3.intave.packet.wire.entity.destroy.EntityDestroyLegacyArrayWirePacket;
import de.jpx3.intave.packet.wire.entity.destroy.EntityDestroySingleEntityWirePacket;
import de.jpx3.intave.packet.wire.entity.destroy.EntityDestroyWirePacket;
import de.jpx3.intave.packet.wire.entity.mount.MountEntityWirePacket;
import de.jpx3.intave.packet.wire.entity.position.*;
import de.jpx3.intave.packet.wire.entity.relativemove.EntityRelativeMoveByteDeltasWirePacket;
import de.jpx3.intave.packet.wire.entity.relativemove.EntityRelativeMoveShortDeltasWirePacket;
import de.jpx3.intave.packet.wire.entity.relativemove.EntityRelativeMoveWirePacket;
import de.jpx3.intave.packet.wire.entity.relativemovelook.EntityRelativeMoveLookByteDeltasWirePacket;
import de.jpx3.intave.packet.wire.entity.relativemovelook.EntityRelativeMoveLookShortDeltasWirePacket;
import de.jpx3.intave.packet.wire.entity.relativemovelook.EntityRelativeMoveLookWirePacket;
import de.jpx3.intave.packet.wire.entity.status.EntityStatusWirePacket;
import de.jpx3.intave.packet.wire.entity.use.EntityUseWirePacket;
import de.jpx3.intave.packet.wire.entity.use.EntityUseWithHandWirePacket;
import de.jpx3.intave.packet.wire.entity.use.EntityUseWithoutHandWirePacket;
import de.jpx3.intave.packet.wire.entity.use.InteractEntityWirePacket;
import de.jpx3.intave.packet.wire.entity.velocity.EntityVelocityShortVectorWirePacket;
import de.jpx3.intave.packet.wire.entity.velocity.EntityVelocityWirePacket;
import de.jpx3.intave.packet.wire.item.carriedslot.CarriedItemSlotWirePacket;
import de.jpx3.intave.packet.wire.item.heldslot.HeldItemSlotWirePacket;
import de.jpx3.intave.packet.wire.item.pick.PickItemWirePacket;
import de.jpx3.intave.packet.wire.item.use.UseItemProtocol107WirePacket;
import de.jpx3.intave.packet.wire.item.use.UseItemProtocol759WirePacket;
import de.jpx3.intave.packet.wire.item.use.UseItemWirePacket;
import de.jpx3.intave.packet.wire.payload.inbound.PayloadInWirePacket;
import de.jpx3.intave.packet.wire.player.action.PlayerActionBeforeOffhandWirePacket;
import de.jpx3.intave.packet.wire.player.action.PlayerActionWirePacket;
import de.jpx3.intave.packet.wire.player.action.PlayerActionWithSneakingWirePacket;
import de.jpx3.intave.packet.wire.player.animation.ArmAnimationWirePacket;
import de.jpx3.intave.packet.wire.player.animation.ArmAnimationWithoutHandWirePacket;
import de.jpx3.intave.packet.wire.player.bed.BedUseWirePacket;
import de.jpx3.intave.packet.wire.player.experience.ExperienceWirePacket;
import de.jpx3.intave.packet.wire.player.ground.PlayerGroundOnGroundOnlyWirePacket;
import de.jpx3.intave.packet.wire.player.ground.PlayerGroundWirePacket;
import de.jpx3.intave.packet.wire.player.health.UpdateHealthWirePacket;
import de.jpx3.intave.packet.wire.player.inforemove.PlayerInfoRemoveWirePacket;
import de.jpx3.intave.packet.wire.player.input.LegacyPlayerInputWirePacket;
import de.jpx3.intave.packet.wire.player.input.PlayerInputWirePacket;
import de.jpx3.intave.packet.wire.player.position.PlayerPositionOnGroundOnlyWirePacket;
import de.jpx3.intave.packet.wire.player.position.PlayerPositionWirePacket;
import de.jpx3.intave.packet.wire.player.positionrotation.PlayerPositionRotationOnGroundOnlyWirePacket;
import de.jpx3.intave.packet.wire.player.positionrotation.PlayerPositionRotationWirePacket;
import de.jpx3.intave.packet.wire.player.rotation.PlayerRotationOnGroundOnlyWirePacket;
import de.jpx3.intave.packet.wire.player.rotation.PlayerRotationWirePacket;
import de.jpx3.intave.packet.wire.player.teleport.*;
import de.jpx3.intave.packet.wire.player.teleportaccept.TeleportAcceptIdOnlyWirePacket;
import de.jpx3.intave.packet.wire.player.teleportaccept.TeleportAcceptWirePacket;
import de.jpx3.intave.packet.wire.recipe.place.PlaceRecipeKeyRecipeWirePacket;
import de.jpx3.intave.packet.wire.recipe.place.PlaceRecipeNumericRecipeWirePacket;
import de.jpx3.intave.packet.wire.recipe.place.PlaceRecipeWirePacket;
import de.jpx3.intave.packet.wire.scoreboard.display.ScoreboardDisplayByteSlotWirePacket;
import de.jpx3.intave.packet.wire.scoreboard.display.ScoreboardDisplayWirePacket;
import de.jpx3.intave.packet.wire.scoreboard.objective.ScoreboardObjectivePlainTextWirePacket;
import de.jpx3.intave.packet.wire.scoreboard.objective.ScoreboardObjectiveWirePacket;
import de.jpx3.intave.packet.wire.tabcomplete.request.TabCompleteRequestWirePacket;
import de.jpx3.intave.packet.wire.tabcomplete.request.TabCompleteRequestWithCommandFlagWirePacket;
import de.jpx3.intave.packet.wire.tabcomplete.request.TabCompleteRequestWithTargetWirePacket;
import de.jpx3.intave.packet.wire.tabcomplete.response.TabCompleteResponseStringsOnlyWirePacket;
import de.jpx3.intave.packet.wire.tabcomplete.response.TabCompleteResponseWirePacket;
import de.jpx3.intave.packet.wire.vehicle.move.VehicleMoveWirePacket;
import de.jpx3.intave.packet.wire.vehicle.move.VehicleMoveWithoutGroundWirePacket;
import de.jpx3.intave.packet.wire.window.button.MenuButtonByteFieldsWirePacket;
import de.jpx3.intave.packet.wire.window.button.MenuButtonWirePacket;
import de.jpx3.intave.packet.wire.window.close.WindowCloseByteIdWirePacket;
import de.jpx3.intave.packet.wire.window.close.WindowCloseWirePacket;
import de.jpx3.intave.packet.wire.window.horseopen.HorseWindowOpenProtocol477WirePacket;
import de.jpx3.intave.packet.wire.window.horseopen.HorseWindowOpenProtocol767WirePacket;
import de.jpx3.intave.packet.wire.window.horseopen.HorseWindowOpenProtocol768WirePacket;
import de.jpx3.intave.packet.wire.window.horseopen.HorseWindowOpenWirePacket;
import de.jpx3.intave.packet.wire.window.open.JsonOpenScreenWirePacket;
import de.jpx3.intave.packet.wire.window.open.LegacyOpenScreenProtocol393WirePacket;
import de.jpx3.intave.packet.wire.window.open.LegacyOpenScreenWirePacket;
import de.jpx3.intave.packet.wire.window.open.NbtOpenScreenWirePacket;
import de.jpx3.intave.packet.wire.window.transaction.TransactionWirePacket;
import de.jpx3.intave.packet.wire.world.border.*;
import de.jpx3.intave.packet.wire.world.explosion.*;
import de.jpx3.intave.packet.wire.world.gamestate.GameStateChangeWirePacket;
import de.jpx3.intave.packet.wire.world.respawn.RespawnWirePacket;
import de.jpx3.intave.packet.wire.world.time.TimeUpdateSignedDayTimeWirePacket;
import de.jpx3.intave.packet.wire.world.time.TimeUpdateWirePacket;

import java.util.Arrays;
import java.util.List;

final class WirePacketCatalog {
	private WirePacketCatalog() {
	}

	static List<Class<? extends PacketContract>> packetClasses() {
		return Arrays.asList(
			BundleDelimiterWirePacket.class,
			AbilityInWirePacket.class,
			AbilityInWithSpeedsWirePacket.class,

			AbilityOutWirePacket.class,

			BlockChangedAckWirePacket.class,

			SingleBlockChangeWirePacket.class,
			SingleBlockChangeLegacyPositionWirePacket.class,

			MultiBlockChangeWirePacket.class,
			MultiBlockChangeWithLightSuppressionWirePacket.class,
			MultiBlockChangeChunkWirePacket.class,

			BlockDigWirePacket.class,
			BlockDigLegacyPositionWirePacket.class,
			BlockDigWithoutSequenceWirePacket.class,
			BlockDigWithSequenceWirePacket.class,

			BlockInteractionWirePacket.class,
			BlockInteractionProtocol107WirePacket.class,
			BlockInteractionProtocol315WirePacket.class,
			BlockInteractionProtocol477WirePacket.class,
			BlockInteractionProtocol759WirePacket.class,

			ChatWirePacket.class,
			ChatShortMessageWirePacket.class,
			ChatUnsignedWirePacket.class,
			ChatWithPreviewWirePacket.class,
			ChatWithLastSeenWirePacket.class,
			ChatWithoutChecksumWirePacket.class,

			MapChunkBulkWirePacket.class,

			MapChunkWirePacket.class,
			MapChunkFlattenedWirePacket.class,
			MapChunkWithFluidCountWirePacket.class,
			MapChunkLegacyMaskWirePacket.class,
			MapChunkVarIntMaskWirePacket.class,
			MapChunkWithBlockEntitiesWirePacket.class,
			MapChunkWithHeightmapsWirePacket.class,
			MapChunkWithBiomesWirePacket.class,
			MapChunkWithIgnoreOldDataWirePacket.class,
			MapChunkVarIntBiomesWirePacket.class,
			MapChunkBitSetMaskWirePacket.class,
			MapChunkWithLightWirePacket.class,
			MapChunkWithoutTrustEdgesWirePacket.class,
			MapChunkUnnamedNbtWirePacket.class,

			ChunkBatchStartWirePacket.class,
			ChunkBatchFinishedWirePacket.class,
			ChunkBatchReceivedWirePacket.class,
			ChunksBiomesWirePacket.class,
			ChunksBiomesWithStorageLengthWirePacket.class,
			LightUpdateWirePacket.class,
			LightUpdateWithTrustEdgesWirePacket.class,
			LightUpdateLegacyMasksWithTrustEdgesWirePacket.class,
			LightUpdateLegacyMasksWirePacket.class,

			UnloadChunkWirePacket.class,
			UnloadChunkCoordinatePairWirePacket.class,

			ClientCommandWirePacket.class,
			ClientCommandWirePacket.Basic.class,
			ClientCommandWirePacket.WithGameRuleRequest.class,

			ClientSettingsWirePacket.class,
			ClientSettingsWithoutHandWirePacket.class,
			ClientSettingsWithHandWirePacket.class,
			ClientSettingsWithFilteringWirePacket.class,
			ClientSettingsWithListingWirePacket.class,

			HandshakeWirePacket.class,

			KeepAliveWirePacket.class,
			KeepAliveVarIntIdWirePacket.class,

			PingWirePacket.class,

			PongWirePacket.class,

			CookieRequestWirePacket.class,

			CookieResponseWirePacket.class,

			StoreCookieWirePacket.class,

			EntityAnimationWirePacket.class,

			AttachEntityWirePacket.class,
			AttachEntityMountOrLeashWirePacket.class,

			AttackEntityWirePacket.class,

			EntityDestroyWirePacket.class,
			EntityDestroyLegacyArrayWirePacket.class,
			EntityDestroySingleEntityWirePacket.class,

			MountEntityWirePacket.class,

			EntityPositionWirePacket.class,
			EntityPositionFixedPointWirePacket.class,
			EntityPositionDoublePositionWirePacket.class,
			EntityPositionSyncWirePacket.class,
			EntityPositionSyncPositionMoveRotationWirePacket.class,

			EntityRelativeMoveWirePacket.class,
			EntityRelativeMoveByteDeltasWirePacket.class,
			EntityRelativeMoveShortDeltasWirePacket.class,

			EntityRelativeMoveLookWirePacket.class,
			EntityRelativeMoveLookByteDeltasWirePacket.class,
			EntityRelativeMoveLookShortDeltasWirePacket.class,

			EntityStatusWirePacket.class,

			EntityUseWirePacket.class,
			EntityUseWithoutHandWirePacket.class,
			EntityUseWithHandWirePacket.class,
			InteractEntityWirePacket.class,

			EntityVelocityWirePacket.class,
			EntityVelocityShortVectorWirePacket.class,

			CarriedItemSlotWirePacket.class,

			HeldItemSlotWirePacket.class,

			PickItemWirePacket.class,

			UseItemWirePacket.class,
			UseItemProtocol107WirePacket.class,
			UseItemProtocol759WirePacket.class,

			PayloadInWirePacket.class,

			PlayerActionWirePacket.class,
			PlayerActionWithSneakingWirePacket.class,
			PlayerActionBeforeOffhandWirePacket.class,

			ArmAnimationWirePacket.class,
			ArmAnimationWithoutHandWirePacket.class,

			BedUseWirePacket.class,

			ExperienceWirePacket.class,

			PlayerGroundWirePacket.class,
			PlayerGroundOnGroundOnlyWirePacket.class,

			UpdateHealthWirePacket.class,

			PlayerInfoRemoveWirePacket.class,

			PlayerInputWirePacket.class,
			LegacyPlayerInputWirePacket.class,

			PlayerPositionWirePacket.class,
			PlayerPositionOnGroundOnlyWirePacket.class,

			PlayerPositionRotationWirePacket.class,
			PlayerPositionRotationOnGroundOnlyWirePacket.class,

			PlayerRotationWirePacket.class,
			PlayerRotationOnGroundOnlyWirePacket.class,

			PlayerTeleportWirePacket.class,
			PlayerTeleportWithoutIdWirePacket.class,
			PlayerTeleportWithIdWirePacket.class,
			PlayerTeleportWithDismountWirePacket.class,
			PlayerTeleportWithoutDismountWirePacket.class,

			TeleportAcceptWirePacket.class,
			TeleportAcceptIdOnlyWirePacket.class,

			PlaceRecipeWirePacket.class,
			PlaceRecipeNumericRecipeWirePacket.class,
			PlaceRecipeKeyRecipeWirePacket.class,

			ScoreboardDisplayWirePacket.class,
			ScoreboardDisplayByteSlotWirePacket.class,

			ScoreboardObjectiveWirePacket.class,
			ScoreboardObjectivePlainTextWirePacket.class,

			TabCompleteRequestWirePacket.class,
			TabCompleteRequestWithTargetWirePacket.class,
			TabCompleteRequestWithCommandFlagWirePacket.class,

			TabCompleteResponseWirePacket.class,
			TabCompleteResponseStringsOnlyWirePacket.class,

			VehicleMoveWirePacket.class,
			VehicleMoveWithoutGroundWirePacket.class,

			MenuButtonWirePacket.class,
			MenuButtonByteFieldsWirePacket.class,

			WindowCloseWirePacket.class,
			WindowCloseByteIdWirePacket.class,

			LegacyOpenScreenWirePacket.class,
			LegacyOpenScreenProtocol393WirePacket.class,
			JsonOpenScreenWirePacket.class,
			NbtOpenScreenWirePacket.class,

			HorseWindowOpenWirePacket.class,
			HorseWindowOpenProtocol477WirePacket.class,
			HorseWindowOpenProtocol767WirePacket.class,
			HorseWindowOpenProtocol768WirePacket.class,

			TransactionWirePacket.class,

			WorldBorderWirePacket.class,
			WorldBorderInitializeWirePacket.class,
			WorldBorderSetCenterWirePacket.class,
			WorldBorderSetSizeWirePacket.class,
			WorldBorderLerpSizeWirePacket.class,
			WorldBorderWarningDelayWirePacket.class,
			WorldBorderWarningDistanceWirePacket.class,

			ExplosionIntBlockCountWirePacket.class,
			ExplosionVarIntBlockCountWirePacket.class,
			ExplosionDoublePositionWirePacket.class,
			ExplosionWithEffectsWirePacket.class,
			ExplosionOptionalMotionWirePacket.class,
			ExplosionWithRadiusWirePacket.class,

			GameStateChangeWirePacket.class,

			RespawnWirePacket.Legacy.class,
			RespawnWirePacket.LevelType.class,
			RespawnWirePacket.HashedSeed.class,
			RespawnWirePacket.NamedDimension.class,
			RespawnWirePacket.DimensionData.class,
			RespawnWirePacket.DeathLocation.class,
			RespawnWirePacket.KeepMask.class,
			RespawnWirePacket.PortalCooldown.class,
			RespawnWirePacket.CommonSpawnInfo.class,
			RespawnWirePacket.RegistryDimension.class,
			RespawnWirePacket.SeaLevel.class,
			RespawnWirePacket.VarIntGameModes.class,

			TimeUpdateWirePacket.class,
			TimeUpdateSignedDayTimeWirePacket.class
		);
	}
}
