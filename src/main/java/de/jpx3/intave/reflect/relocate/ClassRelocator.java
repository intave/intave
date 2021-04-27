package de.jpx3.intave.reflect.relocate;

import de.jpx3.intave.lib.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class ClassRelocator {
  private static ClassMapper classMapper;
  private static FieldMapper fieldMapper;
  private static MethodMapper methodMapper;

  public static boolean hasClass(String className) {
    String newClassName = classMapper == null ? className : classMapper.className(className);
    try {
      Class.forName(newClassName);
      return true;
    } catch (Exception exception) {
      return false;
    }
  }

  public static Class<?> findClass(String className) {
    String newClassName = classMapper == null ? className : classMapper.className(className);
    newClassName = newClassName.replace("/", ".");
    try {
      return Class.forName(newClassName);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to find class " + className + " / " + newClassName);
    }
  }

  public static Method findMethod(String className, String name, String signature) {
    Class<?> clazz = findClass(className);
    Type[] argumentTypes = Type.getArgumentTypes(signature);
    Class<?>[] params = Arrays.stream(argumentTypes).map(argumentType -> findClass(argumentType.getClassName())).toArray(Class<?>[]::new);
    try {
      if(methodMapper == null) {
        return clazz.getMethod(name, params);
      } else {
        Type returnType = Type.getReturnType(signature);
        Type[] paramTypes = new Type[argumentTypes.length];
        for (int i = 0; i < params.length; i++) {
          paramTypes[i] = Type.getType(params[i]);
        }
        String newMethodDescriptor = Type.getMethodDescriptor(returnType, paramTypes);
        return clazz.getMethod(methodMapper.methodName(className, clazz.getName(), name, signature, newMethodDescriptor), params);
      }
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static Field findField(String className, String name) {
    return null;
  }

  public static ClassMapper classMapper() {
    return classMapper;
  }

  public static void setClassMapper(ClassMapper classMapper) {
    ClassRelocator.classMapper = classMapper;
  }

  public static FieldMapper fieldMapper() {
    return fieldMapper;
  }

  public static void setFieldMapper(FieldMapper fieldMapper) {
    ClassRelocator.fieldMapper = fieldMapper;
  }

  public static MethodMapper methodMapper() {
    return methodMapper;
  }

  public static void setMethodMapper(MethodMapper methodMapper) {
    ClassRelocator.methodMapper = methodMapper;
  }
}
