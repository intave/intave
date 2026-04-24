package de.jpx3.intave.test;

import com.google.common.collect.ImmutableList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

public final class FakePlayerFactory {
  private FakePlayerFactory() {}

  public static Player createPlayer() {
    return createPlayer(ImmutableList.of(FakePlayerFactory::identity));
  }

  public static Player createPlayer(BiFunction<String, Object[], Object> methodReturn) {
    List<BiFunction<String, Object[], Object>> methods = new ArrayList<>();
    methods.add(FakePlayerFactory::identity);
    methods.add(methodReturn);
    return createPlayer(methods);
  }

  private static Player createPlayer(List<? extends BiFunction<String, Object[], Object>> methods) {
    return (Player) Proxy.newProxyInstance(
      FakePlayerFactory.class.getClassLoader(),
      new Class[] {Player.class},
      (proxy, method, args) -> {
        String methodName = method.getName();
        for (BiFunction<String, Object[], Object> function : methods) {
          Object result = function.apply(methodName, args == null ? new Object[0] : args);
          if (result != null) {
            return result;
          }
        }
        if ("getServer".equals(methodName)) {
          return Bukkit.getServer();
        }
        if ("toString".equals(methodName)) {
          return "FakeIntavePlayer";
        }
        if ("hashCode".equals(methodName)) {
          return FAKE_TEST_UUID.hashCode();
        }
        if ("equals".equals(methodName)) {
          return proxy == args[0];
        }
        throw new UnsupportedOperationException("Method " + methodName + " is not supported for this test player");
      }
    );
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
