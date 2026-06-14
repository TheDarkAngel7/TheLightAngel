package Angel.PlayerList.Cooldown;

import Angel.PlayerList.Exceptions.CooldownConfigDoesNotExist;
import com.google.gson.annotations.Expose;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class SessionCooldownConfigContainer {
    protected static final Logger log = LogManager.getLogger(SessionCooldownConfigContainer.class);

    @Expose
    private final List<SessionCooldownConfiguration> sessionCooldownConfigurations;

    public SessionCooldownConfigContainer() {
        this.sessionCooldownConfigurations = new ArrayList<>();
    }

    public SessionCooldownConfiguration createNewConfiguration(String sessionName) {
        SessionCooldownConfiguration newSessionCooldownConfiguration = new SessionCooldownConfiguration(sessionName);
        sessionCooldownConfigurations.add(newSessionCooldownConfiguration);

        log.info("Successfully Created New Cooldown Configuration for {}'s Session Channel", newSessionCooldownConfiguration.getSessionName());
        return newSessionCooldownConfiguration;
    }

    public SessionCooldownConfiguration getConfiguration(String sessionName) throws CooldownConfigDoesNotExist {
        SessionCooldownConfiguration configuration = sessionCooldownConfigurations.stream()
                .filter(config -> config.getSessionName().equals(sessionName))
                .findFirst()
                .orElseThrow(() -> new CooldownConfigDoesNotExist(sessionName));

        log.info("Successfully Found Existing Cooldown Configuration for {}'s Session Channel", configuration.getSessionName());

        return configuration;
    }
}
