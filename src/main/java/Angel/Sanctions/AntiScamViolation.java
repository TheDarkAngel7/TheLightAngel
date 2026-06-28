package Angel.Sanctions;

import java.time.ZonedDateTime;

public class AntiScamViolation implements SanctionLogic {
    private int violationCount = 1;
    private ZonedDateTime expiryTime;

    public AntiScamViolation() {
        this.expiryTime = ZonedDateTime.now().plusSeconds(sanctionConfig.getMaxAntiScamTimeoutSeconds());
    }

    public boolean hasTimedOut() {
        return ZonedDateTime.now().isAfter(expiryTime);
    }

    public void incrementViolationCount() {
        this.violationCount++;
        this.expiryTime = ZonedDateTime.now().plusSeconds(sanctionConfig.getMaxAntiScamTimeoutSeconds());
    }

    public int getViolationCount() {
        return violationCount;
    }
}
