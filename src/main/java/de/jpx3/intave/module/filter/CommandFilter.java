package de.jpx3.intave.module.filter;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.view.ChatTextView;
import de.jpx3.intave.packet.view.PacketEventsChatTextView;
import de.jpx3.intave.packet.view.PacketEventsTabCompleteView;
import de.jpx3.intave.packet.view.ProtocolLibChatTextView;
import de.jpx3.intave.packet.view.ProtocolLibTabCompleteView;
import de.jpx3.intave.packet.view.TabCompleteView;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CHAT_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.TAB_COMPLETE_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.TAB_COMPLETE_OUT;

public final class CommandFilter extends Filter {
  private final boolean separateEnable;
  private final boolean disabled;
  private final Map<String, String> redirects = new HashMap<>();

  public CommandFilter(IntavePlugin plugin) {
    super("command");
    separateEnable = plugin.settings().getBoolean("command.hide", true);
    disabled = plugin.settings().getBoolean("command.fix-tab-kicks", false);

    ConfigurationSection reroute = plugin.settings().getConfigurationSection("command.reroute");
    if (reroute != null) {
      reroute.getKeys(false).forEach(key -> redirects.put(key, plugin.settings().getString("command.reroute." + key)));
    }
  }

  @PacketSubscription(
    packetsIn = {
      CHAT_IN, TAB_COMPLETE_IN
    }
  )
  public void receiveChatPacket(PacketEvent event) {
    handleChatPacket(new ProtocolLibChatTextView(event));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      CHAT_IN, TAB_COMPLETE_IN
    }
  )
  public void receiveChatPacket(PacketReceiveEvent event) {
    PacketEventsChatTextView view = PacketEventsChatTextView.of(event);
    if (view == null) {
      return;
    }
    handleChatPacket(view);
  }

  /** Engine independent command rerouting and hiding; see {@link ChatTextView}. */
  private void handleChatPacket(ChatTextView view) {
    Player player = view.player();
    String message = view.text();
    if (player == null || message == null) {
      view.release();
      return;
    }

    String trimmedMessage = message.trim().toLowerCase();

    for (Map.Entry<String, String> stringStringEntry : redirects.entrySet()) {
      if (trimmedMessage.startsWith(stringStringEntry.getKey())) {
        // remove the command and replace it with the redirect without regex
        String redirect = stringStringEntry.getValue();
        if (redirect.toLowerCase().contains("root")) {
          continue;
        }
        trimmedMessage = redirect + trimmedMessage.substring(stringStringEntry.getKey().length());
        view.setText(trimmedMessage);
        trimmedMessage = trimmedMessage.trim().toLowerCase();
      }
    }

    boolean permitted = BukkitPermissionCheck.permissionCheck(player, "intave.command");
    if ((trimmedMessage.startsWith("/iac") || trimmedMessage.startsWith("/intave")) && !permitted) {
      view.setText("/intavecommandforward");
    }
    view.release();
  }

//  @PacketSubscription(
////    engine = Engine.ASYNC_INTERNAL,
//    packetsOut = {
//      COMMANDS
//    }
//  )
//  public void receiveCommands(PacketEvent event) {
//    Player player = event.getPlayer();
//    PacketContainer packet = event.getPacket();
//    StructureModifier<RootCommandNode> rootModifier = packet.getSpecificModifier(RootCommandNode.class);
//    RootCommandNode<?> rootCommandNode = rootModifier.readSafely(0);
////    player.sendMessage("Removing " + rootCommandNode.getChildren());
////    for (CommandNode<?> child : rootCommandNode.getChildren()) {
////      System.out.println(child.getName() + " -> " + child.getUsageText());
////    }
//    rootCommandNode.removeCommand("iac");
//    rootCommandNode.removeCommand("intave:iac");
//    rootCommandNode.removeCommand("intave");
//    rootCommandNode.removeCommand("intave:intave");
////    rootModifier.write(0, new RootCommandNode());
//    rootModifier.write(0, rootCommandNode);
//  }

  @PacketSubscription(
//    engine = Engine.ASYNC_INTERNAL,
    packetsOut = {
      TAB_COMPLETE_OUT
    }
  )
  public void receiveTabComplete(PacketEvent event) {
    handleTabComplete(new ProtocolLibTabCompleteView(event));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      TAB_COMPLETE_OUT
    }
  )
  public void receiveTabComplete(PacketSendEvent event) {
    PacketEventsTabCompleteView view = PacketEventsTabCompleteView.of(event);
    if (view == null) {
      return;
    }
    handleTabComplete(view);
  }

  /** Engine independent hiding of Intave's own commands; see {@link TabCompleteView}. */
  private void handleTabComplete(TabCompleteView view) {
    Player player = view.player();
    if (player == null) {
      view.release();
      return;
    }
    boolean permitted = BukkitPermissionCheck.permissionCheck(player, "intave.command");
    if (permitted) {
      view.release();
      return;
    }
    String[] stuff = view.matches();
    if (stuff != null) {
      List<String> newTabCompletions = Lists.newArrayList();
      Arrays.stream(stuff).filter(string -> !string.contains("/intave") && !string.contains("/iac")).forEach(newTabCompletions::add);
      if (newTabCompletions.size() != stuff.length) {
        view.setMatches(newTabCompletions.toArray(new String[0]));
      }
    }
    view.release();
  }

  @Override
  protected boolean enabled() {
    return (super.enabled() || separateEnable) && !disabled;
  }
}
