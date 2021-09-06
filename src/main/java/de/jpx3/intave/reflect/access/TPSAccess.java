package de.jpx3.intave.reflect.access;

import de.jpx3.intave.math.MathHelper;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.Field;

public final class TPSAccess {
  private static double[] tpsAccess;

  public static void setup() {
    try {
      Server server = Bukkit.getServer();
      Field consoleField = server.getClass().getDeclaredField("console");
      consoleField.setAccessible(true);
      Object minecraftServer = consoleField.get(server);
      Field recentTps = minecraftServer.getClass().getSuperclass().getDeclaredField("recentTps");
      recentTps.setAccessible(true);
      tpsAccess = (double[]) recentTps.get(minecraftServer);
    } catch (IllegalAccessException | NoSuchFieldException exception) {
      try {
        Server serverSpigot = Bukkit.getServer();
        tpsAccess = (double[]) serverSpigot.getClass().getMethod("getTPS").invoke(serverSpigot);
        return;
      } catch (Exception exception1) {
        exception.printStackTrace();
        exception1.printStackTrace();
      }
      tpsAccess = new double[] {20, 20, 20};
    }
  }

  public static double[] recentTickAverage() {
    return tpsAccess;
  }

  public static String stringFormattedTick() {
    return MathHelper.formatDouble(tpsAccess[1], 5);
  }
}
