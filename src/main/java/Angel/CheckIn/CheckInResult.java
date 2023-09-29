package Angel.CheckIn;

import net.dv8tion.jda.api.entities.Member;

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

    boolean pardonPlayer(Member m) {
        return pardonPlayer(m.getIdLong());
    }

    boolean pardonPlayer(long excusedDiscordID) {
        int index = 0;
        CheckInPlayer playerInQuestion = null;

        do {
            if (players.get(index).getPlayerDiscordId() == excusedDiscordID) {
                playerInQuestion = players.remove(index);
                break;
            }
            index++;

        } while (index < players.size());
        try {
            if (playerInQuestion.successfullyCheckedIn()) return false;
            else if (!playerInQuestion.isQueuedToCheckIn()) return false;
            else {
                playerInQuestion.removeFromCheckInQueue();
                players.add(index, playerInQuestion);
                return true;
            }
        }
        catch (NullPointerException ex) {
            return false;
        }
    }

    boolean isCanceled() {
        return cancelled;
    }
}
