package de.jpx3.intave.module.linker.bukkit;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.module.linker.SubscriptionInstanceProvider;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.PlayerEvent;

import java.util.function.Consumer;
import java.util.function.Function;

public class OneBukkitEventForOne<T extends LISTENER, LISTENER> implements SubscriptionInstanceProvider<Event, T, LISTENER> {
  private final Function<? super User, ? extends T> supplier;
  private final T fallback;
  private final Class<T> subscriberClass;

  public OneBukkitEventForOne(Function<? super User, ? extends T> supplier) {
    this.supplier = supplier;
    this.fallback = supplier.apply(UserRepository.userOf((Player) null));
    //noinspection unchecked
    this.subscriberClass = (Class<T>) fallback.getClass();
  }

  @Override
  public Class<T> type() {
    return subscriberClass;
  }

  private boolean warningIssued = false;

  @Override
  public void apply(Event event, Consumer<? super T> consumer) {
    if (IntaveControl.DEBUG) {
      if (!warningIssued) {
        warningIssued = true;
        IntaveLogger.logger().warning("Bukkit per-player event listener is still experimental and may not work as expected.");
      }
    }
    // if event has player, then filter.
    if (event instanceof PlayerEvent) {
      User user = UserRepository.userOf(((PlayerEvent) event).getPlayer());
      consumer.accept(supplier.apply(user));
      return;
    } else if (event instanceof InventoryInteractEvent) {
      HumanEntity human = ((InventoryInteractEvent) event).getWhoClicked();
      if (human instanceof Player) {
        User user = UserRepository.userOf((Player) human);
        consumer.accept(supplier.apply(user));
        return;
      }
    }
    // else apply to all
    UserRepository.applyOnAll(user -> consumer.accept(supplier.apply(user)));
  }

  @Override
  public T fallback() {
    return fallback;
  }
}
