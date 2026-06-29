package de.jpx3.intave.test;

import com.comphenix.protocol.utility.ByteBuddyGenerated;
import com.google.common.collect.ImmutableList;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.description.modifier.ModifierContributor;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiFunction;

import static org.bukkit.GameMode.SURVIVAL;

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

    Map<String, List<MetadataValue>> fakeMetadata = new HashMap<>();
    methods.add((methodName, arguments) -> {
      switch (methodName) {
        case "setMetadata":
          String key = (String) arguments[0];
          MetadataValue metadataValue = (MetadataValue) arguments[1];
          fakeMetadata.computeIfAbsent(key, k -> new ArrayList<>()).add(metadataValue);
          return new Object();
        case "getMetadata":
          String getKey = (String) arguments[0];
          return fakeMetadata.getOrDefault(getKey, Collections.emptyList());
        case "hasMetadata":
          String getKey2 = (String) arguments[0];
          return fakeMetadata.containsKey(getKey2);
      }
      return null;
    });

    PlayerInventory inventory = new MockEmptyInventory();

    // default fallbacks
    methods.add((methodName, args) -> {
      switch (methodName) {
        case "getLocation":
          return Bukkit.getWorlds().isEmpty()
            ? new Location(null, 0, 0, 0)
            : new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
        case "getHealth":
          return MinecraftVersions.VER1_9_0.atOrAbove() ? 20.0 : 20.0f;
        case "getFoodLevel":
          return 20;
        case "isFlying":
        case "getAllowFlight":
        case "isSprinting":
        case "isSneaking":
        case "isSleeping":
          return false;
        case "getFallDistance":
          return 0.0f;
        case "getGameMode":
          return SURVIVAL;
        case "getFlySpeed":
        case "getWalkSpeed":
          return 0.2f;
        case "getEntityId":
          return 100000;
        case "getInventory":
          return inventory;
        case "getActivePotionEffects":
          return Collections.emptyList();
      }
      return null;
    });

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
      public Object delegate(@This Object obj, @Origin Method method, @FieldValue("server") Server server, @AllArguments Object... args) {
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
    ElementMatcher.Junction<ByteCodeElement> callbackFilter = ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class)
      .or(ElementMatchers.isDeclaredBy(FakePlayer.class)));
    try {
      return createSubclass(FakePlayer.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
        .name(FakePlayerFactory.class.getPackage().getName() + ".PlayerGenerator" + UUID.randomUUID().toString().substring(0, 8) + COUNTER++)
        .implement(new Type[]{Player.class})
        .defineField("server", Server.class, new ModifierContributor.ForField[]{Visibility.PRIVATE})
        .defineConstructor(new ModifierContributor.ForMethod[]{Visibility.PUBLIC})
        .withParameters(new Type[]{Server.class})
        .intercept(MethodCall.invoke(FakePlayer.class.getDeclaredConstructor()).andThen(FieldAccessor.ofField("server").setsArgumentAt(0)))
        .defineMethod("getHealth", double.class, Visibility.PUBLIC)
        .intercept(implementation)
        .method(callbackFilter)
        .intercept(implementation)
        .make()
        .load(IntavePlugin.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
        .getLoaded()
        .getDeclaredConstructor(Server.class);
    } catch (NoSuchMethodException var3) {
      throw new RuntimeException("Failed to find constructor", var3);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  private static <T> DynamicType.Builder.MethodDefinition.ImplementationDefinition.Optional<T> createSubclass(
    Class<T> clz, ConstructorStrategy.Default constructorStrategy
  ) {
    return (new ByteBuddy()).subclass(clz, constructorStrategy).implement(ByteBuddyGenerated.class);
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
