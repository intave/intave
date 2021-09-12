package de.jpx3.intave.block.variant;

public final class BlockVariantBoolean extends BlockVariantData<Boolean> {
  private Object converter;

  private BlockVariantBoolean(String name, boolean defaultValue) {
    super(name, defaultValue, Boolean.class);
  }

  @Override
  public void build() {
    this.converter = BlockStateServerBridge.booleanStateOf(name());
  }

  @Override
  public Object convert() {
    return this.converter;
  }

  public static BlockVariantBoolean of(String name) {
    return new BlockVariantBoolean(name, false);
  }

  public static BlockVariantBoolean of(String name, boolean defaultValue) {
    return new BlockVariantBoolean(name, defaultValue);
  }
}