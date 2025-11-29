package Angel.PlayerList;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Session {
    private final String sessionName;
    private final TextChannel sessionChannel;
    private ZonedDateTime date;

    private List<Player> players;
    // Player List Trouble means LA received an empty player list and this session may be experiencing trouble,
    // If this happens 5 times then we'll put the session into the trouble status
    private int missedScreenshots = 0;
    private boolean isExperiencingScreenshotTrouble = false;
    private SessionStatus status;

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
        return this;
    }

    public String getSessionName() {
        return sessionName;
    }

    public TextChannel getSessionChannel() {
        return sessionChannel;
    }

    public ZonedDateTime getDate() {
        return date;
    }

    public long getLastUpdateInSeconds() {
        return Duration.between(date, ZonedDateTime.now()).getSeconds();
    }

    public int getPlayerCount() {
        return players.size();
    }
    public List<Player> getPlayerList() {
        return players;
    }

    public Session clearPlayerList() {
        players.clear();
        return this;
    }

    public SessionStatus getStatus() {
        return status;
    }
    public boolean isExperiencingScreenshotTrouble() {
        return isExperiencingScreenshotTrouble || getLastUpdateInSeconds() > 180;
    }
    public void resetListFilter() {
        this.missedScreenshots = 0;
        this.isExperiencingScreenshotTrouble = false;
    }
}
