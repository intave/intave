package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.user.User;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class PacketLogging extends Module {
  private final Map<UUID, PacketListenerAbstract> adapterMap = GarbageCollector.watch(new HashMap<>());
  private final Map<String, UUID> packetLoggers = GarbageCollector.watch(new HashMap<>());
  private final Map<UUID, PrintStream> packetLogStreams = GarbageCollector.watch(new HashMap<>());

  {
    ShutdownTasks.add(() -> {
      packetLogStreams.forEach((uuid, printStream) -> {
        printStream.flush();
        printStream.close();
      });
    });
  }

  public void togglePacketLogging(CommandSender sender, Player target) {
    File logsFolder = new File(plugin.dataFolder(), "packetlogs");
    File packetLogFile = new File(logsFolder, packetLogFileName(target.getName()));

    UUID userId = target.getUniqueId();
    if (packetLoggers.containsKey(sender.getName())) {
      if (!packetLoggers.get(sender.getName()).equals(userId)) {
        sender.sendMessage(IntavePlugin.prefix() + ChatColor.GREEN + "You currently can only packetlog one player at the time, contact us if you need to log multiple players at the same time.");
        sender.sendMessage(IntavePlugin.prefix() + ChatColor.GREEN + "We will stop packetlogging for " + packetLoggers.get(sender.getName()));
        userId = packetLoggers.get(sender.getName());
      } else {
        sender.sendMessage(IntavePlugin.prefix() + ChatColor.GREEN + "Packetlogging stopped");
      }
      PacketListenerAbstract listener = adapterMap.remove(userId);
      if (listener != null) {
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
      }
      packetLoggers.remove(sender.getName());
      PrintStream remove = packetLogStreams.remove(userId);
      if (remove != null) {
        remove.flush();
        remove.close();
      }
      return;
    }

    try {
      logsFolder.mkdir();
      packetLogFile.createNewFile();
    } catch (IOException exception) {
      exception.printStackTrace();
      return;
    }

    try {
      OutputStream stream = new BufferedOutputStream(new FileOutputStream(packetLogFile));
      PrintStream printStream = new PrintStream(stream);

      UUID finalUserId = userId;
      PacketListenerAbstract adapter = new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
        @Override
        public void onPacketSend(PacketSendEvent event) {
          writeEvent(finalUserId, printStream, event, "<--out--");
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
          writeEvent(finalUserId, printStream, event, "--in-->");
        }
      };
      adapterMap.put(userId, adapter);
      PacketEvents.getAPI().getEventManager().registerListener(adapter);
      packetLoggers.put(sender.getName(), userId);
      packetLogStreams.put(userId, printStream);
    } catch (FileNotFoundException exception) {
      exception.printStackTrace();
    }
    sender.sendMessage(IntavePlugin.prefix() + ChatColor.GREEN + "Packetlogging started for " + target.getName());
    sender.sendMessage(IntavePlugin.prefix() + "You can find it under " + packetLogFile.getAbsolutePath());
  }

  private static void writeEvent(
    UUID target,
    PrintStream printStream,
    ProtocolPacketEvent event,
    String direction
  ) {
    Player player = event.getPlayer();
    if (player == null || !player.getUniqueId().equals(target)) {
      return;
    }
    synchronized (printStream) {
      printStream.println(
        (System.currentTimeMillis() % 1000) + " " +
          direction + " " +
          event.getPacketName() +
          (event.isCancelled() ? " (cancelled)" : "") +
          " " +
          packetContent(event)
      );
    }
  }

  public void logSystemMessage(User target, Supplier<String> messageSupplier) {
    if (target == null) {
      return;
    }
    boolean requestedMovementDebugToConsole = System.currentTimeMillis() - target.meta().violationLevel().lastMovementDebugRequest < 10_000;
    PrintStream stream;
    try {
      stream = packetLogStreams.get(target.player().getUniqueId());
      if (stream == null) {
        if (requestedMovementDebugToConsole) {
          String message = messageSupplier.get();
          plugin.logger().info("MOVE_DEBUG> " + message);
        }
        return;
      }
    } catch (Exception exception) {
      return;
    }
    synchronized (stream) {
      stream.println((System.currentTimeMillis() % 1000) + " " + messageSupplier.get());
    }
  }

  private static String packetContent(ProtocolPacketEvent event) {
    Object buffer = event.getFullBufferClone();
    if (!(buffer instanceof ByteBuf)) {
      return "{buffer=" + String.valueOf(buffer) + "}";
    }
    ByteBuf byteBuf = (ByteBuf) buffer;
    try {
      int length = byteBuf.readableBytes();
      StringBuilder builder = new StringBuilder();
      builder.append("{bytes=").append(length).append(", head=[");
      int startIndex = byteBuf.readerIndex();
      int limit = Math.min(length, 40);
      for (int i = 0; i < limit; i++) {
        if (i > 0) {
          builder.append(' ');
        }
        int value = byteBuf.getUnsignedByte(startIndex + i);
        if (value < 16) {
          builder.append('0');
        }
        builder.append(Integer.toHexString(value));
      }
      if (length > limit) {
        builder.append(" ...");
      }
      builder.append("]}");
      return builder.toString();
    } finally {
      ReferenceCountUtil.release(byteBuf);
    }
  }

  private static final DateTimeFormatter FILE_MESSAGE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH-mm-ss");

  private static String packetLogFileName(String playername) {
    return "intave-packetlog-" + playername + "-" + LocalDateTime.now().format(FILE_MESSAGE_DATE_FORMATTER).toLowerCase(Locale.ROOT) + ".txt";
  }
}
