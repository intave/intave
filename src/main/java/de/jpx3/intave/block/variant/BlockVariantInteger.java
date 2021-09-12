package de.jpx3.intave.block.variant;

public final class BlockVariantInteger extends BlockVariantData<Integer> {
  private final int min;
  private final int max;
  private Object converter;

  private BlockVariantInteger(String name, int min, int max, int defaultValue) {
    super(name, defaultValue, Integer.class);
    this.min = min;
    this.max = max;
  }

  @Override
  public void build() {
    this.converter = BlockStateServerBridge.integerStateOf(name(), min, max);
  }

  @Override
  public Object convert() {
    return this.converter;
  }

  public static BlockVariantInteger of(String name, int min, int max) {
    return new BlockVariantInteger(name, min, max, min);
  }

  public static BlockVariantInteger of(String name, int min, int max, int defaultValue) {
    return new BlockVariantInteger(name, min, max, defaultValue);
  }
}