package Angel.PlayerList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlayerListInit implements Runnable, PlayerListLogic {
    private final Logger log = LogManager.getLogger(PlayerListInit.class);

    public PlayerListInit() {
        log.info("Attempting to Create a Player List Feature JDA Instance");
    }

    @Override
    public void run() {
        log.info("Player List Feature Added as Event Listener to its JDA Instance");
    }

    public PlayerListMain getPlayerListFeature() {
        return playerListMain;
    }

    public long getPing() {
        return jda.getGatewayPing();
    }
}
