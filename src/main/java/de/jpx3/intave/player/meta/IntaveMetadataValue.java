package de.jpx3.intave.player.meta;

import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

public final class IntaveMetadataValue implements MetadataValue {
	private final Object value;

	private IntaveMetadataValue(Object value) {
		this.value = value;
	}

	@Override
	public Object value() {
		return value;
	}

	@Override
	public int asInt() {
		return (int) value;
	}

	@Override
	public float asFloat() {
		return (float) value;
	}

	@Override
	public double asDouble() {
		return (double) value;
	}

	@Override
	public long asLong() {
		return (long) value;
	}

	@Override
	public short asShort() {
		return (short) value;
	}

	@Override
	public byte asByte() {
		return (byte) value;
	}

	@Override
	public boolean asBoolean() {
		return (boolean) value;
	}

	@Override
	public String asString() {
		return (String) value;
	}

	public static IntaveMetadataValue of(Object value) {
		return new IntaveMetadataValue(value);
	}

	@Override
	public Plugin getOwningPlugin() {
		return null;
	}

	@Override
	public void invalidate() {
	}
}
