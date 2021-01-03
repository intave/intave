package de.jpx3.intave.detect;

import com.google.common.collect.Lists;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public abstract class IntaveCheck implements EventProcessor {
  private final String checkName;
  private final String configurationName;

  public final List<IntaveCheckPart> checkParts = new ArrayList<>();

  public IntaveCheck(String checkName, String configurationName) {
    this.checkName = checkName;
    this.configurationName = configurationName;
  }

  protected User userOf(Player player) {
    return UserRepository.userOf(player);
  }

  protected void appendCheckPart(IntaveCheckPart checkPart) {
    checkParts.add(checkPart);
  }

  public List<IntaveCheckPart> checkParts() {
    return Lists.newArrayList();
  }

  public boolean enabled() {
    return true;
  }

  public String name() {
    return checkName;
  }
}