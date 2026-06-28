package Angel.Sanctions.Database;

import Angel.Sanctions.Exceptions.InvalidExpirationDateException;
import Angel.Sanctions.SanctionLogic;
import com.google.gson.annotations.Expose;

import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class SanctionInfo implements SanctionLogic {
    @Expose
    private final long targetDiscordID;
    @Expose
    private ZonedDateTime issuedDate;
    @Expose
    private ZonedDateTime expiryDate;
    @Expose
    private String fullDurationString;
    @Expose
    private String reason;

    public SanctionInfo(long targetDiscordID, String duration, String reason) throws InvalidExpirationDateException {
        this.targetDiscordID = targetDiscordID;
        this.issuedDate = ZonedDateTime.now();
        this.expiryDate = parseExpirationDate(duration, false);
        this.fullDurationString = formatDurationString(duration);
        this.reason = reason;

        applySanction(this.fullDurationString);
    }

    public long getDiscordID() {
        return targetDiscordID;
    }

    public ZonedDateTime getIssuedDate() {
        return issuedDate;
    }

    public ZonedDateTime getExpiryDate() {
        return expiryDate;
    }

    public String getFullDurationString() {
        return fullDurationString;
    }

    public boolean isPermanent() {
        return expiryDate == null;
    }

    public String getReason() {
        return reason;
    }

    public abstract void applySanction(String duration);

    public abstract void reapplySanction();

    public abstract void reverseSanction();

    public abstract boolean sanctionApplied();

    public void extendExpiryDate(String duration) throws InvalidExpirationDateException {
        expiryDate = parseExpirationDate(duration, true);
    }

    public ZonedDateTime parseExpirationDate(String duration, boolean extendExpirationDate) throws InvalidExpirationDateException {
        Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)(mo?|[dhwy])$", Pattern.CASE_INSENSITIVE);

        if (duration == null || duration.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = DURATION_PATTERN.matcher(duration.trim());

        if (!matcher.matches()) {
            throw new InvalidExpirationDateException("Invalid expiration date: " + duration);
        }

        int amount = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();

        // Import Existing Expiration Date if Applicable
        ZonedDateTime expirationDate;

        if (extendExpirationDate) {
            if (isPermanent()) {
                throw new InvalidExpirationDateException("Cannot Extend Permanent Sanction");
            }
            else {
                expirationDate = getExpiryDate();
            }
        }
        else {
            expirationDate = ZonedDateTime.now();
        }

        return switch (unit) {
            case "d" -> expirationDate.plusDays(amount);
            case "h" -> expirationDate.plusHours(amount);
            case "w" -> expirationDate.plusWeeks(amount);
            case "m", "mo" -> expirationDate.plusMonths(amount);
            case "y" -> expirationDate.plusYears(amount);
            default -> throw new InvalidExpirationDateException("Invalid expiration date: " + duration);
        };
    }
}
