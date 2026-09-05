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

package de.jpx3.intave.cloud.protocol.pipeline;

import ac.intave.cloud.protocol.Identity;
import ac.intave.cloud.protocol.Packet;
import ac.intave.cloud.protocol.listener.Clientbound;
import ac.intave.cloud.protocol.packets.ClientboundMitigation;
import ac.intave.cloud.protocol.packets.ClientboundSetTrustfactor;
import ac.intave.cloud.protocol.packets.ClientboundViolation;
import ac.intave.cloud.protocol.packets.base.*;
import ac.intave.cloud.protocol.packets.base.environment.EnvironmentPlayer;
import ac.intave.cloud.protocol.packets.base.environment.EnvironmentPlugin;
import ac.intave.cloud.protocol.packets.base.environment.EnvironmentWorld;
import ac.intave.cloud.protocol.packets.player.*;
import ac.intave.cloud.protocol.packets.sampling.ClientboundSetSamplingBufferSize;
import ac.intave.cloud.protocol.packets.sampling.ClientboundSetSamplingState;
import ac.intave.samples.share.Classifier;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.jpx3.intave.IntaveAccessor;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckService;
import de.jpx3.intave.cloud.Session;
import de.jpx3.intave.diagnostic.timings.Timing;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationProcessor;
import de.jpx3.intave.player.ActionBar;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static ac.intave.cloud.protocol.Direction.CLIENTBOUND;
import static de.jpx3.intave.module.nayoro.OperationalMode.CLOUD_TRANSMISSION;

public final class StandardClientRetriever extends ChannelInboundHandlerAdapter implements Clientbound {
	private final Session session;

	public StandardClientRetriever(Session session) {
		this.session = session;
	}

	@Override
	public void channelRead(ChannelHandlerContext channelHandlerContext, Object message) {
		if (!(message instanceof Packet)) {
			throw new IllegalArgumentException("Cloud processor received unexpected message type " + (message == null ? "null" : message.getClass().getName()));
		}
		Packet<?> packet = (Packet<?>) message;
		if (packet.direction() != CLIENTBOUND) {
			throw new IllegalArgumentException("Cloud processor received " + packet.direction().name().toLowerCase() + " packet '" + packet.name() + "' in the clientbound pipeline");
		}
		onSelect(packet);
	}

	@Override
	public void onCloseConnection(ClientboundDisconnect packet) {
		IntaveLogger.logger().info("[Cloud] Connection closed by cloud: " + packet.reason());
		session.close();
	}

	@Override
	public void onClientHello(ClientboundHello packet) {
		throw new RuntimeException("Unexpected packet " + packet.name());
	}

	@Override
	public void onSetPlayerId(ClientboundSetPlayerId packet) {
		UUID userId = packet.identity().id();
		if (!session.awaitingPlayerId(packet.identity())) {
			Player player = findPlayer(packet.identity());
			if (player == null) {
				IntaveLogger.logger().error("[Cloud] Received player ID for unknown player: " + packet.identity());
				return;
			}
			userId = player.getUniqueId();
		}
		User user = UserRepository.userOf(userId);
		if (!user.hasPlayer()) {
			// A player may have logged out while its PLAYER_LOGIN was in flight.
			// Session keeps the UUID so it can immediately acknowledge with PLAYER_LOGOUT.
			if (!session.awaitingPlayerId(packet.identity())) {
				IntaveLogger.logger().error("[Cloud] Received player ID for user without player: " + userId);
				return;
			}
		}
		session.setUserId(userId, packet.id());
	}

	@Override
	public void onConfirmAttestations(ClientboundConfirmAttestations packet) {
		session.confirmAttestations(packet.requestIds());
	}

	@Override
	public void onClarifyUnknownPlayerId(ClientboundClarifyUnknownPlayerId packet) {
		User user = session.userById(packet.playerId());
		if (!user.hasPlayer()) {
			IntaveLogger.logger().error("[Cloud] Cannot clarify unknown player ID " + packet.playerId() + ": no local player is mapped to it");
			return;
		}
		session.clarifyUnknownPlayerId(user, packet.playerId());
	}

	@Override
	public void onKeepAlive(ClientboundKeepAlive packet) {
		// do nothing
	}

