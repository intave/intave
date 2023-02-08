package de.jpx3.intave.connect.sibyl;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.connect.sibyl.auth.SibylAuthentication;
import de.jpx3.intave.connect.sibyl.data.SibylPacketReceiver;
import de.jpx3.intave.connect.sibyl.data.SibylPacketTransmitter;
import de.jpx3.intave.connect.sibyl.data.packet.*;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@HighOrderService
public final class SibylIntegrationService implements BukkitEventSubscriber {
  private final IntavePlugin plugin;
  private final SibylAuthentication authentication;
  private final SibylPacketTransmitter packetTransmitter;
  private final SibylPacketReceiver packetReceiver;

  public static final Set<UUID> ID_RECORDER = new HashSet<>();
  private static final KeyPair globalKeyPair;
  private static final byte[] verifyToken;
  private static final Map<UUID, Key> KEYS = GarbageCollector.watch(new HashMap<>());

  static {
    KeyPair keyPair;
    try {
      KeyPairGenerator keypairgenerator = KeyPairGenerator.getInstance("RSA");
      keypairgenerator.initialize(1024);
      keyPair = keypairgenerator.generateKeyPair();
    } catch (NoSuchAlgorithmException ignored) {
      keyPair = null;
    }
    globalKeyPair = keyPair;
    verifyToken = new byte[16];
    ThreadLocalRandom.current().nextBytes(verifyToken);
  }

  public SibylIntegrationService(IntavePlugin plugin) {
    this.plugin = plugin;
    List<Consumer<UUID>> subscribers = new ArrayList<>();
    subscribers.add(this::afterAuthentication);
    this.authentication = new SibylAuthentication(plugin, subscribers);
    this.packetTransmitter = new SibylPacketTransmitter(authentication, this);
    this.packetReceiver = new SibylPacketReceiver(plugin, this);
    this.plugin.eventLinker().registerEventsIn(this);
    broadcastRestart();
  }

  private void afterAuthentication(UUID id) {
    Player player = Bukkit.getPlayer(id);
    if (player == null) {
      return;
    }
    if (encryptionAvailable()) {
      packetTransmitter.transmitPacket(player, new SibylPacketOutBeginEncryption(globalKeyPair.getPublic(), verifyToken));
    }
  }

  @Native
  public void confirmEncryption(Player player, SibylPacketInConfirmEncryption packet) {
    if (!encryptionAvailable()) {
      return;
    }
    if (!Arrays.equals(decryptRSA(packet.encryptedVerifyToken()), verifyToken)) {
      if (IntaveControl.SIBYL_DEBUG) {
        System.out.println("Sibyl: Invalid verify token for " + player.getName());
      }
      return;
    }
    byte[] keyBytes = decryptRSA(packet.encryptedSharedSecret());
    if (keyBytes != null) {
      try {
        KEYS.put(player.getUniqueId(), new SecretKeySpec(keyBytes, "AES"));
      } catch (Exception exception) {
        exception.printStackTrace();
        Synchronizer.synchronize(() -> {
          player.kickPlayer(ChatColor.RED + "Error authenticating");
        });
      }
    }
  }

  private static byte[] decryptRSA(byte[] data) {
    try {
      Cipher cipher = Cipher.getInstance("RSA");
      cipher.init(Cipher.DECRYPT_MODE, globalKeyPair.getPrivate());
      return cipher.doFinal(data);
    } catch (Exception exception) {
      exception.printStackTrace();
      return null;
    }
  }

  public boolean encryptionActiveFor(Player player) {
    return KEYS.containsKey(player.getUniqueId());
  }

  @Native
  private void broadcastRestart() {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      authentication.sendMessageToClient(onlinePlayer, "MC|Brand", "INTAVE", null);
    }
  }

  @BukkitEventSubscription
  public void on(PlayerJoinEvent join) {
    Synchronizer.synchronizeDelayed(() -> authenticatePlayer(join.getPlayer()), 20);
    ID_RECORDER.add(join.getPlayer().getUniqueId());
    if (ID_RECORDER.size() > 10000) {
      ID_RECORDER.clear();
    }
  }

  @Native
  public void authenticatePlayer(Player player) {
    if (!authentication.isAuthenticated(player)) {
      authentication.sendMessageToClient(player, "MC|Brand", "INTAVE", null);
    }
  }

  public static KeyPair globalKeyPair() {
    return globalKeyPair;
  }

  public static byte[] verifyToken() {
    return verifyToken;
  }

  public static boolean encryptionAvailable() {
    return globalKeyPair != null && verifyToken != null;
  }

  public void publishAttackCancel(Player attacker, Entity attacked, boolean damage) {
    SibylPacketOutAttackCancel packet = new SibylPacketOutAttackCancel();
    packet.setAttacker(attacker.getUniqueId());
    packet.setAttackedLocation(attacked.getLocation().toVector());
    packet.setDamage(damage);
    broadcastTrustedPacket(packet);
  }

  public void publishDebug(Player player, int id, String fullMessage, String shortMessage) {
    SibylPacketOutDebug packet = new SibylPacketOutDebug();
    packet.setDebugId(id);
    packet.setFullMessage(fullMessage);
    packet.setShortMessage(shortMessage);
    sendTrustedPacket(player, packet);
  }

  @Native
  public void broadcastTrustedPacket(SibylPacket packet) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (authentication.isAuthenticated(player)) {
        packetTransmitter.transmitPacket(player, packet);
      }
    }
  }

  @Native
  public void sendTrustedPacket(Player player, SibylPacket packet) {
    if (authentication.isAuthenticated(player)) {
      packetTransmitter.transmitPacket(player, packet);
    }
  }

  public SibylPacketTransmitter packetTransmitter() {
    return packetTransmitter;
  }

  public SibylAuthentication authentication() {
    return authentication;
  }

  @Native
  public boolean isAuthenticated(Player user) {
    return authentication.isAuthenticated(user);
  }

  public Key keyOf(Player player) {
    return KEYS.get(player.getUniqueId());
  }
}
