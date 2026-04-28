package de.jpx3.intave.klass.locate;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.library.asm.Type;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.jpx3.intave.adapter.MinecraftVersions.VER1_17_0;
import static de.jpx3.intave.adapter.MinecraftVersions.VER1_8_0;

final class MethodLocation extends Location {
  private static final Reference<Method> EMPTY_CLASS_REFERENCE = new WeakReference<>(null);
  private final String classKey;
  private final String target;
  private Reference<Method> methodCache = EMPTY_CLASS_REFERENCE;

  public MethodLocation(String classKey, String name, VersionMatcher versionMatcher, String target) {
    super(name, versionMatcher);
    this.classKey = classKey;
    this.target = target;
  }

  public Method access() {
    Method method = methodCache.get();
    if (method == null) {
      method = compile();
      methodCache = new WeakReference<>(method);
    }
    return method;
  }

  public String targetMethodName() {
    return methodName(target);
  }

  public String targetMethodSignature() {
    return methodSignature(target);
  }

  public String targetMethodDesc() {
    return methodDesc(target);
  }

  public String translatedKey() {
    return methodName(key()) + methodSignature(key());
  }

  private Method compile() {
    String from = key();
    String to = target;
    String fromSig = methodSignature(from);
    String toSig = methodSignature(to);
    if (!fromSig.equals(toSig)) {
//      throw new IllegalStateException("Signatures differ: " + fromSig + " != " + toSig);
    }
    Class<?> owningClass = Lookup.serverClass(classKey());
    Type[] argumentTypes = Type.getArgumentTypes(toSig);
    String name = methodName(to);
    Class<?>[] parameterTypes = Arrays.stream(argumentTypes)
      .map(type -> classOf(type.getCanonicalClassName()))
      .toArray(Class[]::new);
    do {
      try {
        Method declaredMethod = owningClass.getMethod(name, parameterTypes);
        if (!declaredMethod.isAccessible()) {
          declaredMethod.setAccessible(true);
        }
        return declaredMethod;
      } catch (NoSuchMethodException ignored) {}
      try {
        Method declaredMethod = owningClass.getDeclaredMethod(name, parameterTypes);
        if (!declaredMethod.isAccessible()) {
          declaredMethod.setAccessible(true);
        }
        return declaredMethod;
      } catch (NoSuchMethodException ignored) {}
    } while ((owningClass = owningClass.getSuperclass()) != Object.class);
    throw new IllegalStateException("Unable to find method " + to + " / " + toSig + " in " + Lookup.serverClass(classKey()));
  }

  private Class<?> classOf(String name) {
    name = name.replace('/', '.');
    switch (name) {
      case "int":
        return int.class;
      case "boolean":
        return boolean.class;
      case "byte":
        return byte.class;
      case "char":
        return char.class;
      case "double":
        return double.class;
      case "float":
        return float.class;
      case "long":
        return long.class;
      case "short":
        return short.class;
      case "void":
        return void.class;
    }
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String methodName(String input) {
    return input.substring(0, input.indexOf("("));
  }

  private static final Pattern REPLACE_REGEX = Pattern.compile("R([a-z]|[A-Z]|[0-9]|\\$)+;");

  private String methodSignature(String input) {
    if (!input.contains("(")) {
      throw new IllegalArgumentException("Invalid method signature: " + input);
    }
    String signature = input.substring(input.indexOf("("));
    Matcher matcher = REPLACE_REGEX.matcher(signature);
    int lastEnd = 0;
    while (matcher.find(lastEnd)) {
      int start = matcher.start();
      int end = matcher.end();
      String expr = signature.substring(start + 1, end - 1);
      Class<?> serverClass = Lookup.serverClass(expr);
      String formattedServerClass = "L" + serverClass.getName().replace(".", "/") + ";";
      signature = signature.substring(0, start) + formattedServerClass + signature.substring(end);
      lastEnd = start + formattedServerClass.length();
      matcher = REPLACE_REGEX.matcher(signature);
    }
    return signature;
  }

  private String methodDesc(String target) {
    String desc = target.substring(target.indexOf("("));
    Matcher matcher = REPLACE_REGEX.matcher(desc);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String match = matcher.group();
      String content = match.substring(1, match.length() - 1);
      Class<?> serverClass = Lookup.serverClass(content);
      String replacement = "L" + serverClass.getName().replace(".", "/") + ";";
      matcher.appendReplacement(result, replacement);
    }
    matcher.appendTail(result);
    return result.toString();
  }

  public String methodNameOfKey() {
    return methodName(key());
  }

  public String classKey() {
    return classKey;
  }

  public static MethodLocation defaultFor(String classKey, String initialSignature) {
    return new MethodLocation(
      classKey, initialSignature,
      VersionMatcher.between(VER1_8_0, VER1_17_0),
      initialSignature
    );
  }

  public String target() {
    return target;
  }
}
