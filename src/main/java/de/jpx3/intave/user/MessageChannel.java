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

package de.jpx3.intave.user;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

public enum MessageChannel {
	COMBAT_MODIFIERS("intave.command.combatmodifiers", false),
	DEBUG_ATTACK_RAYTRACE("intave.command.verbose", false),
	DEBUG_BLOCK_CACHE("intave.command.verbose", false),
	DEBUG_COLLISIONS("intave.command.verbose", false),
	DEBUG_HITBOXES("intave.command.verbose", false),
	DEBUG_HITBOX("intave.command.verbose", false),
	DEBUG_HITRAY("intave.command.verbose", false),
	DEBUG_ITEM_RESETS("intave.command.verbose", false),
	DEBUG_MOUNTS("intave.command.verbose", false),
	DEBUG_MOVEMENT("intave.command.verbose", false),
	DEBUG_MOTION("intave.command.verbose", false),
	DEBUG_SENT_INPUT("intave.command.verbose", false),
	DEBUG_PACKET_HOLD("intave.command.verbose", false),
	DEBUG_PLAYER_ACTIONS("intave.command.verbose", false),
	DEBUG_NERFS("intave.command.verbose", false),
	DEBUG_POSITION("intave.command.verbose", false),
	DEBUG_TELEPORT("intave.command.verbose", false),
	NOTIFY("intave.command.notify", true),
	VIOLATION_FINE("intave.command.verbose", false),
	VIOLATION_SIMPLE("intave.command.verbose", false),

	;

	final String permission;
	final boolean enabledByDefault;

	MessageChannel(String permission, boolean enabledByDefault) {
		this.permission = permission;
		this.enabledByDefault = enabledByDefault;
	}

	public String permission() {
		return permission;
	}

	public boolean isEnabledByDefault() {
		return enabledByDefault;
	}
}
