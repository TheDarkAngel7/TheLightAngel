package Angel.CheckIn;

import Angel.DiscordBotMain;
import Angel.EmbedEngine;
import Angel.MainConfiguration;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collection;

public class CheckInInit implements Runnable {
    private final Logger log = LogManager.getLogger(CheckInInit.class);
    private final MainConfiguration mainConfig;
    private final EmbedEngine embed;
    private final DiscordBotMain discord;
    private final Guild guild;
    private final JDA jda;

    private CheckInMain checkIn;

    public CheckInInit(MainConfiguration mainConfig, EmbedEngine embed, DiscordBotMain discord, Guild guild) {
        this.mainConfig = mainConfig;
        this.embed = embed;
        this.discord = discord;
        this.guild = guild;

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
        checkIn = new CheckInMain(discord, guild, mainConfig, embed);
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