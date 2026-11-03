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

import de.jpx3.intave.packet.contract.PacketType;
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

final class WirePacketCatalog {
	private WirePacketCatalog() {
	}

	static void registerAll() {
		WirePackets.register(PacketType.Client.ABILITIES_IN, AbilityInWirePacket.class);
		WirePackets.register(PacketType.Client.ABILITIES_IN, AbilityInWithSpeedsWirePacket.class);

		WirePackets.register(PacketType.Server.ABILITIES_OUT, AbilityOutWirePacket.class);

		WirePackets.register(PacketType.Server.BLOCK_CHANGED_ACK, BlockChangedAckWirePacket.class);

		WirePackets.register(PacketType.Client.BLOCK_DIG, BlockDigWirePacket.class);
		WirePackets.register(PacketType.Client.BLOCK_DIG, BlockDigLegacyPositionWirePacket.class);
		WirePackets.register(PacketType.Client.BLOCK_DIG, BlockDigWithoutSequenceWirePacket.class);
		WirePackets.register(PacketType.Client.BLOCK_DIG, BlockDigWithSequenceWirePacket.class);

		WirePackets.register(PacketType.Client.USE_ITEM_ON, BlockInteractionWirePacket.class);
		WirePackets.register(PacketType.Client.USE_ITEM_ON, BlockInteractionProtocol107WirePacket.class);
		WirePackets.register(PacketType.Client.USE_ITEM_ON, BlockInteractionProtocol315WirePacket.class);
		WirePackets.register(PacketType.Client.USE_ITEM_ON, BlockInteractionProtocol477WirePacket.class);
		WirePackets.register(PacketType.Client.USE_ITEM_ON, BlockInteractionProtocol759WirePacket.class);

		WirePackets.register(PacketType.Client.CHAT_IN, ChatWirePacket.class);
		WirePackets.register(PacketType.Client.CHAT_IN, ChatShortMessageWirePacket.class);
		WirePackets.register(PacketType.Client.CHAT_IN, ChatUnsignedWirePacket.class);
		WirePackets.register(PacketType.Client.CHAT_IN, ChatWithPreviewWirePacket.class);
		WirePackets.register(PacketType.Client.CHAT_IN, ChatWithLastSeenWirePacket.class);
		WirePackets.register(PacketType.Client.CHAT_IN, ChatWithoutChecksumWirePacket.class);

		WirePackets.register(PacketType.Server.MAP_CHUNK_BULK, MapChunkBulkWirePacket.class);

		WirePackets.register(PacketType.Server.MAP_CHUNK, MapChunkWirePacket.class);

		WirePackets.register(PacketType.Server.UNLOAD_CHUNK, UnloadChunkWirePacket.class);
		WirePackets.register(PacketType.Server.UNLOAD_CHUNK, UnloadChunkCoordinatePairWirePacket.class);

		WirePackets.register(PacketType.Client.CLIENT_COMMAND, ClientCommandWirePacket.class);

		WirePackets.register(PacketType.Client.SETTINGS, ClientSettingsWirePacket.class);
		WirePackets.register(PacketType.Client.SETTINGS, ClientSettingsWithoutHandWirePacket.class);
		WirePackets.register(PacketType.Client.SETTINGS, ClientSettingsWithHandWirePacket.class);
		WirePackets.register(PacketType.Client.SETTINGS, ClientSettingsWithFilteringWirePacket.class);
		WirePackets.register(PacketType.Client.SETTINGS, ClientSettingsWithListingWirePacket.class);

		WirePackets.register(PacketType.Client.SET_PROTOCOL, HandshakeWirePacket.class);

		WirePackets.register(PacketType.Client.KEEP_ALIVE_IN, KeepAliveWirePacket.class);
		WirePackets.register(PacketType.Server.KEEP_ALIVE_OUT, KeepAliveWirePacket.class);
		WirePackets.register(PacketType.Client.KEEP_ALIVE_IN, KeepAliveVarIntIdWirePacket.class);
		WirePackets.register(PacketType.Server.KEEP_ALIVE_OUT, KeepAliveVarIntIdWirePacket.class);

		WirePackets.register(PacketType.Server.PING, PingWirePacket.class);

		WirePackets.register(PacketType.Client.PONG, PongWirePacket.class);

		WirePackets.register(PacketType.Server.COOKIE_REQUEST, CookieRequestWirePacket.class);

		WirePackets.register(PacketType.Client.COOKIE_RESPONSE, CookieResponseWirePacket.class);

		WirePackets.register(PacketType.Server.STORE_COOKIE, StoreCookieWirePacket.class);

		WirePackets.register(PacketType.Server.ANIMATION, EntityAnimationWirePacket.class);

		WirePackets.register(PacketType.Server.ATTACH_ENTITY, AttachEntityWirePacket.class);
		WirePackets.register(PacketType.Server.ATTACH_ENTITY, AttachEntityMountOrLeashWirePacket.class);

		WirePackets.register(PacketType.Client.ATTACK_ENTITY, AttackEntityWirePacket.class);

		WirePackets.register(PacketType.Server.ENTITY_DESTROY, EntityDestroyWirePacket.class);
		WirePackets.register(PacketType.Server.ENTITY_DESTROY, EntityDestroyLegacyArrayWirePacket.class);
		WirePackets.register(PacketType.Server.ENTITY_DESTROY, EntityDestroySingleEntityWirePacket.class);

		WirePackets.register(PacketType.Server.MOUNT, MountEntityWirePacket.class);

		WirePackets.register(PacketType.Server.ENTITY_TELEPORT, EntityPositionWirePacket.class);
		WirePackets.register(PacketType.Server.ENTITY_TELEPORT, EntityPositionFixedPointWirePacket.class);
		WirePackets.register(PacketType.Server.ENTITY_TELEPORT, EntityPositionDoublePositionWirePacket.class);
		WirePackets.register(PacketType.Server.ENTITY_POSITION_SYNC, EntityPositionSyncWirePacket.class);
		WirePackets.register(PacketType.Server.ENTITY_POSITION_SYNC, EntityPositionSyncPositionMoveRotationWirePacket.class);

		WirePackets.register(PacketType.Server.REL_ENTITY_MOVE, EntityRelativeMoveWirePacket.class);
		WirePackets.register(PacketType.Server.REL_ENTITY_MOVE, EntityRelativeMoveByteDeltasWirePacket.class);
		WirePackets.register(PacketType.Server.REL_ENTITY_MOVE, EntityRelativeMoveShortDeltasWirePacket.class);

		WirePackets.register(PacketType.Server.REL_ENTITY_MOVE_LOOK, EntityRelativeMoveLookWirePacket.class);
		WirePackets.register(PacketType.Server.REL_ENTITY_MOVE_LOOK, EntityRelativeMoveLookByteDeltasWirePacket.class);
		WirePackets.register(PacketType.Server.REL_ENTITY_MOVE_LOOK, EntityRelativeMoveLookShortDeltasWirePacket.class);

		WirePackets.register(PacketType.Server.ENTITY_STATUS, EntityStatusWirePacket.class);

		WirePackets.register(PacketType.Client.USE_ENTITY, EntityUseWirePacket.class);
		WirePackets.register(PacketType.Client.USE_ENTITY, EntityUseWithoutHandWirePacket.class);
		WirePackets.register(PacketType.Client.USE_ENTITY, EntityUseWithHandWirePacket.class);
		WirePackets.register(PacketType.Client.USE_ENTITY, InteractEntityWirePacket.class);

		WirePackets.register(PacketType.Server.ENTITY_VELOCITY, EntityVelocityWirePacket.class);
		WirePackets.register(PacketType.Server.ENTITY_VELOCITY, EntityVelocityShortVectorWirePacket.class);

		WirePackets.register(PacketType.Server.HELD_ITEM_SLOT_OUT, CarriedItemSlotWirePacket.class);

		WirePackets.register(PacketType.Client.HELD_ITEM_SLOT_IN, HeldItemSlotWirePacket.class);

		WirePackets.register(PacketType.Client.PICK_ITEM, PickItemWirePacket.class);

		WirePackets.register(PacketType.Client.USE_ITEM, UseItemWirePacket.class);
		WirePackets.register(PacketType.Client.USE_ITEM, UseItemProtocol107WirePacket.class);
		WirePackets.register(PacketType.Client.USE_ITEM, UseItemProtocol759WirePacket.class);

		WirePackets.register(PacketType.Client.CUSTOM_PAYLOAD_IN, PayloadInWirePacket.class);

		WirePackets.register(PacketType.Client.ENTITY_ACTION_IN, PlayerActionWirePacket.class);
		WirePackets.register(PacketType.Client.ENTITY_ACTION_IN, PlayerActionWithSneakingWirePacket.class);
		WirePackets.register(PacketType.Client.ENTITY_ACTION_IN, PlayerActionBeforeOffhandWirePacket.class);

		WirePackets.register(PacketType.Client.ARM_ANIMATION, ArmAnimationWirePacket.class);
		WirePackets.register(PacketType.Client.ARM_ANIMATION, ArmAnimationWithoutHandWirePacket.class);

		WirePackets.register(PacketType.Server.BED, BedUseWirePacket.class);

		WirePackets.register(PacketType.Server.EXPERIENCE, ExperienceWirePacket.class);

		WirePackets.register(PacketType.Client.FLYING, PlayerGroundWirePacket.class);
		WirePackets.register(PacketType.Client.FLYING, PlayerGroundOnGroundOnlyWirePacket.class);

		WirePackets.register(PacketType.Server.UPDATE_HEALTH, UpdateHealthWirePacket.class);

		WirePackets.register(PacketType.Server.PLAYER_INFO_REMOVE, PlayerInfoRemoveWirePacket.class);

		WirePackets.register(PacketType.Client.PLAYER_INPUT, PlayerInputWirePacket.class);
		WirePackets.register(PacketType.Client.PLAYER_INPUT, LegacyPlayerInputWirePacket.class);

		WirePackets.register(PacketType.Client.POSITION, PlayerPositionWirePacket.class);
		WirePackets.register(PacketType.Client.POSITION, PlayerPositionOnGroundOnlyWirePacket.class);

		WirePackets.register(PacketType.Client.POSITION_LOOK, PlayerPositionRotationWirePacket.class);
		WirePackets.register(PacketType.Client.POSITION_LOOK, PlayerPositionRotationOnGroundOnlyWirePacket.class);

		WirePackets.register(PacketType.Client.LOOK, PlayerRotationWirePacket.class);
		WirePackets.register(PacketType.Client.LOOK, PlayerRotationOnGroundOnlyWirePacket.class);

		WirePackets.register(PacketType.Server.POSITION, PlayerTeleportWirePacket.class);
		WirePackets.register(PacketType.Server.POSITION, PlayerTeleportWithoutIdWirePacket.class);
		WirePackets.register(PacketType.Server.POSITION, PlayerTeleportWithIdWirePacket.class);
		WirePackets.register(PacketType.Server.POSITION, PlayerTeleportWithDismountWirePacket.class);
		WirePackets.register(PacketType.Server.POSITION, PlayerTeleportWithoutDismountWirePacket.class);

		WirePackets.register(PacketType.Client.TELEPORT_ACCEPT, TeleportAcceptWirePacket.class);
		WirePackets.register(PacketType.Client.TELEPORT_ACCEPT, TeleportAcceptIdOnlyWirePacket.class);

		WirePackets.register(PacketType.Client.AUTO_RECIPE, PlaceRecipeWirePacket.class);
		WirePackets.register(PacketType.Client.AUTO_RECIPE, PlaceRecipeNumericRecipeWirePacket.class);
		WirePackets.register(PacketType.Client.AUTO_RECIPE, PlaceRecipeKeyRecipeWirePacket.class);

		WirePackets.register(PacketType.Server.SCOREBOARD_DISPLAY_OBJECTIVE, ScoreboardDisplayWirePacket.class);
		WirePackets.register(PacketType.Server.SCOREBOARD_DISPLAY_OBJECTIVE, ScoreboardDisplayByteSlotWirePacket.class);

		WirePackets.register(PacketType.Server.SCOREBOARD_OBJECTIVE, ScoreboardObjectiveWirePacket.class);
		WirePackets.register(PacketType.Server.SCOREBOARD_OBJECTIVE, ScoreboardObjectivePlainTextWirePacket.class);

		WirePackets.register(PacketType.Client.TAB_COMPLETE_IN, TabCompleteRequestWirePacket.class);
		WirePackets.register(PacketType.Client.TAB_COMPLETE_IN, TabCompleteRequestWithTargetWirePacket.class);
		WirePackets.register(PacketType.Client.TAB_COMPLETE_IN, TabCompleteRequestWithCommandFlagWirePacket.class);

		WirePackets.register(PacketType.Server.TAB_COMPLETE_OUT, TabCompleteResponseWirePacket.class);
		WirePackets.register(PacketType.Server.TAB_COMPLETE_OUT, TabCompleteResponseStringsOnlyWirePacket.class);

		WirePackets.register(PacketType.Client.VEHICLE_MOVE, VehicleMoveWirePacket.class);
		WirePackets.register(PacketType.Client.VEHICLE_MOVE, VehicleMoveWithoutGroundWirePacket.class);

		WirePackets.register(PacketType.Client.ENCHANT_ITEM, MenuButtonWirePacket.class);
		WirePackets.register(PacketType.Client.ENCHANT_ITEM, MenuButtonByteFieldsWirePacket.class);

		WirePackets.register(PacketType.Client.CLOSE_WINDOW, WindowCloseWirePacket.class);
		WirePackets.register(PacketType.Server.CLOSE_WINDOW, WindowCloseWirePacket.class);
		WirePackets.register(PacketType.Client.CLOSE_WINDOW, WindowCloseByteIdWirePacket.class);
		WirePackets.register(PacketType.Server.CLOSE_WINDOW, WindowCloseByteIdWirePacket.class);

		WirePackets.register(PacketType.Server.OPEN_WINDOW_HORSE, HorseWindowOpenWirePacket.class);
		WirePackets.register(PacketType.Server.OPEN_WINDOW_HORSE, HorseWindowOpenProtocol477WirePacket.class);
		WirePackets.register(PacketType.Server.OPEN_WINDOW_HORSE, HorseWindowOpenProtocol767WirePacket.class);
		WirePackets.register(PacketType.Server.OPEN_WINDOW_HORSE, HorseWindowOpenProtocol768WirePacket.class);

		WirePackets.register(PacketType.Client.TRANSACTION_IN, TransactionWirePacket.class);
		WirePackets.register(PacketType.Server.TRANSACTION_OUT, TransactionWirePacket.class);

		WirePackets.register(PacketType.Server.WORLD_BORDER, WorldBorderWirePacket.class);
		WirePackets.register(PacketType.Server.INITIALIZE_BORDER, WorldBorderInitializeWirePacket.class);
		WirePackets.register(PacketType.Server.SET_BORDER_CENTER, WorldBorderSetCenterWirePacket.class);
		WirePackets.register(PacketType.Server.SET_BORDER_SIZE, WorldBorderSetSizeWirePacket.class);
		WirePackets.register(PacketType.Server.SET_BORDER_LERP_SIZE, WorldBorderLerpSizeWirePacket.class);
		WirePackets.register(PacketType.Server.SET_BORDER_WARNING_DELAY, WorldBorderWarningDelayWirePacket.class);
		WirePackets.register(PacketType.Server.SET_BORDER_WARNING_DISTANCE, WorldBorderWarningDistanceWirePacket.class);

		WirePackets.register(PacketType.Server.EXPLOSION, ExplosionIntBlockCountWirePacket.class);
		WirePackets.register(PacketType.Server.EXPLOSION, ExplosionVarIntBlockCountWirePacket.class);
		WirePackets.register(PacketType.Server.EXPLOSION, ExplosionDoublePositionWirePacket.class);

		WirePackets.register(PacketType.Server.GAME_STATE_CHANGE, GameStateChangeWirePacket.class);

		WirePackets.register(PacketType.Server.UPDATE_TIME, TimeUpdateWirePacket.class);
		WirePackets.register(PacketType.Server.UPDATE_TIME, TimeUpdateSignedDayTimeWirePacket.class);
	}
}