	@Override
	public void onSetTrustfactor(ClientboundSetTrustfactor packet) {
		User user = session.userById(packet.id());
		if (!user.hasPlayer()) {
			IntaveLogger.logger().error("[Cloud] Cannot apply packet '" + packet.name() + "': player id " + packet.id() + " is not mapped in this cloud session");
			return;
		}
		Synchronizer.synchronize(user, () -> {
			Player player = user.player();
			if (!player.isOnline()) {
				IntaveLogger.logger().warn("[Cloud] Player id " + packet.id() + " went offline before packet '" + packet.name() + "' could be applied");
				return;
			}
			IntaveAccessor.unsafeAccess().player(player).setTrustFactor(de.jpx3.intave.access.player.trust.TrustFactor.valueOf(packet.trustFactor()));
		});
	}

	@Override
	public void onMitigation(ClientboundMitigation packet) {
		User user = session.userById(packet.playerId());
		if (!user.hasPlayer()) {
			return;
		}
		AttackNerfStrategy strat = AttackNerfStrategy.byName(packet.mitigationName());
		if (strat == null) {
			return;
		}
		if (packet.durationMillis() == 0L) {
			user.nerfOnce(strat, "cc");
		} else if (packet.durationMillis() < 0L) {
			user.nerfPermanently(strat, "cc");
		} else {
			user.nerf(strat, "cc");
		}
	}

	@Override
	public void onSetSamplingBufferSize(ClientboundSetSamplingBufferSize packet) {
		User user = session.userById(packet.playerId());
		if (!user.hasPlayer()) {
			IntaveLogger.logger().warn("[Cloud] Cannot change sampling buffer size: player id " + packet.playerId() + " is not mapped");
			return;
		}
		if (packet.bufferSize() <= 0) {
			IntaveLogger.logger().warn("[Cloud] Ignoring invalid sampling buffer size " + packet.bufferSize());
			return;
		}
		Modules.nayoro().setSamplingBufferSize(user, packet.bufferSize());
	}

	@Override
	public void onChangeSampling(ClientboundSetSamplingState packet) {
		User user = session.userById(packet.id());
		if (!user.hasPlayer()) {
			return;
		}
		Nayoro nayoro = Modules.nayoro();
		boolean startRequested = packet.newState() == ClientboundSetSamplingState.SamplingState.START;
		boolean currentlyActive = nayoro.recordingActiveFor(user);
		if (currentlyActive && startRequested) {
			nayoro.disableRecordingFor(user);
			currentlyActive = false;
		}
		if (currentlyActive == startRequested) {
			return;
		}
		IntaveLogger.logger().info("Sampling state changed for " + user + ": " + (startRequested ? "START" : "STOP"));
		if (currentlyActive) {
			nayoro.disableRecordingFor(user);
		} else {
			nayoro.enableRecordingFor(user, Classifier.UNKNOWN, CLOUD_TRANSMISSION, packet.transmissionId());
		}
	}

	@Override
	public void onSendMessage(ClientboundSendMessage packet) {
		User user = session.userById(packet.playerId());
		if (!user.hasPlayer()) {
			IntaveLogger.logger().error("[Cloud] Cannot deliver clientbound packet '" + packet.name() + "': player id " + packet.playerId() + " is not mapped in this cloud session");
			return;
		}
		List<TextComponent> lines = new ArrayList<>(packet.lines());
		Synchronizer.synchronize(user, () -> {
			Player player = user.player();
			if (!player.isOnline()) {
				IntaveLogger.logger().warn("[Cloud] Player id " + packet.playerId() + " went offline before packet '" + packet.name() + "' could be delivered");
				return;
			}
			try {
				for (TextComponent component : lines) {
					player.spigot().sendMessage(component);
				}
			} catch (Exception exception) {
				IntaveLogger.logger().error("[Cloud] Failed to deliver packet '" + packet.name() + "' to " + player.getName() + ": " + Session.describeFailure(exception));
				exception.printStackTrace();
			}
		});
	}

	@Override
	public void onSendActionbar(ClientboundSendActionbar packet) {
		User user = session.userById(packet.playerId());
		if (!user.hasPlayer()) {
			IntaveLogger.logger().error("[Cloud] Cannot deliver actionbar: player id " + packet.playerId() + " is not mapped");
			return;
		}
		String line = packet.line() == null ? "" : packet.line().toLegacyText();
		Synchronizer.synchronize(user, () -> {
			Player player = user.player();
			if (player.isOnline()) {
				ActionBar.sendActionBar(player, line);
			}
		});
	}

