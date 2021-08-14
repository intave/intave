package de.jpx3.intave.detect;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

/**
 * An extension of the default {@link Check} class, providing a {@link User}-specific metadata holder.
 * This feature was originally proposed by Richy, as a trade-off between the one-check-instance-per-user policy
 * and a totalitarian common-pool policy.
 * It aims to reluctantly offer a fast, limited, secure and uncomplicated per-user custom field pool without huge memory compromises.
 * <br>
 * <br>
 * A quick example on how this would look:
 *
 * <pre>{@code
 * public class Example extends MetaCheck<ExampleMeta> {
 *   public Example() {
 *     super("Example", "example", ExampleMeta.class);
 *   }
 *
 *   public void execution(Player player) {
 *      ExampleMeta meta = metaOf(player);
 *      meta.imUniqueForEveryPlayer++;
 *   }
 *
 *   // every user will hold his own instance of this ExampleClass
 *   public static class ExampleMeta extends CheckCustomMetadata {
 *     public int imUniqueForEveryPlayer = 0;
 *   }
 * }
 * }</pre>
 *
 * The meta class must be declared as type parameter M,
 * its {@code class} must be passed in the {@link MetaCheck#MetaCheck(String, String, Class)} constructor,
 * and it must be a subclass of {@link CheckCustomMetadata}. Make sure it either has no constructor (best) or a public,
 * empty constructor.
 * <br>
 * <br>
 * The {@link MetaCheck#metaOf(User)} or the {@link MetaCheck#metaOf(Player)} method are used to access the metadata holder.
 *
 * @param <M> the meta class
 * @see Check
 * @see CheckCustomMetadata
 * @see User#checkMetadata(Class)
 * @see MetaCheckPart
 */
public abstract class MetaCheck<M extends CheckCustomMetadata> extends Check {
  private final Class<? extends CheckCustomMetadata> metaClass;

  public MetaCheck(String checkName, String configurationName, Class<M> metaClass) {
    super(checkName, configurationName);
    this.metaClass = metaClass;
  }

  protected M metaOf(Player player) {
    return metaOf(UserRepository.userOf(player));
  }

  public M metaOf(User user) {
    //noinspection unchecked
    return (M) user.checkMetadata(metaClass);
  }
}
