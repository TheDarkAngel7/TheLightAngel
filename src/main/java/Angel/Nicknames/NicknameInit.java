package Angel.Nicknames;

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

public class NicknameInit implements Runnable {
    private final Logger log = LogManager.getLogger(NicknameInit.class);
    private final boolean commandsSuspended;
    private final MainConfiguration mainConfig;
    private final EmbedHandler embed;
    private final Guild guild;
    private final DiscordBotMain discord;
    private JDA jda;

    private NicknameMain nickFeature;

    public NicknameInit(boolean commandsSuspended, MainConfiguration mainConfig, EmbedHandler embed, Guild guild, DiscordBotMain discord) {
        this.commandsSuspended = commandsSuspended;
        this.mainConfig = mainConfig;
        this.embed = embed;
        this.guild = guild;
        this.discord = discord;
        Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_EMOJIS);
        Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOTE,
                CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE);
        try {
            jda = JDABuilder.create(mainConfig.token, enabledIntents).disableCache(disabledFlags).setRequestTimeoutRetry(true)
                    .setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.NONE).setMaxReconnectDelay(120).build();
            log.info("Nickname Feature JDA Instance Created");
        }
        catch (LoginException e) {
            log.error("Nickname JDA Threw Login Exception During Build");
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            nickFeature = new NicknameMain(commandsSuspended, mainConfig, embed, guild, discord);
            jda.addEventListener(nickFeature);
            log.info("Nickname Feature Added as Event Listener to its JDA instance");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    public NicknameMain getNickFeature() {
        return nickFeature.getThis();
    }
    public long getPing() {
        return jda.getGatewayPing();
    }
    public JDA.Status getStatus() {
        return jda.getStatus();
    }
}
