package Angel.Sanctions;

import Angel.CommonLogic;
import Angel.Sanctions.Configuration.ConfigurationFile;
import Angel.Sanctions.Configuration.SanctionConfigManager;
import Angel.Sanctions.Database.SanctionDatabaseContainer;
import Angel.Sanctions.Database.SanctionDatabaseManager;
import Angel.Sanctions.Exceptions.InvalidExpirationDateException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface SanctionLogic extends CommonLogic {
    SanctionMain sanctionMain = new SanctionMain();
    AntiScamListener antiScamListener = new AntiScamListener();

    SanctionConfigManager sanctionConfigManager = new SanctionConfigManager();
    ConfigurationFile sanctionConfig = sanctionConfigManager.getConfig();

    SanctionDatabaseManager databaseManager = new SanctionDatabaseManager();
    SanctionDatabaseContainer databaseContainer = databaseManager.loadDatabase();

    Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES);
    Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS,
            CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE, CacheFlag.ONLINE_STATUS, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);
    JDA jda = JDABuilder.create(mainConfig.token, enabledIntents).disableCache(disabledFlags)
            .setRequestTimeoutRetry(true).setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.ALL).setMaxReconnectDelay(120)
            .addEventListeners(sanctionMain, antiScamListener).build();



    @Override
    default Guild getGuild() {
        while (true) {
            try {
                return jda.awaitReady().getGuilds().getFirst();
            }
            catch (InterruptedException e) {
                aue.logCaughtException(Thread.currentThread(), e);
            }
        }
    }
    default TextChannel getTeamBureauChannel() {
        return getGuild().getTextChannelsByName("team_bureau", true).getFirst();
    }
    default String formatDurationString(String duration) {
        Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)(mo?|[dhwy])$", Pattern.CASE_INSENSITIVE);

        if (duration == null || duration.trim().isEmpty()) {
            return "Permanently";
        }

        Matcher matcher = DURATION_PATTERN.matcher(duration.trim());

        if (!matcher.matches()) {
            throw new InvalidExpirationDateException("Invalid expiration date: " + duration);
        }

        int amount = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();

        String unitName = switch (unit) {
            case "h"       -> amount == 1 ? "Hour"  : "Hours";
            case "d"       -> amount == 1 ? "Day"   : "Days";
            case "w"       -> amount == 1 ? "Week"  : "Weeks";
            case "m", "mo" -> amount == 1 ? "Month" : "Months";
            case "y"       -> amount == 1 ? "Year"  : "Years";
            default -> throw new InvalidExpirationDateException("Invalid expiration date: " + duration);
        };

        return amount + " " + unitName;
    }
}
