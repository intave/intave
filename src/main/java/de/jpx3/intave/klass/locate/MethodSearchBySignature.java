package de.jpx3.intave.klass.locate;

import de.jpx3.intave.library.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class MethodSearchBySignature {
  private final MethodHandle[] methodsMatching;

  public MethodSearchBySignature(MethodHandle[] methodsMatching) {
    this.methodsMatching = methodsMatching;
  }

  public void forEach(Consumer<MethodHandle> applier) {
    for (MethodHandle method : methodsMatching) {
      applier.accept(method);
    }
  }

  public Stream<MethodHandle> stream() {
    return Arrays.stream(methodsMatching);
  }

  public MethodHandle[] all() {
    return methodsMatching;
  }

  public MethodHandle findFirstOrThrow() {
    return findFirst().orElseThrow(() -> new IllegalStateException("No matching method found"));
  }

  public MethodHandle findFirstOrThrow(Supplier<? extends RuntimeException> exceptionSupplier)  {
    return findFirst().orElseThrow(exceptionSupplier);
  }

  public Optional<MethodHandle> findFirst() {
    return methodsMatching.length > 0 ?
      Optional.ofNullable(methodsMatching[0]) :
      Optional.empty();
  }

  public MethodSearchBySignature peek(Consumer<? super MethodHandle> applier) {
    for (MethodHandle method : methodsMatching) {
      applier.accept(method);
    }
    return this;
  }

  public static MethodSearchBySignature search(Class<?> target, Class<?>[] params, Class<?> returnVal) {
    return ofClass(target).withParameters(params).withReturnType(returnVal).search();
  }

  public static MethodSearchBySignature withManualFilter(Class<?> target, Class<?>[] params, Class<?> returnVal, Predicate<Method> filter) {
    return ofClass(target).withParameters(params).withReturnType(returnVal).withFilter(filter).search();
  }

  @Deprecated
  public static MethodSearchBySignature publicSearch(Class<?> target, Class<?>[] params, Class<?> returnVal) {
    return ofClass(target).withParameters(params).withReturnType(returnVal).publicLookup().search();
  }

  public static Builder ofClass(Class<?> targetClass) {
    return new Builder(targetClass);
  }

  public static class Builder {
    private final Class<?> targetClass;
    private Type[] parameters;
    private Type returnType;
    private Predicate<? super Method> filter = method -> true;
    private boolean mustHaveResult;
    private boolean publicLookup;

    public Builder(Class<?> targetClass) {
      this.targetClass = targetClass;
    }

    public Builder withParameters(Class<?>[] parameters) {
      Type[] types = new Type[parameters.length];
      for (int i = 0; i < parameters.length; i++) {
        Class<?> parameter = parameters[i];
        types[i] = Type.getType(parameter);
      }
      this.parameters = types;
      return this;
    }

    public Builder withReturnType(Class<?> returnType) {
      this.returnType = Type.getType(returnType);
      return this;
    }

    @Deprecated
    public Builder publicLookup() {
      publicLookup = true;
      return this;
    }

    public Builder enforceResult() {
      this.mustHaveResult = true;
      return this;
    }

    public Builder withFilter(Predicate<? super Method> filter) {
      this.filter = filter;
      return this;
    }

    public MethodSearchBySignature search() {
      if (targetClass == null) {
        throw new IllegalStateException();
      }
      if (parameters == null) {
        parameters = new Type[0];
      }
      if (returnType == null) {
        returnType = Type.VOID_TYPE;
      }
      List<MethodHandle> methodHandles = new ArrayList<>();
      Type methodType = Type.getMethodType(returnType, parameters);
      for (Method matchedMethod : targetClass.getDeclaredMethods()) {
        if (Type.getType(matchedMethod).equals(methodType) && filter.test(matchedMethod)) {
          methodHandles.add(createMethodHandleFor(matchedMethod));
        }
      }
      if (methodHandles.isEmpty() && mustHaveResult) {
        throw new IllegalStateException("Method pattern " + methodType + " does not apply to any methods in " + targetClass);
      }
      return new MethodSearchBySignature(methodHandles.toArray(new MethodHandle[0]));
    }

    private MethodHandle createMethodHandleFor(Method method) {
      try {
        MethodHandles.Lookup lookup = publicLookup ? MethodHandles.publicLookup() : MethodHandles.lookup();
        MethodType matchedMethodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
        return lookup.findVirtual(targetClass, method.getName(), matchedMethodType);
      } catch (NoSuchMethodException | IllegalAccessException exception) {
        throw new IllegalStateException(exception);
      }
    }
  }
}