	@Override
	public void onRequestPermissions(ClientboundRequestPermissions packet) {
		User user = session.userById(packet.playerId());
		if (!user.hasPlayer()) {
			return;
		}
		Synchronizer.synchronize(user, () -> {
			Player player = user.player();
			if (!player.isOnline()) {
				return;
			}
			List<String> permissions = player.getEffectivePermissions().stream().filter(PermissionAttachmentInfo::getValue).map(PermissionAttachmentInfo::getPermission).collect(Collectors.toList());
			session.sendPacket(new ServerboundPlayerPermissions(packet.playerId(), player.isOp() ? 4 : 0, permissions, packet.requestUuid()));
		});
	}

	@Override
	public void onDiagnosticsRequest(ClientboundDiagnosticsRequest packet) {
		Synchronizer.synchronize(() -> respondToDiagnosticsRequest(packet));
	}

	private void respondToDiagnosticsRequest(ClientboundDiagnosticsRequest packet) {
		JsonObject diagnostics = new JsonObject();
		Map<String, String> errors = new LinkedHashMap<>();
		int maxEntries = Math.max(1, packet.maxEntriesPerSection());
		for (String requestedSection : packet.sections()) {
			String responseSection = requestedSection == null ? "null" : requestedSection;
			String section = responseSection.toLowerCase(Locale.ROOT);
			try {
				JsonElement value = diagnosticsSection(section, maxEntries);
				if (value == null) {
					errors.put(responseSection, "Unsupported diagnostics section");
				} else {
					diagnostics.add(responseSection, value);
				}
			} catch (Exception exception) {
				errors.put(responseSection, Session.describeFailure(exception));
			}
		}
		boolean truncated = trimDiagnostics(diagnostics, packet.maxResponseBytes());
		session.sendPacket(new ServerboundDiagnosticsResponse(packet.requestUuid(), System.currentTimeMillis(), diagnostics, errors, truncated));
	}

	private static JsonElement diagnosticsSection(String section, int maxEntries) {
		switch (section) {
			case "timings":
				return timingsDiagnostics(maxEntries);
			case "threads":
				return threadsDiagnostics(maxEntries);
			default:
				return null;
		}
	}

	private static JsonArray timingsDiagnostics(int maxEntries) {
		List<Timing> timings = new ArrayList<>(Timings.timingPool());
		timings.removeIf(timing -> timing.isPacketEventTiming() || timing.isBukkitEventTiming());
		timings.sort(Timing::compareTo);
		JsonArray values = new JsonArray();
		for (int i = 0; i < timings.size() && i < maxEntries; i++) {
			Timing timing = timings.get(i);
			JsonObject entry = new JsonObject();
			entry.addProperty("name", timing.name());
			entry.addProperty("recordedCalls", timing.recordedCalls());
			entry.addProperty("totalDurationMillis", timing.totalDurationMillis());
			entry.addProperty("averageCallDurationMillis", timing.averageCallDurationInMillis());
			entry.addProperty("p99CallDurationMillis", timing.p99CallDurationInMillis());
			values.add(entry);
		}
		return values;
	}

