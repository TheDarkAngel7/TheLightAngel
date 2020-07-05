package Angel;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.concurrent.TimeoutException;

class LightAngel {
    public static final Logger log = LogManager.getLogger(LightAngel.class);
    static DiscordBotMain discord;
    static File dataFile = new File("data/data.json");
    static {
        log.info("New Log Starting at time: " + Calendar.getInstance().getTime());
        log.info("TheLightAngel is Starting! Please Wait...");
        try {
            if (!dataFile.exists()) {
                if (dataFile.createNewFile()) {
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(dataFile));
                    log.info("Successfully Created new Data File");
                    objectOutputStream.close();
                }
            }
            else {
                log.info("Successfully Found Existing Data File");
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) throws LoginException, IOException, TimeoutException {
        boolean isRestart = Boolean.parseBoolean(args[0]);
        discord = new DiscordBotMain(isRestart);

        Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES);
        Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOTE,
                CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE);
        JDABuilder.create(discord.botConfig.token, enabledIntents)
                .disableCache(disabledFlags).addEventListeners(discord).setAutoReconnect(true).build();
    }
}