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

package de.jpx3.intave.packet.event;

import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.Packet;
import de.jpx3.intave.user.User;
import org.bukkit.event.Cancellable;

public class PacketEvent implements AutoCloseable, Cancellable {
	private User user;
	private PacketDirection direction;
	private Packet packet;
	private boolean dirty;
	private boolean cancelled;

	public PacketEvent(User user, PacketDirection direction, Packet packet) {
		this.user = user;
		this.direction = direction;
		this.packet = packet;
	}

	public User user() {
		return user;
	}

	public PacketDirection direction() {
		return direction;
	}

	public Packet packet() {
		return packet;
	}

	public void setPacket(Packet newPacket) {
		this.packet = newPacket;
		this.dirty = true;
	}

	public boolean dirty() {
		return dirty;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean newCancelled) {
		cancelled = newCancelled;
	}

	@Override
	public void close() throws Exception {
		user = null;
		direction = null;
		packet = null;
	}
}
