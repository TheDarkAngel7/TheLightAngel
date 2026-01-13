package Angel.PlayerList;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Session implements PlayerListLogic {
    private final Logger log = LogManager.getLogger(Session.class);

    private final String sessionName;
    private final TextChannel sessionChannel;
    private ZonedDateTime date;

    private List<Player> players;
    // Player List Trouble means LA received an empty player list and this session may be experiencing trouble,
    // If this happens 5 times then we'll put the session into the trouble status
    private int missedScreenshots = 0;
    private boolean isExperiencingScreenshotTrouble = false;
    private SessionStatus status;

    // Session Channel Player List Cooldown

    private boolean playerListCooldownEnabled = false;
    private ZonedDateTime cmdLastUsed;
    private int cooldownDuration = 0;
    private int minNumberOfPlayers = 0;

    public Session(String name, TextChannel sessionChannel, List<Player> players) {
        this.sessionName = name;

        this.sessionChannel = sessionChannel;

        this.date = ZonedDateTime.now();
        this.players = new ArrayList<>(players);
        this.status = SessionStatus.ONLINE;
    }

    public Session setNewPlayers(List<Player> players) {
        this.players = players;
        this.date = ZonedDateTime.now();
        resetListFilter();
        return this;
    }

    public Session setStatus(SessionStatus status) {
        this.status = status;
        return this;
    }

    public Session missedScreenshot() {
        missedScreenshots++;

        if (missedScreenshots >= 5) {
            isExperiencingScreenshotTrouble = true;
        }
        log.warn("{} has missed a screenshot. Count: {} In Trouble: {}", sessionName, missedScreenshots, isExperiencingScreenshotTrouble);
        return this;
    }

    public String getSessionName() {
        return sessionName;
    }

    public TextChannel getSessionChannel() {
        return sessionChannel;
    }

    public ZonedDateTime getLastUpdatedTime() {
        return date;
    }

    public String getLastUpdateTimeString() {
        return getTimerFormatFrom(date);
    }

    public int getPlayerCount() {
        return players.size();
    }

    public List<Player> getPlayerList() {
        return players;
    }

    public PlayerListMessage getPlayerListMessage(Message cmd, boolean sortAlphabetically, boolean useMentions) {
        return getPlayerListMessage(cmd)
                .sortListAlphabetically(sortAlphabetically).useMentions(useMentions);
    }

    public PlayerListMessage getPlayerListMessage(Message cmd) {
        if (playerListCooldownEnabled && !isCooldownActive() && cmd.getChannel().getIdLong() == sessionChannel.getIdLong()) {
            cmdLastUsed = ZonedDateTime.now();
        }

        return new PlayerListMessage(this);
    }

    public Session clearPlayerList() {
        players.clear();
        log.info("Player List for {} has been cleared",  sessionName);
        return this;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public boolean isExperiencingScreenshotTrouble() {
        return isExperiencingScreenshotTrouble;
    }

    public void resetListFilter() {
        this.missedScreenshots = 0;
        this.isExperiencingScreenshotTrouble = false;
        log.info("List Filter has been reset for {}", sessionName);
    }

    // These Methods are related to the cooldown

    public void enablePlayerListCooldown(int cooldownDuration, int minNumberOfPlayers) {
        this.minNumberOfPlayers = minNumberOfPlayers;
        enablePlayerListCooldown(cooldownDuration);
    }

    public void enablePlayerListCooldown(int cooldownDuration) {
        this.cooldownDuration = cooldownDuration;
        playerListCooldownEnabled = true;
        // This is so when the cooldown is enabled it won't immediately be active, but next time the command is used it will be
        this.cmdLastUsed = ZonedDateTime.now().minusMinutes(cooldownDuration + 1);

        sessionChannel.sendMessage("**`" + mainConfig.commandPrefix + "pl` Cooldown has been enabled for this channel.**" +
                "\n\nMinimum Duration Between Commands: **" + cooldownDuration + " Minutes**" +
                (minNumberOfPlayers > 0 ? "\nMinimum Number Of Players: **" + minNumberOfPlayers + "**" : "")).queue();
        log.info("Cooldown has been enabled for #{} - Duration: {} minutes with {} Players Minimum", sessionChannel.getName(), cooldownDuration, minNumberOfPlayers);
    }

    public void disablePlayerListCooldown() {
        playerListCooldownEnabled = false;

        sessionChannel.sendMessage("**`" + mainConfig.commandPrefix + "pl` Cooldown has been disabled for this channel.**").queue();
        log.info("Cooldown has been disabled for #{}", sessionChannel.getName());
    }

    public boolean isCooldownActive() {
        // This answers whether the cooldown in the session channel is currently active
        // Is the Cooldown Setting Enabled and is the current time before the time when the cooldown is over
        return playerListCooldownEnabled && getPlayerCount() >= minNumberOfPlayers &&
                ZonedDateTime.now().isBefore(cmdLastUsed.plusMinutes(cooldownDuration));
    }

    public String getTimerUntilCooldownIsOver() {
        return getTimerFormatTo(cmdLastUsed.plusMinutes(cooldownDuration));
    }

    public int getCooldownDuration() {
        return cooldownDuration;
    }
}
