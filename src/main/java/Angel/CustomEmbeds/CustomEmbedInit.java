package Angel.CustomEmbeds;

import Angel.CommonLogic;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collection;

public class CustomEmbedInit implements Runnable, CommonLogic {
    private final Logger log = LogManager.getLogger(CustomEmbedInit.class);
    private final JDA jda;

    private CustomEmbedMain embedMain;

    public CustomEmbedInit() {
        Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
        Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI,
                CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE, CacheFlag.ONLINE_STATUS, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);
        log.info("Attempting to Create Custom Embed JDA Instance");
        jda = JDABuilder.create(mainConfig.token, enabledIntents).disableCache(disabledFlags).setRequestTimeoutRetry(true)
                .setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.ALL).setMaxReconnectDelay(60).build();
    }

    @Override
    public void run() {
        embedMain = new CustomEmbedMain();
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
