package unix.verse.p2p;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Main entry point
 * <p>Initializes the mod and provides the entry point for the P2P infra</p>
 * @author meowcat767
 * @since 0.1.0
 */
@Mod(uvp2p.MODID)
public class uvp2p {
    public static final String MODID = "uvp2p";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Constructs and inits the mod.
     * @param modEventBus the mod event bus used for registration
     */
    public uvp2p(IEventBus modEventBus) {
        LOGGER.info("uv-p2p initing...");
        // TODO: actual init
    }
}