package de.jpx3.intave.block.shape.resolve;

/**
 * Thrown by a shape drill when it could not read the real shape of a block.
 * <p>
 * It exists so a failure is not silently turned into "empty" or "full cube": those are
 * plausible-looking shapes, and the pipeline caches whatever the drill returns per
 * block variant, so one failed lookup used to give a block a wrong shape for the rest
 * of the server's uptime -- a player would then be predicted colliding with a wall that
 * is a thin post, or walking through a trapdoor that is solid. Throwing instead makes
 * {@link VariantCachePipe} and {@link CubeMemoryPipe} remember nothing and lets
 * {@link DrillRescuePipe} answer this one query with a neutral shape.
 */
public final class ShapeResolutionFailure extends RuntimeException {
  public ShapeResolutionFailure(String message, Throwable cause) {
    super(message, cause);
  }

  public ShapeResolutionFailure(String message) {
    super(message);
  }
}
