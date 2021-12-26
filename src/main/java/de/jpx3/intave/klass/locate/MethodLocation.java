package de.jpx3.intave.klass.locate;

import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.lib.asm.Type;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MethodLocation extends Location {
  private final static Reference<Method> EMPTY_CLASS_REFERENCE = new WeakReference<>(null);
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
      throw new IllegalStateException("Signatures must ( still :( ) match: " + fromSig + " != " + toSig);
    }
    Class<?> ownerClass = Lookup.serverClass(classKey());
    Type[] argumentTypes = Type.getArgumentTypes(toSig);
    Class<?>[] parameterTypes = Arrays.stream(argumentTypes)
      .map(type -> classOf(type.getCanonicalClassName()))
      .toArray(Class[]::new);
    try {
      return ownerClass.getMethod(methodName(to), parameterTypes);
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private Class<?> classOf(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String methodName(String input) {
    return input.substring(0, input.indexOf("("));
  }

  private final static Pattern REPLACE_REGEX = Pattern.compile("R([a-z]|[A-Z]|[0-9]|\\$)+;");

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

  public String classKey() {
    return classKey;
  }

  @Override
  public String toString() {
    return "MethodLocation{"+classKey+"."+key()+"/"+translatedKey()+" -> "+target+" @"+versionMatcher()+"}";
  }

  public static MethodLocation defaultFor(String classKey, String initialSignature) {
    return new MethodLocation(classKey, initialSignature, IntegerMatcher.between(8, 17), initialSignature);
  }
}
