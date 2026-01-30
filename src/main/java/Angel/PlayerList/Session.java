package Angel.PlayerList;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Session implements PlayerListLogic {
    private final Logger log = LogManager.getLogger(Session.class);

    private final String sessionName;
    private final TextChannel sessionChannel;

    private ZonedDateTime playerListLastUpdated;
    private List<Player> players;
    private BufferedImage playerListImage;

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

    public Session(String name, TextChannel sessionChannel, List<Player> players, BufferedImage playerListImage) {
        this.sessionName = name;

        this.sessionChannel = sessionChannel;

        this.playerListLastUpdated = ZonedDateTime.now();
        this.players = new ArrayList<>(players);
        this.playerListImage = playerListImage;
        this.status = SessionStatus.ONLINE;
    }

    public Session setNewPlayers(List<Player> players, BufferedImage playerListImage) {
        this.players = players;
        this.playerListImage = playerListImage;
        this.playerListLastUpdated = ZonedDateTime.now();
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
        return playerListLastUpdated;
    }

    public BufferedImage getPlayerListImage() {
        return playerListImage;
    }

    public String getLastUpdateTimeString() {
        return getTimerFormatFrom(playerListLastUpdated);
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

    public void clearPlayerList() {
        players.clear();
        log.info("Player List for {} has been cleared",  sessionName);
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
    // If the Session Channel is Accessible to the User

    public boolean isSessionChannelAccessible(Member m) {
        if (m == null) return false;

        return m.hasPermission(sessionChannel, Permission.VIEW_CHANNEL);
    }

    public boolean isSessionChannelAccessible(long targetDiscordID) {
        Member m = getGuild().getMemberById(targetDiscordID);

        return isSessionChannelAccessible(m);
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
