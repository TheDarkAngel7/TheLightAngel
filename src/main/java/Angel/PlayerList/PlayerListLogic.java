package Angel.PlayerList;

import Angel.CommonLogic;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.util.Arrays;
import java.util.Collection;

interface PlayerListLogic extends CommonLogic {
    PlayerListMain playerListMain = new PlayerListMain();
    Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES);
    Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI,
            CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE, CacheFlag.ONLINE_STATUS, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);
    JDA jda = JDABuilder.create(mainConfig.token, enabledIntents).disableCache(disabledFlags).setRequestTimeoutRetry(true)
            .setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.ALL).setMaxReconnectDelay(120).addEventListeners(playerListMain).build();

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
}
