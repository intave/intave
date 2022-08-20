package de.jpx3.intave.test;

import com.comphenix.net.bytebuddy.description.ByteCodeElement;
import com.comphenix.net.bytebuddy.description.modifier.ModifierContributor;
import com.comphenix.net.bytebuddy.description.modifier.Visibility;
import com.comphenix.net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import com.comphenix.net.bytebuddy.implementation.FieldAccessor;
import com.comphenix.net.bytebuddy.implementation.MethodCall;
import com.comphenix.net.bytebuddy.implementation.MethodDelegation;
import com.comphenix.net.bytebuddy.implementation.bind.annotation.*;
import com.comphenix.net.bytebuddy.matcher.ElementMatcher;
import com.comphenix.net.bytebuddy.matcher.ElementMatchers;
import com.comphenix.protocol.utility.ByteBuddyFactory;
import com.google.common.collect.ImmutableList;
import de.jpx3.intave.IntavePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

// this class was inspired by protocollib
// big <3 to them
public final class FakePlayerFactory {
  private static final Constructor<?> defaultConstructor = playerConstructorForMethods(ImmutableList.of(FakePlayerFactory::identity));

  public static Player createPlayer() {
    try {
      return (Player) defaultConstructor.newInstance(Bukkit.getServer());
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public static Player createPlayer(BiFunction<String, Object[], Object> methodReturn) {
    List<BiFunction<String, Object[], Object>> methods = new ArrayList<>();
    methods.add(FakePlayerFactory::identity);
    methods.add(methodReturn);
    try {
      return (Player) playerConstructorForMethods(methods).newInstance(Bukkit.getServer());
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private static int COUNTER = 0;

  private static Constructor<?> playerConstructorForMethods(List<? extends BiFunction<String, Object[], Object>> methods) {
    MethodDelegation implementation = MethodDelegation.to(new Object() {
      @RuntimeType
      public Object delegate(@This Object obj, @Origin Method method, @FieldValue("server") Server server, @AllArguments Object... args) throws Throwable {
        String methodName = method.getName();
        for (BiFunction<String, Object[], Object> stringObjectBiFunction : methods) {
          Object result = stringObjectBiFunction.apply(methodName, args);
          if (result != null) {
            return result;
          }
        }
        throw new UnsupportedOperationException("Method " + methodName + " is not supported for this testplayer");
      }
    });
    ElementMatcher.Junction<ByteCodeElement> callbackFilter = ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class).or(ElementMatchers.isDeclaredBy(FakePlayer.class)));
    try {
      return ByteBuddyFactory.getInstance()
        .createSubclass(FakePlayer.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
        .name(FakePlayerFactory.class.getPackage().getName() + ".Generator" + UUID.randomUUID().toString().substring(0,8) + COUNTER++)
        .implement(new Type[]{Player.class})
        .defineField("server", Server.class, new ModifierContributor.ForField[]{Visibility.PRIVATE})
        .defineConstructor(new ModifierContributor.ForMethod[]{Visibility.PUBLIC})
        .withParameters(new Type[]{Server.class})
        .intercept(MethodCall.invoke(FakePlayer.class.getDeclaredConstructor()).andThen(FieldAccessor.ofField("server").setsArgumentAt(0)))
        .method(callbackFilter)
        .intercept(implementation)
        .make()
        .load(IntavePlugin.class.getClassLoader(), com.comphenix.net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.INJECTION)
        .getLoaded()
        .getDeclaredConstructor(Server.class);
    } catch (NoSuchMethodException var3) {
      throw new RuntimeException("Failed to find constructor", var3);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  private static final UUID FAKE_TEST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

  private static Object identity(String name, Object[] args) {
    if (name.equals("getName")) {
      return "TESTPLAYER";
    } else if (name.equals("getUniqueId")) {
      return FAKE_TEST_UUID;
    } else {
      return null;
    }
  }
}
