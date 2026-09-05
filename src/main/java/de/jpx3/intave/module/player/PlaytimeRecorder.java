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

package de.jpx3.intave.module.player;

import ac.intave.cloud.protocol.packets.player.ServerboundPlaytime;
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;

public final class PlaytimeRecorder extends Module {
	private Task task;

	@Override
	public void enable() {
		task = Tasks.periodicNamed(
			"PlaytimeRecorder.heartbeat", this::heartbeat, 0L, 20L * 60 * 2
		).startAsync();
	}

	private void heartbeat() {
		UserRepository.applyOnOnlineUsers(user -> {
			MovementMetadata movement = user.meta().movement();
			long activeTicks = movement.activeTicks.sumThenReset();
			long passiveTicks = movement.passiveTicks.sumThenReset();
			user.transmitCloudPacket(value ->
				new ServerboundPlaytime(value, activeTicks, passiveTicks)
			);
		});
	}

	@Override
	public void disable() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}
}
