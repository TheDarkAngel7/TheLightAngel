package Angel.BotAbuse;

import java.time.ZonedDateTime;

class BotAbuseRecord {
    private int id;
    private long discordID;
    private long issuingTeamMember;
    private int repOffenses;
    private ZonedDateTime issuedDate;
    private ZonedDateTime expiryDate;
    private String reason;
    private String proofImage;

    BotAbuseRecord(int id, long discordID, long issuingTeamMember, int repOffenses, ZonedDateTime issuedDate, ZonedDateTime expiryDate, String reason, String proofImage) {
        this.id = id;
        this.discordID = discordID;
        this.issuingTeamMember = issuingTeamMember;
        this.repOffenses = repOffenses;
        this.issuedDate = issuedDate;
        this.expiryDate = expiryDate;
        this.reason = reason;
        this.proofImage = proofImage;
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

    ZonedDateTime getIssuedDate() {
        return issuedDate;
    }

    ZonedDateTime getExpiryDate() {
        return expiryDate;
    }

    String getReason() {
        return reason;
    }

    String getProofImage() {
        return proofImage;
    }
}
