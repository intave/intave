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

import de.jpx3.intave.packet.wire.ability.inbound.AbilityInWirePacket;
import de.jpx3.intave.packet.wire.ability.inbound.AbilityInWithSpeedsWirePacket;
import de.jpx3.intave.packet.wire.ability.outbound.AbilityOutWirePacket;
import de.jpx3.intave.packet.wire.block.acknowledge.BlockChangedAckWirePacket;
import de.jpx3.intave.packet.wire.block.dig.BlockDigLegacyPositionWirePacket;
import de.jpx3.intave.packet.wire.block.dig.BlockDigWirePacket;
import de.jpx3.intave.packet.wire.block.dig.BlockDigWithSequenceWirePacket;
import de.jpx3.intave.packet.wire.block.dig.BlockDigWithoutSequenceWirePacket;
import de.jpx3.intave.packet.wire.block.interaction.*;
import de.jpx3.intave.packet.wire.chat.message.*;
import de.jpx3.intave.packet.wire.chunk.bulk.MapChunkBulkWirePacket;
import de.jpx3.intave.packet.wire.chunk.data.MapChunkWirePacket;
import de.jpx3.intave.packet.wire.chunk.unload.UnloadChunkCoordinatePairWirePacket;
import de.jpx3.intave.packet.wire.chunk.unload.UnloadChunkWirePacket;
import de.jpx3.intave.packet.wire.client.command.ClientCommandWirePacket;
import de.jpx3.intave.packet.wire.client.settings.*;
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
import de.jpx3.intave.packet.wire.window.transaction.TransactionWirePacket;
import de.jpx3.intave.packet.wire.world.border.*;
import de.jpx3.intave.packet.wire.world.explosion.ExplosionDoublePositionWirePacket;
import de.jpx3.intave.packet.wire.world.explosion.ExplosionIntBlockCountWirePacket;
import de.jpx3.intave.packet.wire.world.explosion.ExplosionVarIntBlockCountWirePacket;
import de.jpx3.intave.packet.wire.world.gamestate.GameStateChangeWirePacket;
import de.jpx3.intave.packet.wire.world.time.TimeUpdateSignedDayTimeWirePacket;
import de.jpx3.intave.packet.wire.world.time.TimeUpdateWirePacket;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;

final class WirePacketCatalog {
	private WirePacketCatalog() {}

