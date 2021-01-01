package de.jpx3.intave.detect;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

public abstract class IntaveMetaCheckPart<P extends IntaveCheck, META extends UserCustomCheckMeta> extends IntaveCheckPart<P> {
  private final Class<? extends UserCustomCheckMeta> metaClass;

  public IntaveMetaCheckPart(P parentCheck, Class<? extends UserCustomCheckMeta> metaClass) {
    super(parentCheck);
    this.metaClass = metaClass;
  }

  public META metaOf(Player player) {
    return metaOf(UserRepository.userOf(player));
  }

  public META metaOf(User user) {
    //noinspection unchecked
    return (META) user.customMeta(metaClass);
  }
}
