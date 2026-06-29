package de.jpx3.intave.module.test.record;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class MaterialVariantStore {
	private final Material type;
	private final int variantIndex;

	public static final StreamCodec<ByteBuf, ByteBuf, MaterialVariantStore> STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.MATERIAL,
		MaterialVariantStore::type,
		ByteBufStreamCodecs.INTEGER,
		MaterialVariantStore::variantIndex,
		MaterialVariantStore::of
	);

	private MaterialVariantStore(
		Material type, int variantIndex
	) {
		this.type = type;
		this.variantIndex = variantIndex;
	}

	public Material type() {
		return type;
	}

	public int variantIndex() {
		return variantIndex;
	}

	@Override
	public String toString() {
		return "MaterialVariantStore{" +
			"type=" + type +
			", variantIndex=" + variantIndex +
			'}';
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		MaterialVariantStore that = (MaterialVariantStore) obj;
		return variantIndex == that.variantIndex && type == that.type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, variantIndex);
	}

	private final static Map<Material, Map<Integer, MaterialVariantStore>> cache = new ConcurrentHashMap<>();

	public static @NotNull MaterialVariantStore of(Material type, int variantIndex) {
		if (type == null) {
			throw new IllegalArgumentException("Material type cannot be null");
		}
		return cache.computeIfAbsent(type, t -> new HashMap<>())
			.computeIfAbsent(variantIndex, i -> new MaterialVariantStore(type, variantIndex));
	}

	public static @NotNull MaterialVariantStore air() {
		return of(Material.AIR, 0);
	}
}
