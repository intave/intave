package de.jpx3.intave.block.shape;

import de.jpx3.intave.block.shape.voxel.IndexMerger;
import de.jpx3.intave.block.shape.voxel.NonOverlappingGridMerger;
import de.jpx3.intave.block.shape.voxel.OverlappingGridMerger;
import de.jpx3.intave.block.shape.voxel.SameIndexMerger;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.*;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.doubles.DoubleSet;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class VoxelShape implements BlockShape {
	private static final BitSet FILLED_BLOCK_BITSET = new BitSet(1);

	static {
		FILLED_BLOCK_BITSET.set(0);
	}

	public static final StreamCodec<ByteBuf, ByteBuf, VoxelShape> STREAM_CODEC = BoundingBox.LIST_STREAM_CODEC.beforeAndAfter(
		VoxelShape::fromBoxes, VoxelShape::elementaryBoxes
	);

	private static final double[] EMPTY_SHAPE_OFFSETS = {0.0D, 0.0D};

	private final BitSet sectorBits;

	private final int xSectors;
	private final int ySectors;
	private final int zSectors;

	private int xFirstFilledSector, yFirstFilledSector, zFirstFilledSector;
	private int xLastFilledSector, yLastFilledSector, zLastFilledSector;

	private final double[] xSectorOffsets;
	private final double[] ySectorOffsets;
	private final double[] zSectorOffsets;

	private final int positionX;
	private final int positionY;
	private final int positionZ;

	private final boolean normalized;
	private final boolean immutable;

	private VoxelShape(
		double minX, double minY, double minZ,
		double maxX, double maxY, double maxZ
	) {
		xSectors = ySectors = zSectors = 1;
		xSectorOffsets = new double[]{minX, maxX};
		ySectorOffsets = new double[]{minY, maxY};
		zSectorOffsets = new double[]{minZ, maxZ};
		immutable = true;
		normalized = true;
		sectorBits = FILLED_BLOCK_BITSET;
		positionX = positionY = positionZ = 0;
		xFirstFilledSector = yFirstFilledSector = zFirstFilledSector = 0;
		xLastFilledSector = yLastFilledSector = zLastFilledSector = 1;
	}

	private VoxelShape() {
		xSectors = ySectors = zSectors = 1;
		xSectorOffsets = EMPTY_SHAPE_OFFSETS;
		ySectorOffsets = EMPTY_SHAPE_OFFSETS;
		zSectorOffsets = EMPTY_SHAPE_OFFSETS;
		immutable = true;
		normalized = true;
		sectorBits = new BitSet();
		positionX = positionY = positionZ = 0;
		xFirstFilledSector = yFirstFilledSector = zFirstFilledSector = 0;
		xLastFilledSector = yLastFilledSector = zLastFilledSector = 0;
	}

	private VoxelShape(
		VoxelShape copyFrom,
		int positionX, int positionY, int positionZ,
		boolean normalized
	) {
		this.xSectors = copyFrom.xSectors;
		this.ySectors = copyFrom.ySectors;
		this.zSectors = copyFrom.zSectors;
		this.xSectorOffsets = copyFrom.xSectorOffsets;
		this.ySectorOffsets = copyFrom.ySectorOffsets;
		this.zSectorOffsets = copyFrom.zSectorOffsets;
		this.sectorBits = copyFrom.sectorBits;
		this.positionX = positionX;
		this.positionY = positionY;
		this.positionZ = positionZ;
		this.immutable = true;
		this.normalized = normalized;
		this.xFirstFilledSector = copyFrom.xFirstFilledSector;
		this.yFirstFilledSector = copyFrom.yFirstFilledSector;
		this.zFirstFilledSector = copyFrom.zFirstFilledSector;
		this.xLastFilledSector = copyFrom.xLastFilledSector;
		this.yLastFilledSector = copyFrom.yLastFilledSector;
		this.zLastFilledSector = copyFrom.zLastFilledSector;
	}

	private VoxelShape(
		BitSet sectorBits,
		double[] xSectorOffsets, double[] ySectorOffsets, double[] zSectorOffsets,
		int[] minBounds, int[] maxBounds,
		boolean normalized
	) {
		this.sectorBits = sectorBits;
		this.xSectorOffsets = xSectorOffsets;
		this.ySectorOffsets = ySectorOffsets;
		this.zSectorOffsets = zSectorOffsets;
		this.xSectors = xSectorOffsets.length - 1;
		this.ySectors = ySectorOffsets.length - 1;
		this.zSectors = zSectorOffsets.length - 1;
		this.xFirstFilledSector = minBounds[0];
		this.yFirstFilledSector = minBounds[1];
		this.zFirstFilledSector = minBounds[2];
		this.xLastFilledSector = maxBounds[0];
		this.yLastFilledSector = maxBounds[1];
		this.zLastFilledSector = maxBounds[2];
		this.normalized = normalized;
		this.positionX = this.positionY = this.positionZ = 0;
		this.immutable = true;
	}

	private void fill(int xSector, int ySector, int zSector) {
		if (immutable) {
			throw new UnsupportedOperationException("Cannot modify immutable voxel-shape");
		}
		this.sectorBits.set(indexOf(xSector, ySector, zSector));
		// Update first and last filled sectors
		this.xFirstFilledSector = Math.min(this.xFirstFilledSector, xSector);
		this.yFirstFilledSector = Math.min(this.yFirstFilledSector, ySector);
		this.zFirstFilledSector = Math.min(this.zFirstFilledSector, zSector);
		this.xLastFilledSector = Math.max(this.xLastFilledSector, xSector + 1);
		this.yLastFilledSector = Math.max(this.yLastFilledSector, ySector + 1);
		this.zLastFilledSector = Math.max(this.zLastFilledSector, zSector + 1);
	}

	private boolean isSet(int xSector, int ySector, int zSector) {
		return this.sectorBits.get(indexOf(xSector, ySector, zSector));
	}

	private boolean isSet(BitSet sectorBits, int xSector, int ySector, int zSector) {
		return sectorBits.get(indexOf(xSector, ySector, zSector));
	}

	private boolean isSetAndInSector(AxisRotation rotation, int xSector, int ySector, int zSector) {
		return isSetAndInSector(
			rotation.cycle(xSector, ySector, zSector, Direction.Axis.X_AXIS),
			rotation.cycle(xSector, ySector, zSector, Direction.Axis.Y_AXIS),
			rotation.cycle(xSector, ySector, zSector, Direction.Axis.Z_AXIS)
		);
	}

	private boolean isSetAndInSector(AxisRotation rotation, BitSet sectorBits, int xSector, int ySector, int zSector) {
		return isSetAndInSector(
			sectorBits,
			rotation.cycle(xSector, ySector, zSector, Direction.Axis.X_AXIS),
			rotation.cycle(xSector, ySector, zSector, Direction.Axis.Y_AXIS),
			rotation.cycle(xSector, ySector, zSector, Direction.Axis.Z_AXIS)
		);
	}

	private boolean isSetAndInSector(int xSector, int ySector, int zSector) {
		return xSector >= 0 && xSector < this.xSectors &&
			ySector >= 0 && ySector < this.ySectors &&
			zSector >= 0 && zSector < this.zSectors &&
			isSet(xSector, ySector, zSector);
	}

	private boolean isSetAndInSector(BitSet sectorBits, int xSector, int ySector, int zSector) {
		return xSector >= 0 && xSector < this.xSectors &&
			ySector >= 0 && ySector < this.ySectors &&
			zSector >= 0 && zSector < this.zSectors &&
			isSet(sectorBits, xSector, ySector, zSector);
	}

	private boolean zLineFilled(BitSet sectorBits, int zStart, int zEnd, int xSector, int ySector) {
		if (xSector < 0 || xSector >= this.xSectors || ySector < 0 || ySector >= this.ySectors) {
			return false;
		}
		return sectorBits.nextClearBit(indexOf(xSector, ySector, zStart)) >= indexOf(xSector, ySector, zEnd);
	}

	private boolean xzRectangleFilled(BitSet sectorBits, int xStart, int xEnd, int zStart, int zEnd, int ySector) {
		if (ySector < 0 || ySector >= this.ySectors) {
			return false;
		}
		for (int xSector = xStart; xSector < xEnd; xSector++) {
			if (!zLineFilled(sectorBits, zStart, zEnd, xSector, ySector)) {
				return false;
			}
		}
		return true;
	}

	private void clearZLine(BitSet sectorBits, int zStart, int zEnd, int xSector, int ySector) {
		if (xSector < 0 || xSector >= this.xSectors || ySector < 0 || ySector >= this.ySectors) {
			return;
		}
		sectorBits.clear(indexOf(xSector, ySector, zStart), indexOf(xSector, ySector, zEnd));
	}

	public int indexOf(int xSector, int ySector, int zSector) {
		return xSector * this.ySectors * this.zSectors + ySector * this.zSectors + zSector;
	}

	@Override
	public double allowedOffset(Direction.Axis axis, BoundingBox entity, double offset) {
		AxisRotation differential = AxisRotations.inverse(AxisRotation.differential(axis, Direction.Axis.X_AXIS));
		double output = allowedOffsetX(differential, entity, offset);
		if (Math.abs(output) < EPSILON) {
			output = 0;
		}
		return output;
	}

	private boolean isLooselyInBounds(Direction.Axis axis, double value) {
		return max(axis) + EPSILON >= value && min(axis) - EPSILON <= value;
	}

	private static final double EPSILON = 0.0000001;

	// Looks scary, but it's easy to understand and quite fast
	private double allowedOffsetX(AxisRotation rotation, BoundingBox entity, double offset) {
		if (Math.abs(offset) < EPSILON) {
			return 0;
		}
		Direction.Axis projectionXAxis = rotation.cycle(Direction.Axis.X_AXIS);
		Direction.Axis projectionYAxis = rotation.cycle(Direction.Axis.Y_AXIS);
		Direction.Axis projectionZAxis = rotation.cycle(Direction.Axis.Z_AXIS);
		int startX = findIndex(projectionXAxis, entity.min(projectionXAxis) + EPSILON);
		int endX = findIndex(projectionXAxis, entity.max(projectionXAxis) - EPSILON);
		int startY = Math.max(0, findIndex(projectionYAxis, entity.min(projectionYAxis) + EPSILON));
		int endY = Math.min(sectorsOf(projectionYAxis), findIndex(projectionYAxis, entity.max(projectionYAxis) - EPSILON) + 1);
		int startZ = Math.max(0, findIndex(projectionZAxis, entity.min(projectionZAxis) + EPSILON));
		int endZ = Math.min(sectorsOf(projectionZAxis), findIndex(projectionZAxis, entity.max(projectionZAxis) - EPSILON) + 1);
		if (offset > 0) {
			for (int xSector = endX + 1; xSector < xSectors; xSector++) {
				// sector iteration size of these axis is one in 90% of cases
				for (int ySector = startY; ySector < endY; ySector++) {
					for (int zSector = startZ; zSector < endZ; zSector++) {
						if (isSetAndInSector(rotation, xSector, ySector, zSector)) {
							double calculatedOffset = positionOf(projectionXAxis, xSector) - entity.max(projectionXAxis);
							if (calculatedOffset >= -EPSILON) {
								offset = Math.min(offset, calculatedOffset);
							}
							return offset;
						}
					}
				}
			}
		} else {
			for (int xSector = startX - 1; xSector >= 0; xSector--) {
				for (int ySector = startY; ySector < endY; ySector++) {
					for (int zSector = startZ; zSector < endZ; zSector++) {
						if (isSetAndInSector(rotation, xSector, ySector, zSector)) {
							double calculatedOffset = positionOf(projectionXAxis, xSector + 1) - entity.min(projectionXAxis);
							if (calculatedOffset <= EPSILON) {
								offset = Math.max(offset, calculatedOffset);
							}
							return offset;
						}
					}
				}
			}
		}
		return offset;
	}

	private int findIndex(Direction.Axis axis, double value) {
		// Binary search for the last sector with a value less than the given value
		int start = 0;
		int diff = sectorsOf(axis) + 1;
		while (diff > 0) {
			int mid = diff / 2;
			int index = start + mid;
			if (value < positionOf(axis, index)) {
				diff = mid;
			} else {
				start = index + 1;
				diff -= mid + 1;
			}
		}
		return start - 1;
	}

	@Override
	public double min(Direction.Axis axis) {
		return positionOf(axis, minSector(axis));
	}

	@Override
	public double max(Direction.Axis axis) {
		return positionOf(axis, maxSector(axis));
	}

	@Override
	public boolean intersectsWith(BoundingBox boundingBox) {
		AtomicBoolean intersects = new AtomicBoolean(false);
		forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			boolean intersectsWith = boundingBox.intersectsWith(minX, minY, minZ, maxX, maxY, maxZ);
			if (intersectsWith) {
				intersects.set(true);
				return false;
			}
			return true;
		}, false);
		return intersects.get();
	}

	@Override
	public VoxelShape contextualized(int xTranslation, int yTranslation, int zTranslation) {
		return new VoxelShape(this, xTranslation, yTranslation, zTranslation, false);
	}

	@Override
	public BlockShape normalized(int posX, int posY, int posZ) {
		if (isOriginShape()) {
			return this;
		}
		return new VoxelShape(this, 0, 0, 0, true);
	}

	public boolean isOriginShape() {
		return this.normalized;
	}

	@Override
	public void appendUnsortedCoordsTo(Direction.Axis axis, DoubleSet appendTo) {
		switch (axis) {
			case X_AXIS:
				for (double xSectorOffset : this.xSectorOffsets) {
					appendTo.add(xSectorOffset + positionX);
				}
				break;
			case Y_AXIS:
				for (double ySectorOffset : this.ySectorOffsets) {
					appendTo.add(ySectorOffset + positionY);
				}
				break;
			case Z_AXIS:
				for (double zSectorOffset : this.zSectorOffsets) {
					appendTo.add(zSectorOffset + positionZ);
				}
				break;
		}
	}

	@Override
	public BlockRaytrace raytrace(Position origin, Position target) {
		BlockRaytrace raytrace = BlockRaytrace.none();
		for (BoundingBox boundingBox : elementaryBoxes()) {
			BlockRaytrace newRaytrace = boundingBox.raytrace(origin, target);
			if (raytrace == null) {
				raytrace = newRaytrace;
			} else if (newRaytrace != null) {
				raytrace = raytrace.minSelect(newRaytrace);
			}
		}
		return raytrace;
	}

	@Override
	public BoundingBox outline() {
		return collectBoxes(Collectors.reducing(BoundingBox::union)).orElse(BoundingBox.empty());
	}

	@Override
	public List<BoundingBox> elementaryBoxes() {
		return collectBoxes(Collectors.toList());
	}

	public <C, R> R collectBoxes(
		Collector<? super BoundingBox, C, R> collector
	) {
		Supplier<C> supplier = collector.supplier();
		C container = supplier.get();
		forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			BoundingBox boundingBox = BoundingBox.fromBounds(minX, minY, minZ, maxX, maxY, maxZ);
			if (isOriginShape()) {
				boundingBox.makeOriginBox();
			}
			collector.accumulator().accept(container, boundingBox);
			return true;
		}, true);
		return collector.finisher().apply(container);
	}

	// cursed code
	private void forAllBoxes(ShapeConsumer shapeConsumer, boolean merge) {
		BitSet sectorCopy = (BitSet) this.sectorBits.clone();
		for (int ySector = 0; ySector < ySectors; ySector++) {
			for (int xSector = 0; xSector < xSectors; xSector++) {
				int zStart = -1;
				for (int zSector = 0; zSector <= zSectors; zSector++) {
					if (isSetAndInSector(sectorCopy, xSector, ySector, zSector)) {
						if (!merge) {
							boolean cont = shapeConsumer.accept(
								positionOf(Direction.Axis.X_AXIS, xSector),
								positionOf(Direction.Axis.Y_AXIS, ySector),
								positionOf(Direction.Axis.Z_AXIS, zSector),
								positionOf(Direction.Axis.X_AXIS, xSector + 1),
								positionOf(Direction.Axis.Y_AXIS, ySector + 1),
								positionOf(Direction.Axis.Z_AXIS, zSector + 1)
							);
							if (!cont) {
								return;
							}
						} else {
							if (zStart == -1) {
								zStart = zSector;
							}
						}
					} else if (zStart != -1) {
						int xEnd = xSector;
						int yEnd = ySector;
						clearZLine(sectorCopy, zStart, zSector, xSector, ySector);
						while (zLineFilled(sectorCopy, zStart, zSector, xEnd + 1, ySector)) {
							clearZLine(sectorCopy, zStart, zSector, xEnd + 1, ySector);
							xEnd++;
						}
						while (xzRectangleFilled(sectorCopy, xSector, xEnd + 1, zStart, zSector, yEnd + 1)) {
							for (int x = xSector; x <= xEnd; x++) {
								clearZLine(sectorCopy, zStart, zSector, x, yEnd + 1);
							}
							yEnd++;
						}
						boolean cont = shapeConsumer.accept(
							positionOf(Direction.Axis.X_AXIS, xSector),
							positionOf(Direction.Axis.Y_AXIS, ySector),
							positionOf(Direction.Axis.Z_AXIS, zStart),
							positionOf(Direction.Axis.X_AXIS, xEnd + 1),
							positionOf(Direction.Axis.Y_AXIS, yEnd + 1),
							positionOf(Direction.Axis.Z_AXIS, zSector)
						);
						if (!cont) {
							return;
						}
						zStart = -1;
					}
				}
			}
		}
	}

	private interface ShapeConsumer {
		boolean accept(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
	}

	@Override
	public boolean isEmpty() {
		return this.sectorBits.isEmpty();
	}

	@Override
	public boolean isCubic() {
		return xSectors == 1 && ySectors == 1 && zSectors == 1 && this.sectorBits.cardinality() == 1 &&
			Math.abs(xSectorOffsets[0]) < EPSILON && Math.abs(ySectorOffsets[0]) < EPSILON && Math.abs(zSectorOffsets[0]) < EPSILON &&
			Math.abs(xSectorOffsets[1] - 1.0D) < EPSILON && Math.abs(ySectorOffsets[1] - 1.0D) < EPSILON && Math.abs(zSectorOffsets[1] - 1.0D) < EPSILON;
	}

	private int maxSector(Direction.Axis axis) {
		switch (axis) {
			case X_AXIS:
				return this.xLastFilledSector;
			case Y_AXIS:
				return this.yLastFilledSector;
			case Z_AXIS:
				return this.zLastFilledSector;
		}
		return 0;
	}

	private int minSector(Direction.Axis axis) {
		switch (axis) {
			case X_AXIS:
				return this.xFirstFilledSector;
			case Y_AXIS:
				return this.yFirstFilledSector;
			case Z_AXIS:
				return this.zFirstFilledSector;
		}
		return 0;
	}

	private double positionOf(Direction.Axis axis, int index) {
		if (index < 0) {
			return 0;
		}
		switch (axis) {
			case X_AXIS:
				return this.xSectorOffsets[index] + positionX;
			case Y_AXIS:
				return this.ySectorOffsets[index] + positionY;
			case Z_AXIS:
				return this.zSectorOffsets[index] + positionZ;
		}
		return 0;
	}

	private int sectorsOf(Direction.Axis axis) {
		switch (axis) {
			case X_AXIS:
				return this.xSectors;
			case Y_AXIS:
				return this.ySectors;
			case Z_AXIS:
				return this.zSectors;
		}
		return 0;
	}

	private double[] sectorOffsetsOf(Direction.Axis axis) {
		switch (axis) {
			case X_AXIS:
				return this.xSectorOffsets;
			case Y_AXIS:
				return this.ySectorOffsets;
			case Z_AXIS:
				return this.zSectorOffsets;
		}
		return new double[0];
	}

	private double[] absoluteSectorOffsetsOf(Direction.Axis axis) {
		double[] offsets = sectorOffsetsOf(axis);
		int translation = translationOf(axis);
		if (translation == 0) {
			return offsets;
		}
		double[] absoluteOffsets = Arrays.copyOf(offsets, offsets.length);
		for (int i = 0; i < absoluteOffsets.length; i++) {
			absoluteOffsets[i] += translation;
		}
		return absoluteOffsets;
	}

	private int translationOf(Direction.Axis axis) {
		switch (axis) {
			case X_AXIS:
				return this.positionX;
			case Y_AXIS:
				return this.positionY;
			case Z_AXIS:
				return this.positionZ;
		}
		return 0;
	}

	public VoxelShape combineWith(VoxelShape other) {
		return merge(this, other, (a, b) -> a || b);
	}

	public VoxelShape subtract(VoxelShape other) {
		return merge(this, other, (a, b) -> a && !b);
	}

	public VoxelShape intersectWith(VoxelShape other) {
		return merge(this, other, (a, b) -> a && b);
	}

	static VoxelShape merge(
		VoxelShape firstShape, VoxelShape secondShape, BinaryOperator<Boolean> mergeFunction
	) {
		if (mergeFunction.apply(false, false)) {
			throw new IllegalArgumentException("Uhm, what?");
		}
		boolean first = mergeFunction.apply(true, false);
		boolean second = mergeFunction.apply(false, true);
		IndexMerger xMerger = indexMergerOf(Direction.Axis.X_AXIS, firstShape, secondShape, first, second);
		IndexMerger yMerger = indexMergerOf(Direction.Axis.Y_AXIS, firstShape, secondShape, first, second);
		IndexMerger zMerger = indexMergerOf(Direction.Axis.Z_AXIS, firstShape, secondShape, first, second);
		return mergeSectors(firstShape, secondShape, xMerger, yMerger, zMerger, mergeFunction);
	}

	static VoxelShape mergeSectors(
		VoxelShape firstShape, VoxelShape secondShape,
		IndexMerger xMerger, IndexMerger yMerger, IndexMerger zMerger,
		BinaryOperator<Boolean> mergeFunction
	) {
		if (firstShape.isOriginShape() != secondShape.isOriginShape() &&
			!firstShape.isEmpty() && !secondShape.isEmpty()
		) {
			throw new IllegalArgumentException("Can't merge origin shape with non-origin shape.");
		}
		int xSectorSize = xMerger.size() - 1;
		int ySectorSize = yMerger.size() - 1;
		int zSectorSize = zMerger.size() - 1;
		BitSet mergedSectors = new BitSet(xSectorSize * ySectorSize * zSectorSize);
		int[] minBounds = new int[]{xSectorSize, ySectorSize, zSectorSize};
		int[] maxBounds = new int[3];
		boolean[] yMergeSuccess = new boolean[1];
		boolean[] zMergeSuccess = new boolean[1];
		xMerger.forMergedIndices((firstXIndex, secondXIndex, xIndex) -> {
			yMergeSuccess[0] = false;
			yMerger.forMergedIndices((firstYIndex, secondYIndex, yIndex) -> {
				zMergeSuccess[0] = false;
				zMerger.forMergedIndices((firstZIndex, secondZIndex, zIndex) -> {
					boolean first = firstShape.isSetAndInSector(firstXIndex, firstYIndex, firstZIndex);
					boolean second = secondShape.isSetAndInSector(secondXIndex, secondYIndex, secondZIndex);
					boolean merged = mergeFunction.apply(first, second);
					if (merged) {
						int thisIndex = xIndex * ySectorSize * zSectorSize + yIndex * zSectorSize + zIndex;
						mergedSectors.set(thisIndex);
						minBounds[2] = Math.min(minBounds[2], zIndex);
						maxBounds[2] = Math.max(maxBounds[2], zIndex + 1);
						zMergeSuccess[0] = true;
					}
					return true;
				});
				if (zMergeSuccess[0]) {
					minBounds[1] = Math.min(minBounds[1], yIndex);
					maxBounds[1] = Math.max(maxBounds[1], yIndex + 1);
					yMergeSuccess[0] = true;
				}
				return true;
			});
			if (yMergeSuccess[0]) {
				minBounds[0] = Math.min(minBounds[0], xIndex);
				maxBounds[0] = Math.max(maxBounds[0], xIndex + 1);
			}
			return true;
		});
		return new VoxelShape(
			mergedSectors,
			xMerger.mergedIndexes().toDoubleArray(),
			yMerger.mergedIndexes().toDoubleArray(),
			zMerger.mergedIndexes().toDoubleArray(),
			minBounds, maxBounds,
			firstShape.isEmpty() ? secondShape.isOriginShape() : firstShape.isOriginShape()
		);
	}

	static IndexMerger indexMergerOf(Direction.Axis axis, VoxelShape firstShape, VoxelShape secondShape, boolean first, boolean second) {
		double[] firstOffsets = firstShape.absoluteSectorOffsetsOf(axis);
		double[] secondOffsets = secondShape.absoluteSectorOffsetsOf(axis);
		int firstLastIndex = firstOffsets.length - 1;
		int secondLastIndex = secondOffsets.length - 1;
		if (firstOffsets[firstLastIndex] < secondOffsets[0] - EPSILON) {
			return new NonOverlappingGridMerger(firstOffsets, secondOffsets, false);
		} else if (secondOffsets[secondLastIndex] < firstOffsets[0] - EPSILON) {
			return new NonOverlappingGridMerger(secondOffsets, firstOffsets, true);
		} else if (firstLastIndex == secondLastIndex && Arrays.equals(firstOffsets, secondOffsets)) {
			return new SameIndexMerger(firstOffsets);
		} else {
			return new OverlappingGridMerger(firstOffsets, secondOffsets, first, second).compileMerge();
		}
	}

	static VoxelShape fromBoxes(List<? extends BoundingBox> boxes) {
		if (boxes.isEmpty()) {
			return empty();
		}
		VoxelShape shape = null;
		Boolean origin = null;
		for (BoundingBox box : boxes) {
			if (origin == null) {
				origin = box.isOriginBox();
			} else if (origin != box.isOriginBox()) {
				throw new IllegalArgumentException("Cannot mix origin and non-origin boxes");
			}
			VoxelShape boxShape = fromBox(box);
			if (shape == null) {
				shape = boxShape;
			} else {
				shape = shape.combineWith(boxShape);
			}
		}
		return shape;
	}

	static VoxelShape fromBox(BoundingBox shape) {
		if (!shape.isOriginBox()) {
			int originXGuess = (int) Math.floor((shape.minX + shape.maxX) / 2f);
			int originYGuess = (int) Math.floor((shape.minY + shape.maxY) / 2f);
			int originZGuess = (int) Math.floor((shape.minZ + shape.maxZ) / 2f);
			return new VoxelShape(
				shape.minX - originXGuess, shape.minY - originYGuess, shape.minZ - originZGuess,
				shape.maxX - originXGuess, shape.maxY - originYGuess, shape.maxZ - originZGuess
			).contextualized(originXGuess, originYGuess, originZGuess);
		}
		return new VoxelShape(shape.minX, shape.minY, shape.minZ, shape.maxX, shape.maxY, shape.maxZ);
	}

	public static VoxelShape fromOriginBox(
		double minX, double minY, double minZ,
		double maxX, double maxY, double maxZ
	) {
		if (maxX - minX < EPSILON || maxY - minY < EPSILON || maxZ - minZ < EPSILON) {
			throw new IllegalArgumentException("Empty shape");
		}
		if (Math.abs(minX) > 10 || Math.abs(minY) > 10 || Math.abs(minZ) > 10) {
			throw new IllegalArgumentException("Must be origin box: " + minX + ", " + minY + ", " + minZ + " -> " + maxX + ", " + maxY + ", " + maxZ);
		}
		return new VoxelShape(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public VoxelShape optimized() {
		if (isEmpty()) {
			return this;
		}
		return collectBoxes(Collectors.mapping(
			VoxelShape::fromBox,
			Collectors.reducing(
				VoxelShape.empty(),
				VoxelShape::combineWith
			)
		));
	}

	private final static VoxelShape EMPTY = new VoxelShape();

	public static VoxelShape empty() {
		return EMPTY;
	}
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("VoxelShape [");
		forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			builder.append("Box [");
			builder.append(minX).append(", ").append(minY).append(", ").append(minZ).append(" -> ");
			builder.append(maxX).append(", ").append(maxY).append(", ").append(maxZ).append("]");
			return true;
		}, true);
		builder.append("]");
		return builder.toString();
	}
}
