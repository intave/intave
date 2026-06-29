package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class MockShapeResolverPipeline implements ShapeResolverPipeline {
	private final Map<Material, Map<Integer, BlockShape>> collisionShape = new HashMap<>();
	private final Map<Material, Map<Integer, BlockShape>> outlineShape = new HashMap<>();

	@Override
	public BlockShape collisionShapeOf(World world, Player player, Material type, int variantIndex, int posX, int posY, int posZ) {
		return collisionShape.computeIfAbsent(type, t -> new HashMap<>())
			.computeIfAbsent(variantIndex, i -> BlockShapes.emptyShape())
			.contextualized(posX, posY, posZ);
	}

	@Override
	public BlockShape outlineShapeOf(World world, Player player, Material type, int variantIndex, int posX, int posY, int posZ) {
		return outlineShape.computeIfAbsent(type, t -> new HashMap<>())
			.computeIfAbsent(variantIndex, i -> BlockShapes.emptyShape())
			.contextualized(posX, posY, posZ);
	}

	public void setCollisionShape(Material type, int variantIndex, BlockShape shape) {
		collisionShape.computeIfAbsent(type, t -> new HashMap<>())
			.put(variantIndex, shape);
	}

	public void setOutlineShape(Material type, int variantIndex, BlockShape shape) {
		outlineShape.computeIfAbsent(type, t -> new HashMap<>())
			.put(variantIndex, shape);
	}


	public static MockShapeResolverPipeline createStoneDefault() {
		MockShapeResolverPipeline pipe = new MockShapeResolverPipeline();
		pipe.setCollisionShape(Material.STONE, 0, BlockShapes.originCube());
		pipe.setOutlineShape(Material.STONE, 0, BlockShapes.originCube());
		return pipe;
	}
}
