package Angel.PlayerList;

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

public class PlayerListInit implements Runnable, CommonLogic {
    private final Logger log = LogManager.getLogger(PlayerListInit.class);
    Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES);
    Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI,
            CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE, CacheFlag.ONLINE_STATUS, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);
    private PlayerListMain playerList;
    private JDA jda;

    public PlayerListInit() {
        log.info("Attempting to Create a Player List Feature JDA Instance");
    }

    @Override
    public void run() {
        jda = JDABuilder.create(mainConfig.token, enabledIntents).disableCache(disabledFlags).setRequestTimeoutRetry(true)
                .setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.ALL).setMaxReconnectDelay(120).build();
        playerList = new PlayerListMain();
        jda.addEventListener(playerList);
        log.info("Player List Feature Added as Event Listener to its JDA Instance");
    }

    public PlayerListMain getPlayerListFeature() {
        return playerList;
    }

    public long getPing() {
        return jda.getGatewayPing();
    }
}
