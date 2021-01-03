package de.jpx3.intave.event.service;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.event.service.transaction.TransactionCallBackData;
import de.jpx3.intave.event.service.transaction.TransactionFeedbackCallback;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaSynchronizeData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public final class TransactionFeedbackService extends PacketAdapter {
  public final static short TRANSACTION_MIN_CODE = Short.MIN_VALUE;
  public final static short TRANSACTION_MAX_CODE = -16370;
  private final static ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

  public TransactionFeedbackService(Plugin plugin) {
    super(plugin, PacketType.Play.Client.TRANSACTION);
    protocolManager.addPacketListener(this);
  }

  @Override
  public void onPacketReceiving(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (user == null) {
      return;
    }
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();
    Map<Short, TransactionCallBackData<?>> transactionFeedBackMap = synchronizeData.transactionFeedBackMap();
    Short transactionIdentifier = event.getPacket().getShorts().readSafely(0);
    if (transactionIdentifier <= TRANSACTION_MAX_CODE) {
      TransactionCallBackData<?> transactionResponse = transactionFeedBackMap.get(transactionIdentifier);
      if (transactionResponse == null) {
        return;
      }
      transactionFeedBackMap.remove(transactionIdentifier);
      transactionResponse.transactionFeedbackCallback().success(
        player,
        convertInstanceOfObject(transactionResponse.obj())
      );
      event.setCancelled(true);
    }
  }

  private <T> T convertInstanceOfObject(Object o) {
    try {
      //noinspection unchecked
      return (T) o;
    } catch (ClassCastException e) {
      return null;
    }
  }

  public <T> void requestPong(Player player, T target, TransactionFeedbackCallback<T> callback) {
    Short id = acquireNewId(player, target, callback);
    if (id != null) {
      sendTransactionPacket(player, id);
    }
  }

  private <T> Short acquireNewId(Player player, T obj, TransactionFeedbackCallback<T> callback) {
    User user = UserRepository.userOf(player);
    if (user == null) {
      return null;
    }
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();
    short transactionCounter = synchronizeData.transactionCounter++;
    if (transactionCounter >= TRANSACTION_MAX_CODE) {
      transactionCounter = TRANSACTION_MIN_CODE;
    }
    TransactionCallBackData<T> transactionCallBackData = new TransactionCallBackData<>(callback, obj);
    synchronizeData.transactionFeedBackMap().put(transactionCounter, transactionCallBackData);
    return transactionCounter;
  }

  private void sendTransactionPacket(Player receiver, short id) {
    PacketContainer transactionPacket = protocolManager.createPacket(PacketType.Play.Server.TRANSACTION);
    transactionPacket.getIntegers().write(0, 0);
    transactionPacket.getShorts().write(0, id);
    transactionPacket.getBooleans().write(0, false);
    try {
      protocolManager.sendServerPacket(receiver, transactionPacket);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }
}