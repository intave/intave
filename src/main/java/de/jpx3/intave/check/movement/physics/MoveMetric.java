package de.jpx3.intave.check.movement.physics;

public enum MoveMetric {
	ATTACK_REDUCE,
	BLOCK_PLACEMENT,
	EDGE_SNEAKING,
	EDGE_SNEAKING_TICK_GRANTS(0, 0),
	ELYTRA_FLYING,
	ENTITY_USE,
	EXTERNAL_VELOCITY,
	FIREWORK_ROCKETS,
	FLYING_PACKET_ACCURATE(0, 0),
	FLYING_PACKET_CLIENT(0, 0),
	INVENTORY_OPEN,
	IN_LAVA,
	IN_POWDER_SNOW,
	IN_WATER,
	IN_WEB,
	LONG_TELEPORT,
	NEARBY_COLLISION_INACCURACY(0, 10),
	RECEIVED_VELOCITY_PACKET,
	RIPTIDE_SPIN,
	SNEAKING,
	SPRINTING,
	SPRINT_CHANGE,
	STEP,
	TELEPORT,
	VEHICLE_ATTACHMENT,
	VEHICLE_DETACHMENT,
	VEHICLE_EXIT,
	VELOCITY,
	WATERFLOW_PUSH;

	private final int activeDefault;
	private final int pastDefault;

	MoveMetric() {
		this(0, 100);
	}

	MoveMetric(int activeDefault, int pastDefault) {
		this.activeDefault = activeDefault;
		this.pastDefault = pastDefault;
	}

	public int activeDefault() {
		return activeDefault;
	}

	public int pastDefault() {
		return pastDefault;
	}
}
