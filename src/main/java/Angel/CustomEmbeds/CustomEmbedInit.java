package Angel.CustomEmbeds;

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

public class CustomEmbedInit implements Runnable {
    private final Logger log = LogManager.getLogger(CustomEmbedInit.class);
    private final MainConfiguration mainConfig;
    private final EmbedEngine embed;
    private final Guild guild;
    private final DiscordBotMain discord;
    private final JDA jda;

    private CustomEmbedMain embedMain;

    public CustomEmbedInit(MainConfiguration mainConfig, EmbedEngine embed, Guild guild, DiscordBotMain discord) {
        this.mainConfig = mainConfig;
        this.embed = embed;
        this.guild = guild;
        this.discord = discord;
        Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
        Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI,
                CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE, CacheFlag.ONLINE_STATUS, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);
        log.info("Attempting to Create Custom Embed JDA Instance");
        jda = JDABuilder.create(mainConfig.token, enabledIntents).disableCache(disabledFlags).setRequestTimeoutRetry(true)
                .setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.ALL).setMaxReconnectDelay(60).build();
    }

    @Override
    public void run() {
        embedMain = new CustomEmbedMain(mainConfig, embed, guild, discord);
        jda.addEventListener(embedMain);
        log.info("Bot Abuse Feature Added as Event Listener to its JDA instance");
    }
    public CustomEmbedMain getFeature() {
        return embedMain;
    }
    public long getPing() {
        return jda.getGatewayPing();
    }
}
