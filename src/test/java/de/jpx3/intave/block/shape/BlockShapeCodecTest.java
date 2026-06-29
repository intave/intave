package de.jpx3.intave.block.shape;

import de.jpx3.intave.share.BoundingBox;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class BlockShapeCodecTest {
	@Test
	void emptyShapeRoundTrips() {
		BlockShape decoded = roundTrip(BlockShapes.emptyShape());
		assertEquals(BlockShapes.emptyShape(), decoded);
		assertTrue(decoded.isEmpty());
		assertTrue(decoded.elementaryBoxes().isEmpty());
	}

	@Test
	void singleBoxRoundTrips() {
		BoundingBox box = BoundingBox.fromBounds(0.0, 0.0, 0.0, 0.5, 1.0, 0.5);
		BlockShape decoded = roundTrip(box);

		assertInstanceOf(BoundingBox.class, decoded);
		assertSameBoxes(box, decoded);
	}

	@Test
	void cubeShapeRoundTripsAsCubeShape() {
		BlockShape cube = BlockShapes.cubeAt(3, 4, 5);
		BlockShape decoded = roundTrip(cube);

		assertInstanceOf(CubeShape.class, decoded);
		assertSameBoxes(cube, decoded);
	}

	@Test
	void voxelShapeRoundTripsAsVoxelShape() {
		BlockShape voxelShape = VoxelShape.fromOriginBox(0.0, 0.0, 0.0, 0.5, 1.0, 0.5);
		BlockShape decoded = roundTrip(voxelShape);

		assertInstanceOf(VoxelShape.class, decoded);
		assertSameBoxes(voxelShape, decoded);
	}

	@Test
	void arrayShapeRoundTripsAsArrayShape() {
		BlockShape arrayShape = new ArrayBlockShape(
			BlockShapes.cubeAt(3, 4, 5),
			BoundingBox.fromBounds(0.0, 0.0, 0.0, 0.5, 1.0, 0.5)
		);
		BlockShape decoded = roundTrip(arrayShape);

		assertInstanceOf(ArrayBlockShape.class, decoded);
		assertSameBoxes(arrayShape, decoded);
	}

	@Test
	void mergedShapeRoundTripsAsMergeShape() {
		BlockShape merged = BlockShapes.optimizedMerge(
			BoundingBox.fromBounds(0.0, 0.0, 0.0, 0.5, 1.0, 0.5),
			BoundingBox.fromBounds(0.5, 0.0, 0.5, 1.0, 1.0, 1.0)
		);
		BlockShape decoded = roundTrip(merged);
		assertSameBoxes(merged, decoded);
	}

	@Test
	void jumboShapeRoundTripsAsJumboShape() {
		BlockShape shape = new MergeBlockShape(
			new ArrayBlockShape(
				IntStream.range(0, 10).mapToObj(_ -> BoundingBox.random()).toArray(BoundingBox[]::new)
			),
			new MergeBlockShape(
				BlockShapes.cubeAt(0, 1, 1),
				BlockShapes.cubeAt(0, 1, 2)
			)
		);
		BlockShape decoded = roundTrip(shape);
		assertInstanceOf(MergeBlockShape.class, decoded);
		assertSameBoxes(shape, decoded);
	}

	private static BlockShape roundTrip(
		BlockShape shape
	) {
		ByteBuf buffer = Unpooled.buffer();
		try {
			BlockShape.STREAM_CODEC.encode(buffer, shape);
			return BlockShape.STREAM_CODEC.decode(buffer);
		} finally {
			buffer.release();
		}
	}

	private static void assertSameBoxes(
		BlockShape expected, BlockShape actual
	) {
		assertEquals(expected.elementaryBoxes(), actual.elementaryBoxes());
	}
}
