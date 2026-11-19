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

package de.jpx3.intave.share;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class MaterialAndVariant {
	private final static Map<Material, Map<Integer, MaterialAndVariant>> GLOBAL_CACHE = new HashMap<>();

	private final Material material;
	private final int variantId;

	public MaterialAndVariant(Material material, int variantId) {
		this.material = Objects.requireNonNull(material, "material");
		if (variantId < 0) {
			throw new IllegalArgumentException("Negative variant ID");
		}
		this.variantId = variantId;
	}

	public Material material() {
		return material;
	}

	public int variantId() {
		return variantId;
	}

	public static MaterialAndVariant of(Material material, int variantId) {
		Map<Integer, MaterialAndVariant> cache = GLOBAL_CACHE.computeIfAbsent(material, m -> new HashMap<>());
		return cache.computeIfAbsent(variantId, v -> new MaterialAndVariant(material, variantId));
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof MaterialAndVariant)) return false;
		MaterialAndVariant other = (MaterialAndVariant) obj;
		return material == other.material && variantId == other.variantId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(material, variantId);
	}

	@Override
	public String toString() {
		return "MaterialAndVariant{" +
			"material=" + material +
			", variantId=" + variantId +
			'}';
	}
}

