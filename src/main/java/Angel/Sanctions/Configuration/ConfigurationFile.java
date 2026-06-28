package Angel.Sanctions.Configuration;

import com.google.gson.annotations.Expose;

public class ConfigurationFile {
    @Expose
    private int minAttachments;
    @Expose
    private int minLinks;
    @Expose
    private int antiScamViolationsBanTrigger;
    @Expose
    private int maxAntiScamTimeoutSeconds;
    @Expose
    private String antiScamBanDuration;
    @Expose
    private long suspensionRoleID;
    @Expose
    private long blockedRoleID;

    public long getBlockedRoleID() {
        return blockedRoleID;
    }

    public long getSuspensionRoleID() {
        return suspensionRoleID;
    }

    public int getMinAttachments() {
        return minAttachments;
    }

    public int getMinLinks() {
        return minLinks;
    }

    public int getAntiScamViolationsBanTrigger() {
        return antiScamViolationsBanTrigger;
    }

    public int getMaxAntiScamTimeoutSeconds() {
        return maxAntiScamTimeoutSeconds;
    }

    public String getAntiScamBanDuration() {
        return antiScamBanDuration;
    }
}
