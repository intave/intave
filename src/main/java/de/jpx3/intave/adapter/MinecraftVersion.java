package de.jpx3.intave.adapter;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MinecraftVersion implements Comparable<MinecraftVersion> {
	private static final Pattern VERSION_PATTERN;

	public static final StreamCodec<ByteBuf, ByteBuf, MinecraftVersion> STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.INTEGER, MinecraftVersion::getMajor,
		ByteBufStreamCodecs.INTEGER, MinecraftVersion::getMinor,
		ByteBufStreamCodecs.INTEGER, MinecraftVersion::getBuild,
		MinecraftVersion::new
	);

	private static MinecraftVersion currentVersion;
	private final int major;
	private final int minor;
	private final int build;
	private final String development;
	private final SnapshotVersion snapshot;
	private volatile Boolean atCurrentOrAbove;

	public MinecraftVersion(Server server) {
		this(extractVersion(server.getVersion()));
	}

	public MinecraftVersion(String versionOnly) {
		this(versionOnly, true);
	}

	private MinecraftVersion(String versionOnly, boolean parseSnapshot) {
		String[] section = versionOnly.split("-");
		SnapshotVersion snapshot = null;
		int[] numbers = new int[3];

		try {
			numbers = this.parseVersion(section[0]);
		} catch (NumberFormatException cause) {
			if (!parseSnapshot) {
				throw cause;
			}

			try {
				snapshot = new SnapshotVersion(section[0]);
				SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
				MinecraftVersion latest = new MinecraftVersion("26.1", false);
				boolean newer = snapshot.getSnapshotDate().compareTo(format.parse("2026-03-24")) > 0;
				numbers[0] = latest.getMajor();
				numbers[1] = latest.getMinor() + (newer ? 1 : -1);
			} catch (Exception e) {
				throw new IllegalStateException("Cannot parse " + section[0], e);
			}
		}

		this.major = numbers[0];
		this.minor = numbers[1];
		this.build = numbers[2];
		this.development = section.length > 1 ? section[1] : (snapshot != null ? "snapshot" : null);
		this.snapshot = snapshot;
	}

	public MinecraftVersion(int major, int minor, int build) {
		this(major, minor, build, null);
	}

	public MinecraftVersion(int major, int minor, int build, String development) {
		this.major = major;
		this.minor = minor;
		this.build = build;
		this.development = development;
		this.snapshot = null;
	}

	public com.comphenix.protocol.utility.MinecraftVersion toProtocolLibVersion() {
		return new com.comphenix.protocol.utility.MinecraftVersion(this.major, this.minor, this.build);
	}

	public static String extractVersion(String text) {
		Matcher version = VERSION_PATTERN.matcher(text);
		if (version.matches() && version.group(1) != null) {
			return version.group(1);
		} else {
			throw new IllegalStateException("Cannot parse version String '" + text + "'");
		}
	}

	public static MinecraftVersion fromServerVersion(String serverVersion) {
		return new MinecraftVersion(extractVersion(serverVersion));
	}

	public static MinecraftVersion current() {
		if (currentVersion == null) {
			currentVersion = fromServerVersion(Bukkit.getVersion());
		}

		return currentVersion;
	}

	public static void setCurrent(MinecraftVersion version) {
		currentVersion = version;
		com.comphenix.protocol.utility.MinecraftVersion.setCurrentVersion(
			version.toProtocolLibVersion()
		);
	}

	private static boolean atOrAbove(MinecraftVersion version) {
		return current().isAtLeast(version);
	}

	private int[] parseVersion(String version) {
		String[] elements = version.split("\\.");
		int[] numbers = new int[3];
		if (elements.length < 1) {
			throw new IllegalStateException("Corrupt MC version: " + version);
		} else {
			for (int i = 0; i < Math.min(numbers.length, elements.length); ++i) {
				try {
					numbers[i] = Integer.parseInt(elements[i].trim());
				} catch (NumberFormatException e) {
					throw new IllegalStateException("Corrupt MC version: " + version, e);
				}
			}

			return numbers;
		}
	}

	public int getMajor() {
		return this.major;
	}

	public int getMinor() {
		return this.minor;
	}

	public int getBuild() {
		return this.build;
	}

	public String getDevelopmentStage() {
		return this.development;
	}

	public SnapshotVersion getSnapshot() {
		return this.snapshot;
	}

	public boolean isSnapshot() {
		return this.snapshot != null;
	}

	public boolean atOrAbove() {
		if (this.atCurrentOrAbove == null) {
			this.atCurrentOrAbove = atOrAbove(this);
		}

		return this.atCurrentOrAbove;
	}

	public String getVersion() {
		return this.getDevelopmentStage() == null ? String.format("%s.%s.%s", this.getMajor(), this.getMinor(), this.getBuild()) : String.format("%s.%s.%s-%s%s", this.getMajor(), this.getMinor(), this.getBuild(), this.getDevelopmentStage(), this.isSnapshot() ? this.snapshot : "");
	}

	@Override
	public int compareTo(MinecraftVersion o) {
		if (o == null) {
			return 1;
		}

		int cmp = Integer.compare(this.getMajor(), o.getMajor());
		if (cmp != 0) return cmp;

		cmp = Integer.compare(this.getMinor(), o.getMinor());
		if (cmp != 0) return cmp;

		cmp = Integer.compare(this.getBuild(), o.getBuild());
		if (cmp != 0) return cmp;

		// development stage: Ordering.natural().nullsLast()
		String aDev = this.getDevelopmentStage();
		String bDev = o.getDevelopmentStage();
		if (aDev != bDev) { // handles when one is null and/or different
			if (aDev == null) return 1; // nulls last => null > non-null
			if (bDev == null) return -1;
			cmp = aDev.compareTo(bDev);
			if (cmp != 0) return cmp;
		}

		// snapshot: Ordering.natural().nullsFirst()
		SnapshotVersion aSnap = this.getSnapshot();
		SnapshotVersion bSnap = o.getSnapshot();
		if (aSnap != bSnap) {
			if (aSnap == null) return -1; // nulls first => null < non-null
			if (bSnap == null) return 1;
			return aSnap.compareTo(bSnap);
		}

		return 0;
	}

	public boolean isAtLeast(MinecraftVersion other) {
		if (other == null) {
			return false;
		} else {
			return this.compareTo(other) >= 0;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		} else if (obj == this) {
			return true;
		} else if (!(obj instanceof MinecraftVersion)) {
			return false;
		} else {
			MinecraftVersion other = (MinecraftVersion) obj;
			return this.getMajor() == other.getMajor() && this.getMinor() == other.getMinor() && this.getBuild() == other.getBuild() && Objects.equals(this.getDevelopmentStage(), other.getDevelopmentStage());
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(new Object[]{this.getMajor(), this.getMinor(), this.getBuild()});
	}

	@Override
	public String toString() {
		return String.format("(MC: %s)", this.getVersion());
	}

	static {
//    LATEST = v26_1;
		VERSION_PATTERN = Pattern.compile(".*\\(.*MC.\\s*([a-zA-Z0-9\\-.]+).*");
	}
}