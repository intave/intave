package de.jpx3.intave.detect;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

public abstract class IntaveCheckPart<P extends IntaveCheck> implements EventProcessor {
  private final P parentCheck;

  public IntaveCheckPart(P parentCheck) {
    this.parentCheck = parentCheck;
  }

  protected User userOf(Player player) {
    return UserRepository.userOf(player);
  }

  public P parentCheck() {
    return parentCheck;
  }

  public boolean enabled() {
    return parentCheck.enabled();
  }
}