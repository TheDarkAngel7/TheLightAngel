package Angel.CustomEmbeds;

import Angel.DiscordBotMain;
import Angel.EmbedHandler;
import Angel.MainConfiguration;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.security.auth.login.LoginException;
import java.util.Arrays;
import java.util.Collection;

public class CustomEmbedInit implements Runnable {
    private final Logger log = LogManager.getLogger(CustomEmbedInit.class);
    private final MainConfiguration mainConfig;
    private final EmbedHandler embed;
    private final Guild guild;
    private final DiscordBotMain discord;
    private JDA jda;

    private CustomEmbedMain embedMain;

    public CustomEmbedInit(MainConfiguration mainConfig, EmbedHandler embed, Guild guild, DiscordBotMain discord) {
        this.mainConfig = mainConfig;
        this.embed = embed;
        this.guild = guild;
        this.discord = discord;
        Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES);
        Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOTE,
                CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE);
        try {
            jda = JDABuilder.create(mainConfig.token, enabledIntents).disableCache(disabledFlags).setRequestTimeoutRetry(true)
                    .setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.ALL).setMaxReconnectDelay(60).build();
            log.info("Custom Embed JDA Instance Created");
        }
        catch (LoginException e) {
            log.error("Custom Embed JDA Threw Login Exception During Build", e);
        }
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
