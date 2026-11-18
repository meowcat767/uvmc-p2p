package unix.verse.p2p.network.protocol;

/**
 * Global constants for the uv-p2p wire protocol.
 * @author meowcat767
 * @since 0.1.0
 */

public class ProtocolConstants {
    /**
     * Magic identifier
     */
    public static final int MAGIC = 0x55565032; // UVP2

    /**
     * Maximum allowed size of an individual protocol message
     */
    public static final int MAX_MESSAGE_SIZE= 1_048_576; // 1 MiB

    /**
     * Maximum allowed payload size for handshake messages
     */
    public static final int MAX_HANDSHAKE_SIZE = 16_384; // 16 KiB

    /**
     * Maximum number of capability identifiers sent during handshake
     */
    public static final int MAX_CAPABILITIES = 64;

    /**
     * Maximum length of a capability identifier
     */
    public static final int MAX_CAPABILITY_LENGTH = 64;

    private ProtocolConstants() {
        // Utility class
    }
}
