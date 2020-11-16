package Angel;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
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

class LightAngel {
    private static final Logger log = LogManager.getLogger(LightAngel.class);
    private static FileHandler fileHandler;
    static DiscordBotMain discord;
    static File BADataFile = new File("data/BAdata.json");
    static File nickDataFile = new File("data/nickdata.json");
    static {
        log.info("New Log Starting at time: " + Calendar.getInstance().getTime());
        log.info("TheLightAngel is Starting! Please Wait...");
        try {
            if (!BADataFile.exists()) {
                if (BADataFile.createNewFile()) {
                    FileOutputStream fos = new FileOutputStream(BADataFile);
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(BADataFile));
                    log.info("Successfully Created new Bot Abuse Data File");
                    objectOutputStream.close();
                    fos.close();
                }
            }
            else {
                log.info("Successfully Found Existing Bot Abuse Data File");
            }
            if (!nickDataFile.exists()) {
                if (nickDataFile.createNewFile()) {
                    FileOutputStream fos = new FileOutputStream(nickDataFile);
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(fos);
                    log.info("Successfully Created new Nickname Request Data File");
                    objectOutputStream.close();
                    fos.close();
                }
            }
            else {
                log.info("Successfully Found Existing Nickname Request Data File");
            }
            fileHandler = new FileHandler();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) throws LoginException, IOException {
        boolean isRestart;
        if (args.length == 1) {
            isRestart = Boolean.parseBoolean(args[0]);
        }
        else if (args.length > 1) {
            log.warn("Invalid Number of Arguments on Startup - Reverting to an startup argument of \"false\"");
            isRestart = false;
        }
        else isRestart = false;
        MainConfiguration mainConfig = new ModifyMainConfiguration(fileHandler.getMainConfig());
        mainConfig.initialSetup();
        EmbedHandler embed = new EmbedHandler(mainConfig);
        discord = new DiscordBotMain(isRestart, mainConfig, embed, fileHandler);
        embed.setDiscordInstance(discord);
        Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_EMOJIS);
        Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOTE,
                CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE);
        JDABuilder.create(mainConfig.token, enabledIntents)
                .disableCache(disabledFlags).addEventListeners(discord).setRequestTimeoutRetry(true)
                .setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.NONE).build();
    }
}