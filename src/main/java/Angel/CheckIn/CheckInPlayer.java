package Angel.CheckIn;

import net.dv8tion.jda.api.entities.Member;

class CheckInPlayer {
    // The ID here is not the same as the check-in ID or player discord ID, this IDs the player in the list of players to check-in
    private final int id;
    private final Member player;
    private boolean queuedToCheckIn = true;
    private boolean checkedIn = false;
    private String checkInRemainingTime = "--:--";

    CheckInPlayer(int id, Member player) {
        this.id = id;
        this.player = player;
    }

    void removeFromCheckInQueue() {
        queuedToCheckIn = false;
    }

    void addToCheckInQueue() {
        queuedToCheckIn = true;
    }

    boolean checkIn(String checkInTime) {
        if (queuedToCheckIn && !checkedIn) {
            this.checkInRemainingTime = checkInTime;
            checkedIn = true;
            return true;
        }
        else return false;
    }

    int getId() {
        return id;
    }

    Member getPlayer() {
        return player;
    }

    boolean isQueuedToCheckIn() {
        return queuedToCheckIn;
    }

    boolean successfullyCheckedIn() {
        return checkedIn;
    }

    String getCheckInRemainingTime() {
        return checkInRemainingTime;
    }
}