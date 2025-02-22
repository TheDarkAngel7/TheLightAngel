package Angel.Nicknames;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collection;

public class NicknameInit implements Runnable, NickLogic {
    private final Logger log = LogManager.getLogger(NicknameInit.class);
    private final boolean commandsSuspended;
    private final JDA jda;

    public NicknameInit(boolean commandsSuspended) {
        this.commandsSuspended = commandsSuspended;
        Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
        Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI,
                CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE, CacheFlag.ONLINE_STATUS, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);
        log.info("Attempting to Create Nickname Feature JDA Instance");
        jda = JDABuilder.create(mainConfig.token, enabledIntents).disableCache(disabledFlags).setRequestTimeoutRetry(true)
                .setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.ALL).setMaxReconnectDelay(120).build();
    }

    @Override
    public void run() {
        nickFeature.setCommandsSuspended(commandsSuspended);
        jda.addEventListener(nickFeature);
        log.info("Nickname Feature Added as Event Listener to its JDA instance");
    }
    public NicknameMain getNickFeature() {
        return nickFeature.getThis();
    }
    public long getPing() {
        return jda.getGatewayPing();
    }
}
