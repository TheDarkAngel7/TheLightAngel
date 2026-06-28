package Angel.Sanctions.Database;

import Angel.Sanctions.Exceptions.InvalidExpirationDateException;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

public class BanInfo extends SanctionInfo {
    protected static final Logger log = LogManager.getLogger(BanInfo.class);

    public BanInfo(long targetDiscordID, String duration, String reason) throws InvalidExpirationDateException {
        super(targetDiscordID, duration, reason);
    }

    @Override
    public void applySanction(String duration) {
        UserSnowflake u = UserSnowflake.fromId(getDiscordID());
        Member m = getGuild().getMember(u);
        String effectiveName = m.getEffectiveName();

        try {
            getGuild().ban(u, getReason().toLowerCase().contains("compromised") || getReason().toLowerCase().contains("hacked")? 1 : 0, TimeUnit.HOURS)
                    .reason(getReason()).queue(
                    success -> log.info("[BAN HAMMER] Successfully Banned {} {}",
                            effectiveName, duration.equalsIgnoreCase("Permanently") ? duration : "for " + duration),
                    error -> log.error("[BAN HAMMER] Unable to ban {}", effectiveName, error)
            );
        }
        catch (Exception e) {
            log.error("[BAN HAMMER] Unable to Ban {}", effectiveName, e);
        }
    }

    @Override
    public void reapplySanction() {
        UserSnowflake u = UserSnowflake.fromId(getDiscordID());

        getGuild().ban(u, getReason().contains("Compromised") ? 1 : 0, TimeUnit.HOURS).reason(getReason()).queue(
                success -> log.info("[BAN HAMMER] Successfully Banned Discord ID {} again", getDiscordID()),
                error -> log.error("[BAN HAMMER] Unable to ban Discord ID {} again", getDiscordID(), error)
        );
    }

    @Override
    public void reverseSanction() {
        UserSnowflake u = UserSnowflake.fromId(getDiscordID());

        getGuild().unban(u).reason("Ban Expired: " + getReason()).queue(
                success -> log.info("Successfully Unbanned Discord ID {}", getDiscordID()),
                error -> log.error("Unable to unban Discord ID {}", getDiscordID(), error)
        );
    }

    @Override
    public boolean sanctionApplied() {
        UserSnowflake u = UserSnowflake.fromId(getDiscordID());

        try {
            getGuild().retrieveBan(u).submit().get(5, TimeUnit.SECONDS);
            return true;
        }
        catch (Exception e) {
            log.error("Discord Account {} is not actively banned.", getDiscordID(), e);
            return false;
        }
    }
}
