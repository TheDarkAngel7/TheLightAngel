package Angel.BotAbuse;

import java.util.Date;

class BotAbuseRecord {
    private int id;
    private long discordID;
    private long issuingTeamMember;
    private int repOffenses;
    private Date issuedDate;
    private Date expiryDate;
    private String reason;
    private String proofImage;
    private boolean currentlyBotAbused;

    BotAbuseRecord(int id, long discordID, long issuingTeamMember, int repOffenses, Date issuedDate, Date expiryDate, String reason, String proofImage) {
        this.id = id;
        this.discordID = discordID;
        this.issuingTeamMember = issuingTeamMember;
        this.repOffenses = repOffenses;
        this.issuedDate = issuedDate;
        this.expiryDate = expiryDate;
        this.reason = reason;
        this.proofImage = proofImage;
        this.currentlyBotAbused = true;
    }

    void setDiscordID(long newDiscordID) {
        discordID = newDiscordID;
    }

    void setNewTeamMember(long newTeamMember) {
        issuingTeamMember = newTeamMember;
    }

    void setExpiryDateAsPermanent() {
        expiryDate = null;
    }

    void setReason(String reason) {
        this.reason = reason;
    }

    void setBotAbuseAsExpired() {
        currentlyBotAbused = false;
    }

    int getId() {
        return id;
    }

    long getDiscordID() {
        return discordID;
    }

    long getIssuingTeamMember() {
        return issuingTeamMember;
    }

    int getRepOffenses() {
        return repOffenses;
    }

    Date getIssuedDate() {
        return issuedDate;
    }

    Date getExpiryDate() {
        return expiryDate;
    }

    String getReason() {
        return reason;
    }

    String getProofImage() {
        return proofImage;
    }

    boolean isCurrentlyBotAbused() {
        return currentlyBotAbused;
    }
}
