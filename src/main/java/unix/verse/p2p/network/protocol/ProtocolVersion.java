package unix.verse.p2p.network.protocol;

/**
 * Represents a uv-p2p wire protocol version.
 * @param major major version
 * @param minor minor version
 * @author meowcat767
 * @since 0.1.0
 */
public record ProtocolVersion(int major, int minor) implements Comparable<ProtocolVersion> {
    /**
     * Current protocol version
     * @param major
     * @param minor
     */
    public static final ProtocolVersion CURRENT = new ProtocolVersion(1, 0);

    /**
     * Minimum supported protocol version
     * @param major
     * @param minor
     */
    public static final ProtocolVersion MINIMUM = new ProtocolVersion(1, 0);

    public ProtocolVersion {
        if (major <0 || minor < 0) {
            throw new IllegalArgumentException("Protocol version values cannot be negative");
        }
    }

    /**
     * Determines whether this version is compatible with another.
     * For protocol version 1, compatibility requires the same major version.
     * @param other version to compare
     * @return true of compatible
     */
    public boolean isCompatibleWith(ProtocolVersion other) {
        return other != null && major == other.major;
    }

    @Override
    public int compareTo(ProtocolVersion other) {
        int majorComparison = Integer.compare(this.major, other.major);
        if (majorComparison != 0) {
            return majorComparison;
        }
        return Integer.compare(minor, other.minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
