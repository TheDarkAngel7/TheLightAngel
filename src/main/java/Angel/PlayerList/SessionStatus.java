package Angel.PlayerList;

public enum SessionStatus {
    ONLINE("Online"),
    /*
   A Session that was just brought back online would have several in game phone calls to the host
   These cause in game prompts to be displayed in place of the player list and can confuse the bot
   This is a temporary status given to a host that just came back online, then after 5 minutes it is removed.
     */
    FRESH_ONLINE("Fresh Online"),
    // These are the offline and restart status for different embeds for the bot to print.
    OFFLINE("Offline"),
    RESTARTING("Restarting"),
    RESTART_SOON("Restarting Soon"),
    RESTART_MOD("Restarting Due to Modded Elements");

    private final String statusString;

    SessionStatus(String name) {
        this.statusString = name;
    }

    public String getStatusString() {
        return statusString;
    }
}
