package de.jpx3.intave.check;

import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.PlayerBukkitEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PlayerPacketEventSubscriber;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;

import java.util.function.Function;

class PlayerCheckPartDelegate<P extends Check, D extends PlayerCheckPart<P>>
  extends CheckPart<P>
  implements PlayerBukkitEventSubscriber, PlayerPacketEventSubscriber
{
	protected PlayerCheckPartDelegate(P parentCheck, Class<? extends D> delegateClass) {
    this(parentCheck, user -> {
      try {
        return delegateClass.getDeclaredConstructor(User.class, parentCheck.getClass()).newInstance(user, parentCheck);
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private final UserLocal<D> checks;

  protected PlayerCheckPartDelegate(P parentCheck, Function<? super User, ? extends D> generator) {
    super(parentCheck);
	  this.checks = UserLocal.withInitial(generator);
  }

  @Override
  public PacketEventSubscriber packetSubscriberFor(User user) {
    return delegateOf(user);
  }

  @Override
  public BukkitEventSubscriber bukkitSubscriberFor(User user) {
    return delegateOf(user);
  }

  private D delegateOf(User user) {
    return checks.get(user);
  }
}
