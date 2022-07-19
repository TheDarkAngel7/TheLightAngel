package Angel.CheckIn;

class CheckInPlayer {
    // The ID here is not the same as the check-in ID or player discord ID,
    // this IDs the player in the list of players to check in
    private final int id;
    private final long playerDiscordId;
    private boolean queuedToCheckIn = true;
    private boolean checkedIn = false;
    private String checkInRemainingTime = "--:--";

    CheckInPlayer(int id, long playerDiscordId) {
        this.id = id;
        this.playerDiscordId = playerDiscordId;
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

    long getPlayerDiscordId() {
        return playerDiscordId;
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