package Angel.BotAbuse;

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
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeoutException;

public class BotAbuseInit implements Runnable {
    private final Logger log = LogManager.getLogger(BotAbuseInit.class);
    private final boolean commandsSuspended;
    private final boolean isRestart;
    private final MainConfiguration mainConfig;
    private final EmbedHandler embed;
    private final Guild guild;
    private final DiscordBotMain discord;
    public JDA jda;

    BotAbuseMain baFeature;

    public BotAbuseInit(boolean commandsSuspended, boolean isRestart, MainConfiguration mainConfig, EmbedHandler embed, Guild guild, DiscordBotMain discord) {
        this.commandsSuspended = commandsSuspended;
        this.isRestart = isRestart;
        this.mainConfig = mainConfig;
        this.embed = embed;
        this.guild = guild;
        this.discord = discord;
        Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_EMOJIS);
        Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOTE,
                CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE);
        try {
            jda = JDABuilder.create(mainConfig.token, enabledIntents).disableCache(disabledFlags)
                    .setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.NONE).setMaxReconnectDelay(60).build();
            log.info("Bot Abuse Feature JDA Instance Created");
        }
        catch (LoginException e) {
            log.error("Bot Abuse JDA Threw Login Exception During Build");
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            baFeature = new BotAbuseMain(commandsSuspended, isRestart, mainConfig, embed, guild, discord);
            jda.addEventListener(baFeature);
            log.info("Bot Abuse Feature Added as Event Listener to its JDA instance");
        }
        catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }
    public BotAbuseMain getBaFeature() {
        return baFeature.getThis();
    }
    public long getPing() {
        return jda.getGatewayPing();
    }
}