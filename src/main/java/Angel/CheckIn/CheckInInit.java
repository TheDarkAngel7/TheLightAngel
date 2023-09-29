package Angel.CheckIn;

import Angel.DiscordBotMain;
import Angel.MainConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collection;

public class CheckInInit implements Runnable, MainConfig {
    private final Logger log = LogManager.getLogger(CheckInInit.class);
    private final DiscordBotMain discord;
    private final JDA jda;

    private CheckInMain checkIn;

    public CheckInInit(DiscordBotMain discord) {
        this.discord = discord;

        Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.MESSAGE_CONTENT);
        Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI,
                CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE, CacheFlag.ONLINE_STATUS, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);

        log.info("Attempting to Create Check-In Feature JDA Instance");
        jda = JDABuilder.create(mainConfig.token, enabledIntents).disableCache(disabledFlags).setRequestTimeoutRetry(true)
                .setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.ALL).setMaxReconnectDelay(180).build();
    }

    @Override
    public void run() {
        checkIn = new CheckInMain(discord);
        jda.addEventListener(checkIn);
        log.info("Check-In Feature Added as Event Listener to its JDA instance");
    }

    public CheckInMain getThis() {
        return checkIn;
    }

    public long getPing() {
        return jda.getGatewayPing();
    }
}