	private static JsonArray threadsDiagnostics(int maxEntries) {
		JsonArray threads = new JsonArray();
		int count = 0;
		for (Thread thread : Thread.getAllStackTraces().keySet()) {
			if (count++ >= maxEntries) {
				break;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("id", thread.getId());
			entry.addProperty("name", thread.getName());
			entry.addProperty("state", thread.getState().name());
			entry.addProperty("daemon", thread.isDaemon());
			threads.add(entry);
		}
		return threads;
	}

	private static boolean trimDiagnostics(JsonObject diagnostics, int maxBytes) {
		boolean truncated = false;
		while (diagnostics.toString().getBytes(StandardCharsets.UTF_8).length > maxBytes && !diagnostics.entrySet().isEmpty()) {
			List<String> keys = new ArrayList<>();
			diagnostics.entrySet().forEach(entry -> keys.add(entry.getKey()));
			String lastKey = keys.get(keys.size() - 1);
			JsonElement last = diagnostics.get(lastKey);
			if (last.isJsonArray() && last.getAsJsonArray().size() > 0) {
				JsonArray trimmed = new JsonArray();
				JsonArray array = last.getAsJsonArray();
				for (int i = 0; i < array.size() - 1; i++) {
					trimmed.add(array.get(i));
				}
				diagnostics.add(lastKey, trimmed);
			} else {
				diagnostics.remove(lastKey);
			}
			truncated = true;
		}
		return truncated;
	}

	@Override
	public void onSetPacketLoggingState(ClientboundSetPacketLoggingState packet) {
		UUID playerId = session.playerUUIDBy(packet.playerId());
		if (playerId == null) {
			IntaveLogger.logger().error("[Cloud] Cannot change packet logging state: player id " + packet.playerId() + " is not mapped");
			return;
		}
		boolean enabled = packet.newState() == ClientboundSetPacketLoggingState.PacketLoggingState.START;
		User user = session.userById(packet.playerId());
		Runnable update = () -> {
			Player player = user.hasPlayer() ? user.player() : null;
			if (enabled && (player == null || !player.isOnline())) {
				IntaveLogger.logger().warn("[Cloud] Player id " + packet.playerId() + " went offline before packet logging could start");
				return;
			}
			List<String> packetLog = Modules.tracker().packetLogging().setPacketLoggingState(playerId, player, enabled);
			if (!enabled) {
				session.sendPacket(new ServerboundPacketLog(packet.playerId(), packetLog));
			}
		};
		if (user.hasPlayer()) {
			Synchronizer.synchronize(user, update);
		} else {
			Synchronizer.synchronize(update);
		}
	}

	@Override
	public void onKickPlayer(ClientboundKickPlayer packet) {
		User user = session.userById(packet.playerId());
		if (!user.hasPlayer()) {
			IntaveLogger.logger().error("[Cloud] Cannot kick player: player id " + packet.playerId() + " is not mapped");
			return;
		}
		Synchronizer.synchronize(user, () -> {
			Player player = user.player();
      String reason = packet.reason() == null ? "" : packet.reason();
      if (player != null && player.isOnline()) {
        session.rememberKickRequest(packet.playerId(), packet.requestUuid());
        player.kickPlayer(reason);
      }
    });
	}

	@Override
	public void onRespondPlaytime(ClientboundRespondPlaytime packet) {
		session.completePlaytime(packet.requestUuid(), packet.playtimeOfDay());
	}

	@Override
	public void onEnvironmentRequest(ClientboundEnvironmentRequest packet) {
		Synchronizer.synchronize(() -> {
			List<EnvironmentPlugin> plugins = Arrays.stream(Bukkit.getPluginManager().getPlugins()).map(plugin -> new EnvironmentPlugin(plugin.getName(), plugin.getDescription().getVersion(), "")).collect(Collectors.toList());
			List<EnvironmentPlayer> players = Bukkit.getOnlinePlayers().stream().map(player -> new EnvironmentPlayer(player.getUniqueId(), player.getName(), player.getGameMode().name())).collect(Collectors.toList());
			List<EnvironmentWorld> worlds = Bukkit.getWorlds().stream().map(world -> new EnvironmentWorld(world.getUID(), world.getName())).collect(Collectors.toList());
			session.sendPacket(new ServerboundEnvironmentResponse(Bukkit.getName() + "@" + Bukkit.getVersion(), plugins, players, worlds, packet.requestUuid()));
		});
	}

	@Override
	public void onIncidentIdResponse(ClientboundIncidentIdResponse packet) {
		session.completeIncidentId(packet.requestUuid(), packet.incidentId());
	}

	@Override
	public void onViolationHistoryResponse(ClientboundViolationHistoryResponse packet) {
		session.completeViolationHistory(packet.requestUuid(), packet.sessions());
		IntaveLogger.logger().info("[Cloud] Received violation history response " + packet.requestUuid() + " for player id " + packet.playerId() + " with " + packet.sessions().size() + " sessions");
	}

	@Override
	public void onViolation(ClientboundViolation packet) {
		User user = session.userById(packet.id());
		if (!user.hasPlayer()) {
			return;
		}
		Player player = user.player();
		try {
			IntavePlugin intave = IntavePlugin.singletonInstance();
			CheckService checks = intave.checks();
			Check check = checks.searchCheck(packet.check());
			Violation violation = Violation.builderFor(check.getClass()).forPlayer(player).withCustomThreshold(packet.threshold()).withMessage(packet.message()).withDetails(packet.details()).withVL(packet.vl()).build();
			ViolationProcessor violationProcessor = Modules.violationProcessor();
			violationProcessor.processViolation(violation);
		} catch (Exception exception) {
			IntaveLogger.logger().error("[Cloud] Failed to process clientbound violation for player id " + packet.id() + ": " + Session.describeFailure(exception));
			exception.printStackTrace();
		}
	}

	@Override
	public void onListObjects(ClientboundListObjects packet) {
		BackgroundExecutors.executeWhenever(() -> {
			try {
				session.sendPacket(new ServerboundSendObjectList(packet.requestUuid(), session.objectStore().list()));
			} catch (Exception exception) {
				IntaveLogger.logger().error("[Cloud] Failed to list cloud objects: " + Session.describeFailure(exception));
				session.sendPacket(new ServerboundSendObjectList(packet.requestUuid(), new ArrayList<>()));
			}
		});
	}

	@Override
	public void onGetObject(ClientboundGetObject packet) {
		BackgroundExecutors.executeWhenever(() -> {
			try {
				Path object = session.objectStore().existingObject(packet.key());
				if (object == null) {
					IntaveLogger.logger().warn("[Cloud] Requested object does not exist: " + packet.key());
					sendEmptyObject(packet.requestUuid());
					return;
				}
				sendObject(packet.requestUuid(), object);
			} catch (Exception exception) {
				IntaveLogger.logger().error("[Cloud] Failed to read cloud object '" + packet.key() + "': " + Session.describeFailure(exception));
				sendEmptyObject(packet.requestUuid());
			}
		});
	}

	@Override
	public void onPutObject(ClientboundPutObject packet) {
		ByteBuffer readable = packet.data().duplicate();
		byte[] bytes = new byte[readable.remaining()];
		readable.get(bytes);
		BackgroundExecutors.executeWhenever(() -> {
			try {
				session.objectStore().put(packet.requestUuid(), packet.key(), packet.chunkIndex(), packet.lastChunk(), ByteBuffer.wrap(bytes));
			} catch (Exception exception) {
				IntaveLogger.logger().error("[Cloud] Failed to write cloud object '" + packet.key() + "': " + Session.describeFailure(exception));
			}
		});
	}

	@Override
	public void onEraseObject(ClientboundEraseObject packet) {
		BackgroundExecutors.executeWhenever(() -> {
			try {
				session.objectStore().erase(packet.key());
			} catch (Exception exception) {
				IntaveLogger.logger().error("[Cloud] Failed to erase cloud object '" + packet.key() + "': " + Session.describeFailure(exception));
			}
		});
	}

	@Override
	public void onUncaught(Packet<?> packet) {
		IntaveLogger.logger().error("[Cloud] Dropped clientbound packet '" + packet.name() + "' (version " + packet.version() + "): no application handler is registered");
	}

	private void sendObject(UUID requestId, Path object) throws IOException {
		long remaining = Files.size(object);
		try (InputStream input = Files.newInputStream(object)) {
			if (remaining == 0) {
				sendEmptyObject(requestId);
				return;
			}
			byte[] buffer = new byte[ServerboundSendObject.MAX_CHUNK_BYTES];
			int chunkIndex = 0;
			while (remaining > 0) {
				int length = (int) Math.min(remaining, buffer.length);
				readFully(input, buffer, length);
				remaining -= length;
				session.sendPacket(new ServerboundSendObject(requestId, chunkIndex++, remaining == 0, ByteBuffer.wrap(java.util.Arrays.copyOf(buffer, length))));
			}
		}
	}

	private void sendEmptyObject(UUID requestId) {
		session.sendPacket(new ServerboundSendObject(requestId, 0, true, ByteBuffer.allocate(0)));
	}

	private static void readFully(InputStream input, byte[] buffer, int length) throws IOException {
		int offset = 0;
		while (offset < length) {
			int read = input.read(buffer, offset, length - offset);
			if (read < 0) {
				throw new IOException("Object changed while it was being read");
			}
			offset += read;
		}
	}

	private static Player findPlayer(Identity identity) {
		if (identity.id() != null) {
			Player player = Bukkit.getPlayer(identity.id());
			if (player != null) {
				return player;
			}
		}
		return identity.name() == null ? null : Bukkit.getPlayerExact(identity.name());
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext channelHandlerContext, Throwable throwable) {
		channelHandlerContext.fireExceptionCaught(throwable);
	}

	@Override
	public void channelInactive(ChannelHandlerContext context) {
		if (session.started()) {
			IntaveLogger.logger().warn("[Cloud] Channel became inactive while the cloud session was ready");
		}
		context.fireChannelInactive();
	}
}
