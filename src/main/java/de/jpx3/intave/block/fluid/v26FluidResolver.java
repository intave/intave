package de.jpx3.intave.block.fluid;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.locate.Locate;
import de.jpx3.intave.klass.locate.MethodSearchBySignature;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.IBlockData;
import org.bukkit.Material;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

@PatchyAutoTranslation
public final class v26FluidResolver implements FluidResolver {
	private static Object TAG_KEY_WATER = null;
	private static Object TAG_KEY_LAVA = null;

	private static final MethodHandle getHolderMethod;

	static {
		try {
			TAG_KEY_WATER = Lookup.serverField("TagsFluid", "WATER").get(null);
			TAG_KEY_LAVA = Lookup.serverField("TagsFluid", "LAVA").get(null);
		} catch (IllegalAccessException exception) {
			exception.printStackTrace();
		}
		getHolderMethod = MethodSearchBySignature
			.ofClass(Locate.classByKey("Fluid"))
			.withParameters(new Class[0])
			.withReturnType(Locate.classByKey("Holder")).search()
			.findFirstOrThrow();
	}

	private static Method fluidAccessMethod;
	private static Method isOfMethod;

	@Override
	@PatchyAutoTranslation
	public Fluid liquidFrom(Material type, int variantIndex) {
		IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, variantIndex);
		if (blockData == null) {
			return Dry.of();
		}
		try {
			net.minecraft.world.level.material.Fluid fluid;
			Block block = blockData.getBlock();
			if (fluidAccessMethod == null) {
				fluidAccessMethod = Lookup.serverClass("BlockBase").getDeclaredMethod("getFluidState", IBlockData.class);
				fluidAccessMethod.setAccessible(true);
			}
			fluid = (net.minecraft.world.level.material.Fluid) fluidAccessMethod.invoke(block, blockData);
			Holder<?> holder = (Holder<?>) getHolderMethod.invoke(fluid);
			if (isOfMethod == null) {
				isOfMethod = holder.getClass().getMethod("is", Lookup.serverClass("TagKey"));
				isOfMethod.setAccessible(true);
			}
			boolean dry = fluid.isEmpty();
			boolean isWater = !dry && (boolean) isOfMethod.invoke(holder, TAG_KEY_WATER);
			boolean isLava = !dry && (boolean) isOfMethod.invoke(holder, TAG_KEY_LAVA);
			boolean source = fluid.isSource();
			float height = fluid.d();
			if (dry) {
				return Dry.of();
			}
			BlockVariant variant = BlockVariantRegister.variantOf(type, variantIndex);
			Boolean fallingProperty = variant.propertyOf("falling");
			int level = MaterialMagic.isLavaOrWater(type) ? variant.propertyOf("level") : 8;
			if (fallingProperty == null) {
				fallingProperty = MaterialMagic.isLavaOrWater(type) && level >= 8;
			}
			return select(isWater, isLava, false, fallingProperty, height, level);
		} catch (Throwable throwable) {
			throwable.printStackTrace();
			return Dry.of();
		}
	}
}