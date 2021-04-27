package de.jpx3.intave.reflect.relocate;

import de.jpx3.intave.lib.asm.Handle;

import java.util.Map;

public class UnivHandleMapper implements ClassMapper, MethodMapper, FieldMapper {
  public static final int METHOD = 0x800;
  public static final int FIELD = 0x1000;

  private final Map<String, String> classTranslation;
  private final Map<Handle, Handle> handleTranslation;

  public UnivHandleMapper(Map<String, String> classTranslation, Map<Handle, Handle> handleTranslation) {
    this.classTranslation = classTranslation;
    this.handleTranslation = handleTranslation;
  }

  @Override
  public String className(String originalClassName) {
    return classTranslation.get(originalClassName);
  }

  @Override
  public String methodName(String originalClassName, String relocatedClassName, String originalMethodName, String originalMethodSignature, String relocatedMethodSignature) {
    Handle originalHandle = new Handle(METHOD, originalClassName, originalMethodName, originalMethodName);
    return handleTranslation.get(originalHandle).getName();
  }

  @Override
  public String fieldName(String originalClassName, String relocatedClassName, String originalFieldName, String originalFieldType, String relocatedFieldType) {
    Handle originalHandle = new Handle(FIELD, originalClassName, originalFieldName, originalFieldType);
    return handleTranslation.get(originalHandle).getName();
  }
}
