package Angel.CheckIn;

import java.time.ZonedDateTime;
import java.util.List;

// This is for all check-ins run. All of their results go here.
class CheckInResult {
    private final int id;
    private final ZonedDateTime startDate;
    private final List<CheckInPlayer> players;

    CheckInResult(int id, ZonedDateTime startDate, List<CheckInPlayer> players) {
        this.id = id;
        this.startDate = startDate;
        this.players = players;
    }

    int getId() {
        return id;
    }

    ZonedDateTime getStartDate() {
        return startDate;
    }

    List<CheckInPlayer> getPlayers() {
        return players;
    }
}
