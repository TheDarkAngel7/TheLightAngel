package Angel.CheckIn;

import java.time.ZonedDateTime;
import java.util.List;

// This is for all check-ins run. All of their results go here.
class CheckInResult {
    private final int id;
    private final ZonedDateTime endDate;
    private final List<CheckInPlayer> players;
    private final boolean cancelled;

    CheckInResult(int id, ZonedDateTime endDate, List<CheckInPlayer> players, boolean isCancelled) {
        this.id = id;
        this.endDate = endDate;
        this.players = players;
        this.cancelled = isCancelled;
    }

    int getId() {
        return id;
    }

    ZonedDateTime getEndDate() {
        return endDate;
    }

    List<CheckInPlayer> getPlayers() {
        return players;
    }

    boolean isCancelled() {
        return cancelled;
    }
}
