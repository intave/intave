package de.jpx3.intave.adapter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SnapshotVersion implements Comparable<SnapshotVersion> {
	private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("(\\d{2}w\\d{2})([a-z])");
	private final Date snapshotDate;
	private final int snapshotWeekVersion;
	private transient String rawString;

	public SnapshotVersion(String version) {
		Matcher matcher = SNAPSHOT_PATTERN.matcher(version.trim());
		if (matcher.matches()) {
			try {
				this.snapshotDate = getDateFormat().parse(matcher.group(1));
				this.snapshotWeekVersion = matcher.group(2).charAt(0) - 97;
				this.rawString = version;
			} catch (ParseException e) {
				throw new IllegalArgumentException("Date implied by snapshot version is invalid.", e);
			}
		} else {
			throw new IllegalArgumentException("Cannot parse " + version + " as a snapshot version.");
		}
	}

	private static SimpleDateFormat getDateFormat() {
		SimpleDateFormat format = new SimpleDateFormat("yy'w'ww", Locale.US);
		format.setLenient(false);
		return format;
	}

	public int getSnapshotWeekVersion() {
		return this.snapshotWeekVersion;
	}

	public Date getSnapshotDate() {
		return this.snapshotDate;
	}

	public String getSnapshotString() {
		if (this.rawString == null) {
			Calendar current = Calendar.getInstance(Locale.US);
			current.setTime(this.snapshotDate);
			this.rawString = String.format("%02dw%02d%s", current.get(1) % 100, current.get(3), (char) (97 + this.snapshotWeekVersion));
		}

		return this.rawString;
	}

	public int compareTo(SnapshotVersion o) {
		if (o == null) {
			return 1;
		}

		int cmp = this.snapshotDate.compareTo(o.getSnapshotDate());
		if (cmp != 0) {
			return cmp;
		}

		return Integer.compare(this.snapshotWeekVersion, o.getSnapshotWeekVersion());
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		} else if (!(obj instanceof SnapshotVersion)) {
			return false;
		} else {
			SnapshotVersion other = (SnapshotVersion) obj;
			return Objects.equals(this.snapshotDate, other.getSnapshotDate()) && this.snapshotWeekVersion == other.getSnapshotWeekVersion();
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.snapshotDate, this.snapshotWeekVersion);
	}

	@Override
	public String toString() {
		return this.getSnapshotString();
	}
}