	static void register() {
		WirePackets.register(SERVERBOUND, AbilityInWirePacket.class);
		WirePackets.register(SERVERBOUND, AbilityInWithSpeedsWirePacket.class);

		WirePackets.register(CLIENTBOUND, AbilityOutWirePacket.class);

		WirePackets.register(CLIENTBOUND, BlockChangedAckWirePacket.class);

		WirePackets.register(SERVERBOUND, BlockDigWirePacket.class);
		WirePackets.register(SERVERBOUND, BlockDigLegacyPositionWirePacket.class);
		WirePackets.register(SERVERBOUND, BlockDigWithoutSequenceWirePacket.class);
		WirePackets.register(SERVERBOUND, BlockDigWithSequenceWirePacket.class);

		WirePackets.register(SERVERBOUND, BlockInteractionWirePacket.class);
		WirePackets.register(SERVERBOUND, BlockInteractionProtocol107WirePacket.class);
		WirePackets.register(SERVERBOUND, BlockInteractionProtocol315WirePacket.class);
		WirePackets.register(SERVERBOUND, BlockInteractionProtocol477WirePacket.class);
		WirePackets.register(SERVERBOUND, BlockInteractionProtocol759WirePacket.class);

		WirePackets.register(SERVERBOUND, ChatWirePacket.class);
		WirePackets.register(SERVERBOUND, ChatShortMessageWirePacket.class);
		WirePackets.register(SERVERBOUND, ChatUnsignedWirePacket.class);
		WirePackets.register(SERVERBOUND, ChatWithPreviewWirePacket.class);
		WirePackets.register(SERVERBOUND, ChatWithLastSeenWirePacket.class);
		WirePackets.register(SERVERBOUND, ChatWithoutChecksumWirePacket.class);

		WirePackets.register(CLIENTBOUND, MapChunkBulkWirePacket.class);

		WirePackets.register(CLIENTBOUND, MapChunkWirePacket.class);

		WirePackets.register(CLIENTBOUND, UnloadChunkWirePacket.class);
		WirePackets.register(CLIENTBOUND, UnloadChunkCoordinatePairWirePacket.class);

		WirePackets.register(SERVERBOUND, ClientCommandWirePacket.class);

		WirePackets.register(SERVERBOUND, ClientSettingsWirePacket.class);
		WirePackets.register(SERVERBOUND, ClientSettingsWithoutHandWirePacket.class);
		WirePackets.register(SERVERBOUND, ClientSettingsWithHandWirePacket.class);
		WirePackets.register(SERVERBOUND, ClientSettingsWithFilteringWirePacket.class);
		WirePackets.register(SERVERBOUND, ClientSettingsWithListingWirePacket.class);

		WirePackets.register(SERVERBOUND, HandshakeWirePacket.class);

		WirePackets.register(SERVERBOUND, KeepAliveWirePacket.class);
		WirePackets.register(CLIENTBOUND, KeepAliveWirePacket.class);
		WirePackets.register(SERVERBOUND, KeepAliveVarIntIdWirePacket.class);
		WirePackets.register(CLIENTBOUND, KeepAliveVarIntIdWirePacket.class);

		WirePackets.register(CLIENTBOUND, PingWirePacket.class);

		WirePackets.register(SERVERBOUND, PongWirePacket.class);

		WirePackets.register(CLIENTBOUND, CookieRequestWirePacket.class);

		WirePackets.register(SERVERBOUND, CookieResponseWirePacket.class);

		WirePackets.register(CLIENTBOUND, StoreCookieWirePacket.class);

		WirePackets.register(CLIENTBOUND, EntityAnimationWirePacket.class);

		WirePackets.register(CLIENTBOUND, AttachEntityWirePacket.class);
		WirePackets.register(CLIENTBOUND, AttachEntityMountOrLeashWirePacket.class);

		WirePackets.register(SERVERBOUND, AttackEntityWirePacket.class);

		WirePackets.register(CLIENTBOUND, EntityDestroyWirePacket.class);
		WirePackets.register(CLIENTBOUND, EntityDestroyLegacyArrayWirePacket.class);
		WirePackets.register(CLIENTBOUND, EntityDestroySingleEntityWirePacket.class);

		WirePackets.register(CLIENTBOUND, MountEntityWirePacket.class);

		WirePackets.register(CLIENTBOUND, EntityPositionWirePacket.class);
		WirePackets.register(CLIENTBOUND, EntityPositionFixedPointWirePacket.class);
		WirePackets.register(CLIENTBOUND, EntityPositionDoublePositionWirePacket.class);
		WirePackets.register(CLIENTBOUND, EntityPositionSyncWirePacket.class);
		WirePackets.register(CLIENTBOUND, EntityPositionSyncPositionMoveRotationWirePacket.class);

		WirePackets.register(CLIENTBOUND, EntityRelativeMoveWirePacket.class);
		WirePackets.register(CLIENTBOUND, EntityRelativeMoveByteDeltasWirePacket.class);
		WirePackets.register(CLIENTBOUND, EntityRelativeMoveShortDeltasWirePacket.class);

		WirePackets.register(CLIENTBOUND, EntityRelativeMoveLookWirePacket.class);
		WirePackets.register(CLIENTBOUND, EntityRelativeMoveLookByteDeltasWirePacket.class);
		WirePackets.register(CLIENTBOUND, EntityRelativeMoveLookShortDeltasWirePacket.class);

		WirePackets.register(CLIENTBOUND, EntityStatusWirePacket.class);

		WirePackets.register(SERVERBOUND, EntityUseWirePacket.class);
		WirePackets.register(SERVERBOUND, EntityUseWithoutHandWirePacket.class);
		WirePackets.register(SERVERBOUND, EntityUseWithHandWirePacket.class);
		WirePackets.register(SERVERBOUND, InteractEntityWirePacket.class);

		WirePackets.register(CLIENTBOUND, EntityVelocityWirePacket.class);
		WirePackets.register(CLIENTBOUND, EntityVelocityShortVectorWirePacket.class);

		WirePackets.register(CLIENTBOUND, CarriedItemSlotWirePacket.class);

		WirePackets.register(SERVERBOUND, HeldItemSlotWirePacket.class);

		WirePackets.register(SERVERBOUND, PickItemWirePacket.class);

		WirePackets.register(SERVERBOUND, UseItemWirePacket.class);
		WirePackets.register(SERVERBOUND, UseItemProtocol107WirePacket.class);
		WirePackets.register(SERVERBOUND, UseItemProtocol759WirePacket.class);

		WirePackets.register(SERVERBOUND, PayloadInWirePacket.class);

		WirePackets.register(SERVERBOUND, PlayerActionWirePacket.class);
		WirePackets.register(SERVERBOUND, PlayerActionWithSneakingWirePacket.class);
		WirePackets.register(SERVERBOUND, PlayerActionBeforeOffhandWirePacket.class);

		WirePackets.register(SERVERBOUND, ArmAnimationWirePacket.class);
		WirePackets.register(SERVERBOUND, ArmAnimationWithoutHandWirePacket.class);

		WirePackets.register(CLIENTBOUND, BedUseWirePacket.class);

		WirePackets.register(CLIENTBOUND, ExperienceWirePacket.class);

		WirePackets.register(SERVERBOUND, PlayerGroundWirePacket.class);
		WirePackets.register(SERVERBOUND, PlayerGroundOnGroundOnlyWirePacket.class);

		WirePackets.register(CLIENTBOUND, UpdateHealthWirePacket.class);

		WirePackets.register(CLIENTBOUND, PlayerInfoRemoveWirePacket.class);

		WirePackets.register(SERVERBOUND, PlayerInputWirePacket.class);
		WirePackets.register(SERVERBOUND, LegacyPlayerInputWirePacket.class);

		WirePackets.register(SERVERBOUND, PlayerPositionWirePacket.class);
		WirePackets.register(SERVERBOUND, PlayerPositionOnGroundOnlyWirePacket.class);

		WirePackets.register(SERVERBOUND, PlayerPositionRotationWirePacket.class);
		WirePackets.register(SERVERBOUND, PlayerPositionRotationOnGroundOnlyWirePacket.class);

		WirePackets.register(SERVERBOUND, PlayerRotationWirePacket.class);
		WirePackets.register(SERVERBOUND, PlayerRotationOnGroundOnlyWirePacket.class);

		WirePackets.register(CLIENTBOUND, PlayerTeleportWirePacket.class);
		WirePackets.register(CLIENTBOUND, PlayerTeleportWithoutIdWirePacket.class);
		WirePackets.register(CLIENTBOUND, PlayerTeleportWithIdWirePacket.class);
		WirePackets.register(CLIENTBOUND, PlayerTeleportWithDismountWirePacket.class);
		WirePackets.register(CLIENTBOUND, PlayerTeleportWithoutDismountWirePacket.class);

		WirePackets.register(SERVERBOUND, TeleportAcceptWirePacket.class);
		WirePackets.register(SERVERBOUND, TeleportAcceptIdOnlyWirePacket.class);

		WirePackets.register(SERVERBOUND, PlaceRecipeWirePacket.class);
		WirePackets.register(SERVERBOUND, PlaceRecipeNumericRecipeWirePacket.class);
		WirePackets.register(SERVERBOUND, PlaceRecipeKeyRecipeWirePacket.class);

		WirePackets.register(CLIENTBOUND, ScoreboardDisplayWirePacket.class);
		WirePackets.register(CLIENTBOUND, ScoreboardDisplayByteSlotWirePacket.class);

		WirePackets.register(CLIENTBOUND, ScoreboardObjectiveWirePacket.class);
		WirePackets.register(CLIENTBOUND, ScoreboardObjectivePlainTextWirePacket.class);

		WirePackets.register(SERVERBOUND, TabCompleteRequestWirePacket.class);
		WirePackets.register(SERVERBOUND, TabCompleteRequestWithTargetWirePacket.class);
		WirePackets.register(SERVERBOUND, TabCompleteRequestWithCommandFlagWirePacket.class);

		WirePackets.register(CLIENTBOUND, TabCompleteResponseWirePacket.class);
		WirePackets.register(CLIENTBOUND, TabCompleteResponseStringsOnlyWirePacket.class);

		WirePackets.register(SERVERBOUND, VehicleMoveWirePacket.class);
		WirePackets.register(SERVERBOUND, VehicleMoveWithoutGroundWirePacket.class);

		WirePackets.register(SERVERBOUND, MenuButtonWirePacket.class);
		WirePackets.register(SERVERBOUND, MenuButtonByteFieldsWirePacket.class);

		WirePackets.register(SERVERBOUND, WindowCloseWirePacket.class);
		WirePackets.register(CLIENTBOUND, WindowCloseWirePacket.class);
		WirePackets.register(SERVERBOUND, WindowCloseByteIdWirePacket.class);
		WirePackets.register(CLIENTBOUND, WindowCloseByteIdWirePacket.class);

		WirePackets.register(CLIENTBOUND, HorseWindowOpenWirePacket.class);
		WirePackets.register(CLIENTBOUND, HorseWindowOpenProtocol477WirePacket.class);
		WirePackets.register(CLIENTBOUND, HorseWindowOpenProtocol767WirePacket.class);
		WirePackets.register(CLIENTBOUND, HorseWindowOpenProtocol768WirePacket.class);

		WirePackets.register(SERVERBOUND, TransactionWirePacket.class);
		WirePackets.register(CLIENTBOUND, TransactionWirePacket.class);

		WirePackets.register(CLIENTBOUND, WorldBorderWirePacket.class);
		WirePackets.register(CLIENTBOUND, WorldBorderInitializeWirePacket.class);
		WirePackets.register(CLIENTBOUND, WorldBorderSetCenterWirePacket.class);
		WirePackets.register(CLIENTBOUND, WorldBorderSetSizeWirePacket.class);
		WirePackets.register(CLIENTBOUND, WorldBorderLerpSizeWirePacket.class);
		WirePackets.register(CLIENTBOUND, WorldBorderWarningDelayWirePacket.class);
		WirePackets.register(CLIENTBOUND, WorldBorderWarningDistanceWirePacket.class);

		WirePackets.register(CLIENTBOUND, ExplosionIntBlockCountWirePacket.class);
		WirePackets.register(CLIENTBOUND, ExplosionVarIntBlockCountWirePacket.class);
		WirePackets.register(CLIENTBOUND, ExplosionDoublePositionWirePacket.class);

		WirePackets.register(CLIENTBOUND, GameStateChangeWirePacket.class);

		WirePackets.register(CLIENTBOUND, TimeUpdateWirePacket.class);
		WirePackets.register(CLIENTBOUND, TimeUpdateSignedDayTimeWirePacket.class);
	}
}
