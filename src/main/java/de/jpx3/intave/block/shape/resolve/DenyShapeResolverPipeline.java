package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class DenyShapeResolverPipeline implements ShapeResolverPipeline {
	@Override
	public BlockShape collisionShapeOf(World world, Player player, Material type, int variantIndex, int posX, int posY, int posZ) {
		throw new UnsupportedOperationException("DenyShapeResolverPipeline does not support resolving collision shapes");
	}

	@Override
	public BlockShape outlineShapeOf(World world, Player player, Material type, int variantIndex, int posX, int posY, int posZ) {
		throw new UnsupportedOperationException("DenyShapeResolverPipeline does not support resolving outline shapes");
	}

	public static DenyShapeResolverPipeline create() {
		return new DenyShapeResolverPipeline();
	}
}
