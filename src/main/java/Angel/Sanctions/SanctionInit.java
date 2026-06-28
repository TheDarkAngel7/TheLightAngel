package Angel.Sanctions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SanctionInit implements Runnable, SanctionLogic {
    private final Logger log = LogManager.getLogger(SanctionInit.class);

    public SanctionInit() {
        log.info("Attempting to Create a Sanctions Feature JDA Instance");
    }

    @Override
    public void run() {
        log.info("Sanctions Feature Added as Event Listener to its JDA Instance");
    }

    public SanctionMain getSanctionFeature() {
        return sanctionMain;
    }

    public long getPing() {
        return jda.getGatewayPing();
    }
}
