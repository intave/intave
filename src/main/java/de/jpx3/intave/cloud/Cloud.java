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

package de.jpx3.intave.cloud;

import ac.intave.cloud.protocol.Packet;
import ac.intave.cloud.protocol.listener.Serverbound;
import ac.intave.cloud.protocol.packets.ServerboundCommand;
import ac.intave.cloud.protocol.packets.ServerboundRequestTrustfactor;
import ac.intave.cloud.protocol.packets.base.ServerboundIncidentIdRequest;
import ac.intave.cloud.protocol.packets.player.ServerboundPlayerKicked;
import ac.intave.cloud.protocol.packets.player.ServerboundRequestPlaytime;
import ac.intave.cloud.protocol.packets.player.ServerboundViolationHistoryRequest;
import ac.intave.cloud.protocol.packets.player.playtime.PlaytimeOfDay;
import ac.intave.cloud.protocol.packets.player.violation.ViolationHistorySession;
import ac.intave.cloud.protocol.packets.sampling.ServerboundPassSample;
import de.jpx3.intave.IntaveAccessor;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.IntaveAccess;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.cloud.protocol.CloudToken;
import de.jpx3.intave.cloud.request.CloudStorageGateaway;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongFunction;

@HighOrderService
public final class Cloud {
	// later
	private volatile Session session;
	private volatile int reconnectAttempts = 0;
	private final ObjectStore objectStore = new ObjectStore();

	private CloudConfig cloudConfig;
	private Task keepAliveTask;
	private boolean lastAttemptFailed = false;
	private volatile boolean shuttingDown;

	public void init() {
		try {
			objectStore.initialize();
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to initialize the cloud object store", exception);
		}
		ShutdownTasks.add(objectStore::shutdown);
		setupKeepAliveTick();
	}

	public void configInit(YamlConfiguration config) {
		cloudConfig = CloudConfig.from(config);
	}

	public CloudConfig config() {
		return cloudConfig;
	}

	public void connectMasterShard() {
		shuttingDown = false;
		if (cloudConfig.isEnabled()) {
			openSession(cloudConfig.connectionSettings());
			ShutdownTasks.add(this::disable);
		} else {
			IntaveLogger.logger().info("Cloud is disabled");
		}
	}

	private void disable() {
		shuttingDown = true;
		Session activeSession = session;
		if (activeSession != null) {
			activeSession.close();
		}
		if (keepAliveTask != null) {
			keepAliveTask.cancel();
			keepAliveTask = null;
		}
	}

	public void openSession(CloudToken cloudToken) {
		if (shuttingDown) {
			return;
		}
		if (cloudToken == null) {
			throw new IllegalArgumentException("Shard cannot be null");
		}
		Session session = new Session(cloudToken, objectStore);
		session.tryToConnect(success -> {
			if (success) {
				if (shuttingDown) {
					session.close();
					return;
				}
				this.session = session;
				session.subscribeToStarted(unused -> {
					reconnectAttempts = 0;
					if (lastAttemptFailed) {
						IntaveLogger.logger().info("Successfully reconnected to cloud");
						lastAttemptFailed = false;
					}
					setTrustAndStorage();
					Synchronizer.synchronize(() -> UserRepository.applyOnAll(user ->
						Synchronizer.synchronize(user, () -> playerLogin(user.player()))
					));
				});
			} else {
				if (shuttingDown) {
					if (this.session == session) {
						this.session = null;
					}
					return;
				}
				if (this.session != null && this.session != session) {
					return;
				}
				boolean reconnectingClosedSession = this.session == session;
				lastAttemptFailed = true;
				// called on failure or connection closure
				int attempts = reconnectAttempts;
				int retryingIn = (int) (Math.pow(2, attempts + 1.75) * 2) + 10;
				retryingIn += (int) (retryingIn * (Math.random() * 0.25));

				String retryReason = reconnectingClosedSession
					? "Cloud connection closed"
					: "Cloud reconnect unsuccessful";
				IntaveLogger.logger().warning(String.format(
					"%s, retrying in %d seconds, attempt %d/20",
					retryReason,
					retryingIn,
					attempts + 1
				));
				if (attempts < 20) {
					reconnectAttempts = attempts + 1;
					Synchronizer.synchronizeDelayed(() ->
						BackgroundExecutors.executeWhenever(() -> openSession(cloudToken)), 20 * retryingIn);
				} else {
					IntaveLogger.logger().warning("Unable to connect to " + cloudToken + " after 20 attempts");
					IntaveLogger.logger().warning("We will try to reconnect every 12 hours now");
					Synchronizer.synchronizeDelayed(() ->
						BackgroundExecutors.executeWhenever(() -> openSession(cloudToken)), 20 * 60 * 60 * 12);
				}
				if (this.session == session) {
					this.session = null;
				}
			}
		});
	}

	private void setupKeepAliveTick() {
		keepAliveTask = Tasks.periodicNamed(
			"Cloud.heartbeat", this::heartbeat, 20 * 10, 20 * 60
		).startAsync();
	}

