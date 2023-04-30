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
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;

class LightAngel {
    private static final Logger log = LogManager.getLogger(LightAngel.class);
    private static FileHandler fileHandler;
    static DiscordBotMain discord;
    static File BADataFile = new File("data/BAdata.json");
    static File nickDataFile = new File("data/nickdata.json");
    static File checkInDataFile = new File("data/checkIndata.json");
    static {
        Thread.currentThread().setName("Main Thread");
        Thread.currentThread().setUncaughtExceptionHandler(new AngelExceptionHandler());
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
                    log.info("Successfully Created new Nickname Data File");
                    objectOutputStream.close();
                    fos.close();
                }
            }
            else {
                log.info("Successfully Found Existing Nickname Data File");
            }
            if (!checkInDataFile.exists()) {
                if (checkInDataFile.createNewFile()) {
                    FileOutputStream fos = new FileOutputStream(nickDataFile);
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(fos);
                    log.info("Successfully Created new Check-In Data File");
                    objectOutputStream.close();
                    fos.close();
                }
            }
            else {
                log.info("Successfully Found Existing Check-In Data File");
            }
            fileHandler = new FileHandler();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) throws LoginException, IOException {
        boolean isRestart;
        int restartValue = 0;
        if (args.length == 1) {
            // restartValue of 0 indicates Cold Start
            // restartValue of 1 indicates Restart
            // restartValue of 2 indicates Silent Start/Restart
            if (args[0].equalsIgnoreCase("true") || args[0].equalsIgnoreCase("false")) {
                isRestart = Boolean.parseBoolean(args[0]);
                if (!isRestart) restartValue = 0;
                else restartValue = 1;
            }
            else if (args[0].equalsIgnoreCase("-s") || args[0].equalsIgnoreCase("silent")) {
                restartValue = 2;
            }
            else if (args[0].equalsIgnoreCase("-min") || args[0].equalsIgnoreCase("minimized")) {
                new ProcessBuilder("cmd", "/c", "start", "/MIN", "java", "-jar", "-Dlog4j.configurationFile=./log4j2.properties", "TheLightAngel.jar", "false").start();
                System.exit(1);
            }
            else restartValue = 0;
        }
        else if (args.length > 1) {
            log.warn("Invalid Number of Arguments on Startup - Reverting to an restartValue of \"false\"");
            restartValue = 0;
        }
        else {
            try {
                if (!new FileCreator().startup()) {
                    FileWarningMessage warningMessage = new FileWarningMessage();
                    warningMessage.startup();

                    while (true) {
                        try { Thread.sleep(500); } catch (InterruptedException ex) {}
                        if (!warningMessage.isContinueBoot() && !warningMessage.isTimerRunning()) {
                            System.exit(1);
                        }
                        else if (warningMessage.isContinueBoot()) {
                            break;
                        }
                    }
                }
                new ProcessBuilder("cmd", "/c", "start", "java", "-jar", "-Dlog4j.configurationFile=./log4j2.properties", "TheLightAngel.jar", "false").start();
                System.exit(1);
            }
            catch (URISyntaxException e) {
                System.exit(1);
            }
            return;
        }
        MainConfiguration mainConfig = new ModifyMainConfiguration(fileHandler.getMainConfig());
        mainConfig.initialSetup();
        EmbedEngine embed = new EmbedEngine(mainConfig);
        discord = new DiscordBotMain(restartValue, mainConfig, embed, fileHandler);
        embed.setDiscordInstance(discord);
        Collection<GatewayIntent> enabledIntents = Arrays.asList(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.MESSAGE_CONTENT);
        Collection<CacheFlag> disabledFlags = Arrays.asList(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI,
                CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE, CacheFlag.ONLINE_STATUS, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);
        JDABuilder.create(mainConfig.token, enabledIntents)
                .disableCache(disabledFlags).addEventListeners(discord).setRequestTimeoutRetry(true)
                .setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.ALL).build();
    }
}