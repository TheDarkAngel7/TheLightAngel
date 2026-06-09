package Angel.PlayerList.Cooldown;

import com.google.gson.annotations.Expose;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class SessionCooldownConfiguration {
    private final Logger log = LogManager.getLogger(SessionCooldownConfiguration.class);

    @Expose
    private final String sessionName;
    @Expose
    private ZonedDateTime cmdLastUsed;
    @Expose
    private int cooldownDuration;
    @Expose
    private int minNumberOfPlayers;

    public SessionCooldownConfiguration(String sessionName) {
        this.sessionName = sessionName;
        this.cmdLastUsed = null;
        this.cooldownDuration = 0;
        this.minNumberOfPlayers = 0;
        log.debug("SessionCooldownConfiguration created for {}'s Session Channel", sessionName);
    }

    public void setCmdLastUsed(ZonedDateTime cmdLastUsed) {
        this.cmdLastUsed = cmdLastUsed;
        log.debug("cmdLastUsed variable updated to {} for {}'s Session Channel", cmdLastUsed.format(DateTimeFormatter.ISO_ZONED_DATE_TIME), sessionName);
    }

    public void setCooldownDuration(int cooldownDuration) {
        this.cooldownDuration = cooldownDuration;
        log.debug("Cooldown Duration Updated to {} for {}'s Session Channel", cooldownDuration, sessionName);
    }

    public void setMinNumberOfPlayers(int minNumberOfPlayers) {
        this.minNumberOfPlayers = minNumberOfPlayers;
        log.debug("Minimum Number of Players Updated to {} for {}'s Session Channel", minNumberOfPlayers, sessionName);
    }

    public String getSessionName() {
        return sessionName;
    }

    public ZonedDateTime getCmdLastUsed() {
        return cmdLastUsed;
    }

    public int getCooldownDuration() {
        return cooldownDuration;
    }

    public int getMinNumberOfPlayers() {
        return minNumberOfPlayers;
    }
}
