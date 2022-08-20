package de.jpx3.intave.check;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import org.bukkit.entity.Player;

/**
 * An extension of the default {@link CheckPart} class, providing a {@link User}-specific metadata holder.
 * This approach was chosen as a trade-off between the one-check-instance-per-user policy
 * and a full-common-pool policy.
 * It aims to reluctantly offer a fast, secure, scalable and easy-to-use per-{@link User} meta pool, outside
 * the {@link MetadataBundle}.
 * <br>
 * <br>
 * A quick example on how this would look:
 * <pre>{@code
 * public class ExamplePart extends MetaCheckPart<Example, ExamplePartMeta> {
 *   public ExamplePart(Example example) {
 *     super(example, ExamplePartMeta.class);
 *   }
 *
 *   public void execution(Player player) {
 *      ExamplePartMeta meta = metaOf(player);
 *      meta.imUniqueForEveryPlayer++;
 *   }
 *
 *   // every user will hold his own instance of this ExampleClass
 *   public static class ExamplePartMeta extends CheckCustomMetadata {
 *     public int imUniqueForEveryPlayer = 0;
 *   }
 * }
 * }</pre>
 * The meta class must be declared as type parameter M,
 * its {@code class} must be passed in the {@link MetaCheckPart#MetaCheckPart(Check, Class)} constructor,
 * and it must be a subclass of {@link CheckCustomMetadata}. Make sure it has a public, empty constructor - explicitly or implicitly.
 * <br>
 * <br>
 * The {@link MetaCheckPart#metaOf(User)} or the {@link MetaCheckPart#metaOf(Player)} method are used to access the metadata holder.
 *
 * @param <M> the meta type
 * @see MetaCheck
 * @see Check
 * @see CheckCustomMetadata
 * @see User#checkMetadata(Class)
 */
public abstract class MetaCheckPart<P extends Check, M extends CheckCustomMetadata> extends CheckPart<P> {
  private final Class<? extends M> metaClass;

  protected MetaCheckPart(P parentCheck, Class<? extends M> metaClass) {
    super(parentCheck);
    this.metaClass = metaClass;
  }

  /**
   * Retrieve the meta instance of the passed meta class for the given {@link Player}.<br>
   * This method is effectively equal to {@code
   * metaOf(UserRepository.userOf(player));
   * }
   *
   * @param player the player to perform a linkage lookup on
   * @return the meta instance
   */
  public M metaOf(Player player) {
    return metaOf(UserRepository.userOf(player));
  }

  /**
   * Retrieve the meta instance of the passed meta class for the given {@link User}
   *
   * @param user the user to retrieve the meta from
   * @return the meta instance
   */
  public M metaOf(User user) {
    //noinspection unchecked
    return (M) user.checkMetadata(metaClass);
  }
}
