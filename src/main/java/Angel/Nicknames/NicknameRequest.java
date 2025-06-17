package Angel.Nicknames;

import java.time.ZoneId;
import java.time.ZonedDateTime;

class NicknameRequest implements NickLogic {
    private final int requestID;
    private final ZonedDateTime requestTime = ZonedDateTime.now(ZoneId.of("UTC"));
    private final long discordID;
    private final String oldName;
    private final String newName;
    // This Message is specifically for the notification in the team channel
    private long teamNotificationChannelID;
    private long teamNotificationID;


    NicknameRequest(int requestID, long discordID, String oldName, String newName) {
        this.requestID = requestID;
        this.discordID = discordID;
        this.oldName = oldName;
        this.newName = newName;

    }

    int getRequestID() {
        return requestID;
    }

    long getDiscordID() {
        return discordID;
    }

    ZonedDateTime getRequestTime() {
        return requestTime;
    }

    String getOldName() {
        return oldName;
    }

    String getNewName() {
        return newName;
    }
    long getTeamNotificationChannelID() {
        return teamNotificationChannelID;
    }
    long getTeamNotification() {
        return teamNotificationID;
    }

    NicknameRequest setTeamNotification(long channelID, long messageID) {
        this.teamNotificationChannelID = channelID;
        this.teamNotificationID = messageID;
        return this;
    }
}
