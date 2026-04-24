package de.jpx3.intave.share;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.Relative;
import io.netty.buffer.ByteBuf;

import java.io.Serializable;
import java.util.Set;

public final class Rotation implements Serializable {
  public static final StreamCodec<ByteBuf, ByteBuf, Rotation> STREAM_CODEC = StreamCodec.of(
      (buf, rotation) -> {
        buf.writeFloat(rotation.yaw);
        buf.writeFloat(rotation.pitch);
      },
      buf -> new Rotation(buf.readFloat(), buf.readFloat())
  );
  private float yaw, pitch;

  public Rotation(float yaw, float pitch) {
    this.yaw = yaw;
    this.pitch = pitch;
  }

  public float yaw() {
    return yaw;
  }

  public float pitch() {
    return pitch;
  }

  public float distanceTo(Rotation rotation) {
    float yawDistance = MathHelper.distanceInDegrees(yaw, rotation.yaw);
    float pitchDistance = MathHelper.distanceInDegrees(pitch, rotation.pitch);
    return yawDistance + pitchDistance;
  }

  public void setYaw(float yaw) {
    this.yaw = yaw;
  }

  public void setPitch(float pitch) {
    this.pitch = pitch;
  }

  public Rotation add(Rotation rotation) {
    return new Rotation(yaw + rotation.yaw, pitch + rotation.pitch);
  }

  @Override
  public String toString() {
    return "{" + yaw + ", " + pitch + "}";
  }

  public Rotation filtered(Set<Relative> relativeSet) {
    return new Rotation(
      relativeSet.contains(Relative.X_ROT) ? yaw : 0,
      relativeSet.contains(Relative.Y_ROT) ? pitch : 0
    );
  }

  private static final Rotation ZERO = new Rotation(0, 0);

  public static Rotation zero() {
    return ZERO;
  }

  public static Rotation of(float yaw, float pitch) {
    return new Rotation(yaw, pitch);
  }
}
