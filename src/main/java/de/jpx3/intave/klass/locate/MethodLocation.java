package de.jpx3.intave.klass.locate;

import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.library.asm.Type;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MethodLocation extends Location {
  private static final Reference<Method> EMPTY_CLASS_REFERENCE = new WeakReference<>(null);
  private final String classKey;
  private final String target;
  private Reference<Method> methodCache = EMPTY_CLASS_REFERENCE;

  public MethodLocation(String classKey, String name, IntegerMatcher versionMatcher, String target) {
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

  public String translatedKey() {
    return methodName(key()) + methodSignature(key());
  }

  private Method compile() {
    String from = key();
    String to = target;
    String fromSig = methodSignature(from);
    String toSig = methodSignature(to);
    if (!fromSig.equals(toSig)) {
      throw new IllegalStateException("Signatures differ: " + fromSig + " != " + toSig);
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
    } while ((owningClass = owningClass.getSuperclass()) != Object.class);
    throw new IllegalStateException("Unable to find method " + to + " in " + classKey());
  }

  private Class<?> classOf(String name) {
    name = name.replace('/', '.');
    if (name.equals("int")) {
      return int.class;
    }
    if (name.equals("boolean")) {
      return boolean.class;
    }
    if (name.equals("byte")) {
      return byte.class;
    }
    if (name.equals("char")) {
      return char.class;
    }
    if (name.equals("double")) {
      return double.class;
    }
    if (name.equals("float")) {
      return float.class;
    }
    if (name.equals("long")) {
      return long.class;
    }
    if (name.equals("short")) {
      return short.class;
    }
    if (name.equals("void")) {
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

  public String methodNameOfKey() {
    return methodName(key());
  }

  public String classKey() {
    return classKey;
  }

  public static MethodLocation defaultFor(String classKey, String initialSignature) {
    return new MethodLocation(classKey, initialSignature, IntegerMatcher.between(80, 170), initialSignature);
  }
}
