package de.jpx3.intave.block.shape;

import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Position;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.share.Direction.Axis.*;
import static org.junit.jupiter.api.Assertions.*;

final class VoxelShapeTest {
	private static final double EPSILON = 1.0E-7;
	private static final double SAMPLE_BOX_RADIUS = 1.0E-5;
	private static final int GRID_X = 25;
	private static final int GRID_Y = 63;
	private static final int GRID_Z = 15;
	private static final int[] REPRESENTATIVE_MASKS = {
		0x01, 0x02, 0x03, 0x05, 0x0F, 0x11, 0x33, 0x55, 0x66, 0x81, 0x99, 0xAA, 0xF0, 0x7E, 0xFE, 0xFF
	};

	@Test
	void originBoxExposesBoundsAndElementaryBox() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.25, 0.0, 0.5, 0.75, 1.0, 1.0);

		assertEquals(0.25, shape.min(X_AXIS), EPSILON);
		assertEquals(0.75, shape.max(X_AXIS), EPSILON);
		assertEquals(0.0, shape.min(Y_AXIS), EPSILON);
		assertEquals(1.0, shape.max(Y_AXIS), EPSILON);
		assertEquals(0.5, shape.min(Z_AXIS), EPSILON);
		assertEquals(1.0, shape.max(Z_AXIS), EPSILON);
		assertEquals(
			Collections.singletonList(BoundingBox.fromBounds(0.25, 0.0, 0.5, 0.75, 1.0, 1.0)),
			shape.elementaryBoxes()
		);
		assertEquals(BoundingBox.fromBounds(0.25, 0.0, 0.5, 0.75, 1.0, 1.0), shape.outline());
		assertFalse(shape.isEmpty());
		assertFalse(shape.isCubic());
	}

	@Test
	void fullOriginCubeIsCubic() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

		assertTrue(shape.isCubic());
		assertEquals(1.0, shape.max(X_AXIS), EPSILON);
		assertEquals(1.0, shape.max(Y_AXIS), EPSILON);
		assertEquals(1.0, shape.max(Z_AXIS), EPSILON);
	}

	@Test
	void contextualizedShapeTranslatesBoundsAndBoxes() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.25, 0.0, 0.5, 0.75, 1.0, 1.0)
			.contextualized(3, 4, 5);

		assertFalse(shape.isOriginShape());

		assertEquals(3.25, shape.min(X_AXIS), EPSILON);
		assertEquals(3.75, shape.max(X_AXIS), EPSILON);
		assertEquals(4.0, shape.min(Y_AXIS), EPSILON);
		assertEquals(5.0, shape.max(Y_AXIS), EPSILON);
		assertEquals(5.5, shape.min(Z_AXIS), EPSILON);
		assertEquals(6.0, shape.max(Z_AXIS), EPSILON);
		assertEquals(
			Collections.singletonList(BoundingBox.fromBounds(3.25, 4.0, 5.5, 3.75, 5.0, 6.0)),
			shape.elementaryBoxes()
		);

		BlockShape normalized = shape.normalized(3, 4, 5);
		assertInstanceOf(VoxelShape.class, normalized);
		assertTrue(((VoxelShape) normalized).isOriginShape());
		assertEquals(
			Collections.singletonList(BoundingBox.fromBounds(0.25, 0.0, 0.5, 0.75, 1.0, 1.0)),
			normalized.elementaryBoxes()
		);
	}

	@Test
	void booleanOperationsProduceExpectedVolumes() {
		VoxelShape leftHalf = VoxelShape.fromOriginBox(0.0, 0.0, 0.0, 0.5, 1.0, 1.0);
		VoxelShape rightHalf = VoxelShape.fromOriginBox(0.5, 0.0, 0.0, 1.0, 1.0, 1.0);
		VoxelShape full = leftHalf.combineWith(rightHalf).optimized();

		assertTrue(full.isCubic());
		assertEquals(
			Collections.singletonList(BoundingBox.fromBounds(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)),
			full.elementaryBoxes()
		);
		assertEquals(
			Collections.singletonList(BoundingBox.fromBounds(0.0, 0.0, 0.0, 0.5, 1.0, 1.0)),
			full.subtract(rightHalf).optimized().elementaryBoxes()
		);
		assertEquals(
			Collections.singletonList(BoundingBox.fromBounds(0.5, 0.0, 0.0, 1.0, 1.0, 1.0)),
			full.intersectWith(rightHalf).optimized().elementaryBoxes()
		);
	}

	@Test
	void intersectsWithDoesNotTreatEmptySectorsAsFilled() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.0, 0.0, 0.5, 1.0, 1.0, 1.0);

		assertFalse(shape.intersectsWith(BoundingBox.fromBounds(0.25, 0.25, 0.1, 0.75, 0.75, 0.4)));
		assertTrue(shape.intersectsWith(BoundingBox.fromBounds(0.25, 0.25, 0.75, 0.75, 0.75, 0.9)));
	}

	@Test
	void allowedOffsetClampsAgainstFilledVoxel() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
		BoundingBox leftEntity = BoundingBox.fromBounds(-0.5, 0.25, 0.25, -0.25, 0.75, 0.75);
		BoundingBox rightEntity = BoundingBox.fromBounds(1.25, 0.25, 0.25, 1.5, 0.75, 0.75);

		assertEquals(0.25, shape.allowedOffset(X_AXIS, leftEntity, 1.0), EPSILON);
		assertEquals(-0.25, shape.allowedOffset(X_AXIS, rightEntity, -1.0), EPSILON);
		assertEquals(1.0, shape.allowedOffset(X_AXIS, leftEntity.offset(0.0, 1.5, 0.0), 1.0), EPSILON);
	}

	@Test
	void raytraceReturnsClosestHit() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.25, 0.25, 0.25, 0.75, 0.75, 0.75);

		BlockRaytrace hit = shape.raytrace(new Position(-1.0, 0.5, 0.5), new Position(1.0, 0.5, 0.5));

		assertNotNull(hit);
		assertEquals(Direction.WEST, hit.direction());
		assertEquals(1.25, hit.lengthOffset(), EPSILON);
		assertNull(shape.raytrace(new Position(-1.0, 0.1, 0.1), new Position(1.0, 0.1, 0.1)));
	}

	@Test
	void streamCodecRoundTripsMultipleBoxes() {
		VoxelShape shape = VoxelShape.fromBoxes(Arrays.asList(
			BoundingBox.fromBounds(0.0, 0.0, 0.0, 0.25, 0.5, 1.0),
			BoundingBox.fromBounds(0.75, 0.5, 0.0, 1.0, 1.0, 1.0)
		));

		VoxelShape decoded = roundTrip(shape);

		assertEquals(shape.elementaryBoxes(), decoded.elementaryBoxes());
		assertEquals(shape.outline(), decoded.outline());
	}

	@Test
	void testContextualizedMerge() {
		List<BlockShape> cubes = List.of(
			BlockShapes.cubeAt(25, 63, 15),
			BlockShapes.cubeAt(25, 63, 16),
			BlockShapes.cubeAt(26, 63, 15),
			BlockShapes.cubeAt(26, 63, 16)
		);
		VoxelShape voxelShape = VoxelShape.fromBoxes(cubes.stream().map(BlockShape::elementaryBoxes).flatMap(List::stream).toList());
		List<BoundingBox> boxes = voxelShape.optimized().elementaryBoxes();
		assertEquals(1, boxes.size());
		assertEquals(BoundingBox.fromBounds(25.0, 63.0, 15.0, 27.0, 64.0, 17.0), boxes.get(0));
	}

	@Test
	void absorbsModernAnvilShapeFacingXAxis() {
		List<BoundingBox> anvilBoxes = modernAnvilBoxesFacingXAxis();
		VoxelShape shape = VoxelShape.fromBoxes(anvilBoxes);
		VoxelShape optimized = shape.optimized();

		assertSameOccupancyAsBoxes(optimized, anvilBoxes);
		assertEquals(BoundingBox.fromBounds(0.0, 0.0, 2.0 / 16.0, 1.0, 1.0, 14.0 / 16.0), optimized.outline());
		assertModernAnvilSamples(optimized, anvilBoxes);
		assertFalse(intersectsSample(optimized, 1.0 / 16.0, 2.0 / 16.0, 1.0 / 16.0));
		assertFalse(intersectsSample(optimized, 2.5 / 16.0, 4.5 / 16.0, 3.5 / 16.0));
		assertFalse(intersectsSample(optimized, 3.5 / 16.0, 7.0 / 16.0, 5.5 / 16.0));
		assertFalse(intersectsSample(optimized, 0.5, 12.0 / 16.0, 14.5 / 16.0));
	}

	@Test
	void absorbsModernAnvilShapeFacingZAxis() {
		List<BoundingBox> anvilBoxes = modernAnvilBoxesFacingZAxis();
		VoxelShape shape = VoxelShape.fromBoxes(anvilBoxes);
		VoxelShape optimized = shape.optimized();

		assertSameOccupancyAsBoxes(optimized, anvilBoxes);
		assertEquals(BoundingBox.fromBounds(2.0 / 16.0, 0.0, 0.0, 14.0 / 16.0, 1.0, 1.0), optimized.outline());
		assertModernAnvilSamples(optimized, anvilBoxes);
		assertFalse(intersectsSample(optimized, 1.0 / 16.0, 2.0 / 16.0, 1.0 / 16.0));
		assertFalse(intersectsSample(optimized, 3.5 / 16.0, 4.5 / 16.0, 2.5 / 16.0));
		assertFalse(intersectsSample(optimized, 5.5 / 16.0, 7.0 / 16.0, 3.5 / 16.0));
		assertFalse(intersectsSample(optimized, 14.5 / 16.0, 12.0 / 16.0, 0.5));
	}

	@Test
	void everyTwoByTwoByTwoMaskPreservesOccupiedCellsWhenOptimizedAndEncoded() {
		for (int mask = 1; mask < 256; mask++) {
			List<BoundingBox> sourceBoxes = boxesForMask(mask);
			VoxelShape shape = VoxelShape.fromBoxes(sourceBoxes);
			VoxelShape optimized = shape.optimized();
			VoxelShape decoded = roundTrip(shape);

			assertMaskMatches("fromBoxes mask " + mask, mask, shape);
			assertMaskMatches("optimized mask " + mask, mask, optimized);
			assertMaskMatches("codec mask " + mask, mask, decoded);
			assertEquals(union(sourceBoxes), shape.outline(), "outline mask " + mask);
			assertEquals(union(sourceBoxes), optimized.outline(), "optimized outline mask " + mask);
			assertEquals(union(sourceBoxes), decoded.outline(), "codec outline mask " + mask);
		}
	}

	@Test
	void booleanOperationsMatchBitmaskReferenceForRepresentativeMasks() {
		for (int firstMask : REPRESENTATIVE_MASKS) {
			VoxelShape first = VoxelShape.fromBoxes(boxesForMask(firstMask));
			for (int secondMask : REPRESENTATIVE_MASKS) {
				VoxelShape second = VoxelShape.fromBoxes(boxesForMask(secondMask));

				assertMaskMatches(
					"combine " + firstMask + " | " + secondMask,
					firstMask | secondMask,
					first.combineWith(second)
				);
				assertMaskMatches(
					"subtract " + firstMask + " - " + secondMask,
					firstMask & ~secondMask,
					first.subtract(second)
				);
				assertMaskMatches(
					"optimized subtract " + firstMask + " - " + secondMask,
					firstMask & ~secondMask,
					first.subtract(second).optimized()
				);
				assertMaskMatches(
					"intersect " + firstMask + " & " + secondMask,
					firstMask & secondMask,
					first.intersectWith(second)
				);
				assertMaskMatches(
					"optimized intersect " + firstMask + " & " + secondMask,
					firstMask & secondMask,
					first.intersectWith(second).optimized()
				);
			}
		}
	}

	@Test
	void emptyBooleanResultsRemainUsableAndRoundTrip() {
		VoxelShape cube = VoxelShape.fromOriginBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
		VoxelShape empty = cube.subtract(cube);
		VoxelShape gridCell = VoxelShape.fromBoxes(boxesForMask(0x01));
		VoxelShape emptyGridCell = gridCell.subtract(gridCell);

		assertTrue(empty.isEmpty());
		assertTrue(empty.elementaryBoxes().isEmpty());
		assertEquals(BoundingBox.empty(), empty.outline());
		assertTrue(empty.optimized().isEmpty());
		assertTrue(roundTrip(empty).isEmpty());
		assertMaskMatches("empty combines on left", 0x01, emptyGridCell.combineWith(gridCell));
		assertMaskMatches("empty combines on right", 0x01, gridCell.combineWith(emptyGridCell));
		assertMaskMatches("subtracting empty preserves shape", 0x01, gridCell.subtract(emptyGridCell));
		assertMaskMatches("empty intersection stays empty", 0, emptyGridCell.intersectWith(gridCell));
	}

	@Test
	void allowedOffsetMatchesElementaryBoxReferenceAcrossAxes() {
		int mask = 0x6D;
		List<BoundingBox> sourceBoxes = boxesForMask(mask);
		VoxelShape shape = VoxelShape.fromBoxes(sourceBoxes);
		List<BoundingBox> entities = Arrays.asList(
			BoundingBox.fromBounds(24.25, 63.25, 15.25, 24.75, 63.75, 15.75),
			BoundingBox.fromBounds(27.25, 63.25, 15.25, 27.75, 63.75, 15.75),
			BoundingBox.fromBounds(25.25, 62.25, 15.25, 25.75, 62.75, 15.75),
			BoundingBox.fromBounds(25.25, 65.25, 15.25, 25.75, 65.75, 15.75),
			BoundingBox.fromBounds(25.25, 63.25, 14.25, 25.75, 63.75, 14.75),
			BoundingBox.fromBounds(25.25, 63.25, 17.25, 25.75, 63.75, 17.75),
			BoundingBox.fromBounds(24.25, 62.25, 14.25, 24.75, 62.75, 14.75)
		);
		double[] offsets = {-2.0, -0.75, -0.1, 0.0, 0.1, 0.75, 2.0};

		for (Direction.Axis axis : values()) {
			for (BoundingBox entity : entities) {
				for (double offset : offsets) {
					assertEquals(
						expectedAllowedOffset(sourceBoxes, axis, entity, offset),
						shape.allowedOffset(axis, entity, offset),
						EPSILON,
						axis + " offset " + offset + " for " + entity
					);
				}
			}
		}
	}

	@Test
	void touchingBoundsDoNotIntersectButOverlapDoes() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

		assertFalse(shape.intersectsWith(BoundingBox.fromBounds(1.0, 0.0, 0.0, 2.0, 1.0, 1.0)));
		assertFalse(shape.intersectsWith(BoundingBox.fromBounds(0.0, 1.0, 0.0, 1.0, 2.0, 1.0)));
		assertFalse(shape.intersectsWith(BoundingBox.fromBounds(0.0, 0.0, 1.0, 1.0, 1.0, 2.0)));
		assertTrue(shape.intersectsWith(BoundingBox.fromBounds(1.0 - EPSILON, 0.0, 0.0, 2.0, 1.0, 1.0)));
		assertTrue(shape.intersectsWith(BoundingBox.fromBounds(0.0, 1.0 - EPSILON, 0.0, 1.0, 2.0, 1.0)));
		assertTrue(shape.intersectsWith(BoundingBox.fromBounds(0.0, 0.0, 1.0 - EPSILON, 1.0, 1.0, 2.0)));
	}

	@Test
	void rejectsEmptyOriginBoxes() {
		assertThrows(IllegalArgumentException.class, () -> VoxelShape.fromOriginBox(0.0, 0.0, 0.0, 0.0, 1.0, 1.0));
		assertThrows(IllegalArgumentException.class, () -> VoxelShape.fromOriginBox(0.0, 0.0, 0.0, 1.0, 0.0, 1.0));
		assertThrows(IllegalArgumentException.class, () -> VoxelShape.fromOriginBox(0.0, 0.0, 0.0, 1.0, 1.0, 0.0));
	}

	private static VoxelShape roundTrip(VoxelShape shape) {
		ByteBuf buffer = Unpooled.buffer();
		try {
			VoxelShape.STREAM_CODEC.encode(buffer, shape);
			return VoxelShape.STREAM_CODEC.decode(buffer);
		} finally {
			buffer.release();
		}
	}

	private static List<BoundingBox> boxesForMask(int mask) {
		List<BoundingBox> boxes = new ArrayList<>();
		for (int x = 0; x < 2; x++) {
			for (int y = 0; y < 2; y++) {
				for (int z = 0; z < 2; z++) {
					if ((mask & bitOf(x, y, z)) != 0) {
						boxes.add(BoundingBox.fromBounds(
							GRID_X + x, GRID_Y + y, GRID_Z + z,
							GRID_X + x + 1.0, GRID_Y + y + 1.0, GRID_Z + z + 1.0
						));
					}
				}
			}
		}
		return boxes;
	}

	private static int bitOf(int x, int y, int z) {
		return 1 << (x * 4 + y * 2 + z);
	}

	private static void assertMaskMatches(String message, int expectedMask, VoxelShape shape) {
		if (expectedMask == 0) {
			assertTrue(shape.isEmpty(), message + " should be empty");
			assertTrue(shape.elementaryBoxes().isEmpty(), message + " should expose no boxes");
		} else {
			assertFalse(shape.isEmpty(), message + " should not be empty");
		}
		double[] sampleOffsets = {-0.5, 0.5, 1.5, 2.5};
		for (int sampleX = 0; sampleX < sampleOffsets.length; sampleX++) {
			for (int sampleY = 0; sampleY < sampleOffsets.length; sampleY++) {
				for (int sampleZ = 0; sampleZ < sampleOffsets.length; sampleZ++) {
					int cellX = sampleX - 1;
					int cellY = sampleY - 1;
					int cellZ = sampleZ - 1;
					boolean expected = cellX >= 0 && cellX < 2 &&
						cellY >= 0 && cellY < 2 &&
						cellZ >= 0 && cellZ < 2 &&
						(expectedMask & bitOf(cellX, cellY, cellZ)) != 0;
					assertEquals(
						expected,
						intersectsSample(shape, GRID_X + sampleOffsets[sampleX], GRID_Y + sampleOffsets[sampleY], GRID_Z + sampleOffsets[sampleZ]),
						message + " at sample " + cellX + "," + cellY + "," + cellZ
					);
				}
			}
		}
	}

	private static boolean intersectsSample(VoxelShape shape, double x, double y, double z) {
		return shape.intersectsWith(BoundingBox.fromBounds(
			x - SAMPLE_BOX_RADIUS, y - SAMPLE_BOX_RADIUS, z - SAMPLE_BOX_RADIUS,
			x + SAMPLE_BOX_RADIUS, y + SAMPLE_BOX_RADIUS, z + SAMPLE_BOX_RADIUS
		));
	}

	private static BoundingBox union(List<BoundingBox> boxes) {
		BoundingBox union = boxes.getFirst();
		for (int i = 1; i < boxes.size(); i++) {
			union = union.union(boxes.get(i));
		}
		return union;
	}

	private static double expectedAllowedOffset(
		List<BoundingBox> boxes,
		Direction.Axis axis,
		BoundingBox entity,
		double offset
	) {
		double result = offset;
		for (BoundingBox box : boxes) {
			result = box.allowedOffset(axis, entity, result);
		}
		return Math.abs(result) < EPSILON ? 0.0 : result;
	}

	private static List<BoundingBox> modernAnvilBoxesFacingXAxis() {
		return Arrays.asList(
			BoundingBox.originFromX16(2.0, 0.0, 2.0, 14.0, 4.0, 14.0),
			BoundingBox.originFromX16(3.0, 4.0, 4.0, 13.0, 5.0, 12.0),
			BoundingBox.originFromX16(4.0, 5.0, 6.0, 12.0, 10.0, 10.0),
			BoundingBox.originFromX16(0.0, 10.0, 3.0, 16.0, 16.0, 13.0)
		);
	}

	private static List<BoundingBox> modernAnvilBoxesFacingZAxis() {
		return Arrays.asList(
			BoundingBox.originFromX16(2.0, 0.0, 2.0, 14.0, 4.0, 14.0),
			BoundingBox.originFromX16(4.0, 4.0, 3.0, 12.0, 5.0, 13.0),
			BoundingBox.originFromX16(6.0, 5.0, 4.0, 10.0, 10.0, 12.0),
			BoundingBox.originFromX16(3.0, 10.0, 0.0, 13.0, 16.0, 16.0)
		);
	}

	private static void assertModernAnvilSamples(VoxelShape shape, List<BoundingBox> expectedBoxes) {
		for (BoundingBox box : expectedBoxes) {
			assertTrue(intersectsSample(
				shape,
				(box.minX + box.maxX) / 2.0,
				(box.minY + box.maxY) / 2.0,
				(box.minZ + box.maxZ) / 2.0
			));
		}
	}

	private static void assertSameOccupancyAsBoxes(VoxelShape shape, List<BoundingBox> expectedBoxes) {
		double[] xSamples = axisSamples(expectedBoxes, X_AXIS);
		double[] ySamples = axisSamples(expectedBoxes, Y_AXIS);
		double[] zSamples = axisSamples(expectedBoxes, Z_AXIS);
		for (double x : xSamples) {
			for (double y : ySamples) {
				for (double z : zSamples) {
					assertEquals(
						containsAny(expectedBoxes, x, y, z),
						intersectsSample(shape, x, y, z),
						"sample " + x + "," + y + "," + z
					);
				}
			}
		}
	}

	private static double[] axisSamples(List<BoundingBox> boxes, Direction.Axis axis) {
		List<Double> bounds = new ArrayList<>();
		addUnique(bounds, 0.0);
		addUnique(bounds, 1.0);
		for (BoundingBox box : boxes) {
			addUnique(bounds, box.min(axis));
			addUnique(bounds, box.max(axis));
		}
		Collections.sort(bounds);
		double[] samples = new double[bounds.size() + 1];
		samples[0] = bounds.getFirst() - 1.0 / 32.0;
		for (int i = 1; i < bounds.size(); i++) {
			samples[i] = (bounds.get(i - 1) + bounds.get(i)) / 2.0;
		}
		samples[samples.length - 1] = bounds.getLast() + 1.0 / 32.0;
		return samples;
	}

	private static void addUnique(List<Double> values, double value) {
		for (double existing : values) {
			if (Math.abs(existing - value) < EPSILON) {
				return;
			}
		}
		values.add(value);
	}

	private static boolean containsAny(List<BoundingBox> boxes, double x, double y, double z) {
		for (BoundingBox box : boxes) {
			if (box.contains(x, y, z)) {
				return true;
			}
		}
		return false;
	}
}
