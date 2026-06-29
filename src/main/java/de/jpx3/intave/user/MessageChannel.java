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
	DEBUG_ITEM_RESETS("intave.command.verbose", false),
	DEBUG_MOUNTS("intave.command.verbose", false),
	DEBUG_MOVEMENT("intave.command.verbose", false),
	DEBUG_PACKET_HOLD("intave.command.verbose", false),
	DEBUG_PLAYER_ACTIONS("intave.command.verbose", false),
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