	private void setTrustAndStorage() {
		IntaveAccess access = IntaveAccessor.unsafeAccess();
		CloudConfig.CloudFeatures features = cloudConfig.features();
		if (features.cloudStorageEnabled()) {
			access.setStorageGateway(new CloudStorageGateaway());
		}
	}

	public long sentBytes() {
		Session target = session;
		return target != null ? target.sentBytes() : 0;
	}

	public long receivedBytes() {
		Session target = session;
		return target != null ? target.receivedBytes() : 0;
	}

	public boolean isConnected() {
		Session target = session;
		return target != null && target.active();
	}

	public void sendPlayerPacket(
		User user, LongFunction<? extends Packet<Serverbound>> generator
	) {
		if (!user.hasPlayer()) {
			return;
		}
		BackgroundExecutors.execute(() -> {
			Session target = session;
			if (target != null) {
				target.sendUserPacket(user, generator);
			}
		});
	}

	public void requestViolationHistory(User user, Consumer<List<ViolationHistorySession>> callback) {
		Objects.requireNonNull(callback, "callback");
		if (!user.hasPlayer()) {
			return;
		}
		UUID requestId = UUID.randomUUID();
		BackgroundExecutors.execute(() -> {
			Session target = session;
			if (target != null) {
				target.registerViolationHistoryCallback(requestId, callback);
				target.sendUserPacket(user, id -> new ServerboundViolationHistoryRequest(id, requestId));
			}
		});
	}

	public void requestPlaytime(User user, Consumer<PlaytimeOfDay[]> callback) {
		Objects.requireNonNull(callback, "callback");
		if (!user.hasPlayer()) {
			return;
		}
		UUID requestId = UUID.randomUUID();
		BackgroundExecutors.execute(() -> {
			Session target = session;
			if (target != null) {
				target.registerPlaytimeCallback(requestId, callback);
				target.sendUserPacket(user, id -> new ServerboundRequestPlaytime(id, requestId));
			}
		});
	}

	public void requestIncidentId(User user, Consumer<String> callback) {
		Objects.requireNonNull(callback, "callback");
		if (!user.hasPlayer()) {
			return;
		}
		UUID requestId = UUID.randomUUID();
		BackgroundExecutors.execute(() -> {
			Session target = session;
			if (target != null) {
				target.registerIncidentIdCallback(requestId, callback);
				target.sendUserPacket(user, id -> new ServerboundIncidentIdRequest(id, requestId));
			}
		});
	}

	public void requestTrustfactor(User user) {
		sendPlayerPacket(user, ServerboundRequestTrustfactor::new);
	}

	public boolean sendCommand(User user, String command) {
		if (!user.hasPlayer()) {
			return false;
		}
		Session target = session;
		if (target == null || !target.canSend(ServerboundCommand.class)) {
			return false;
		}
		BackgroundExecutors.execute(() ->
			target.sendUserPacket(user, playerId -> new ServerboundCommand(playerId, command))
		);
		return true;
	}

	public void playerLogin(Player player) {
		User user = UserRepository.userOf(player);
		Session target = session;
		if (target != null) {
			target.announceUser(user);
		}
	}

	public void playerLogout(Player player) {
		User user = UserRepository.userOf(player);
		if (Modules.nayoro().recordingActiveFor(user)) {
			Modules.nayoro().disableRecordingFor(user);
		}
		Session target = session;
		if (target != null) {
			target.sendUserLogout(user);
		}
	}

	public void playerKicked(Player player, String reason) {
		User user = UserRepository.userOf(player);
		Session target = session;
		UUID requestId = target == null ? null : target.consumeKickRequest(player.getUniqueId());
		UUID finalRequestId = requestId == null ? UUID.randomUUID() : requestId;
		String finalReason = reason == null ? "" : reason;
		sendPlayerPacket(user, id -> new ServerboundPlayerKicked(id, finalReason, finalRequestId));
	}

	private void heartbeat() {
		objectStore.garbageCollect();
		Session target = session;
		if (target != null) {
			target.garbageCollectCallbacks();
		}
		if (target == null || !target.active()) {
			return;
		}
		target.keepAliveTick();
		target.retryMissedAttestations();
	}

	public void uploadSample(
		Player player, ByteBuffer buffer,
		UUID transmissionId, int sampleSubIndex
	) {
		User user = UserRepository.userOf(player);
		Session target = session;
		if (target != null) {
			target.sendUserPacket(
				user,
				id -> new ServerboundPassSample(id, transmissionId, sampleSubIndex, buffer)
			);
		}
	}

	public void completeSampleTransmission(
		Player player, UUID transmissionId, int sampleSubIndex
	) {
		uploadSample(player, ByteBuffer.allocate(0), transmissionId, sampleSubIndex);
	}

	public boolean isEnabled() {
		return cloudConfig.isEnabled();
	}
}
