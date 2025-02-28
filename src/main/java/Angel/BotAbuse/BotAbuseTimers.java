package Angel.BotAbuse;

import net.dv8tion.jda.api.entities.Guild;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class BotAbuseTimers implements BotAbuseLogic {
    private final Logger log = LogManager.getLogger(BotAbuseTimers.class);
    private Guild guild = getGuild();
    private ExpiryTimer expiryTimer;
    private RoleScanningTimer roleScanningTimer;
    private ScheduledExecutorService service = Executors.newScheduledThreadPool(2);

    BotAbuseTimers() {
        roleScanningTimer = new RoleScanningTimer();
        expiryTimer = new ExpiryTimer();
    }

    void startTimers() {
        // Here we're running an integrity check on the data that was loaded, if the data loaded is no good...
        // then we suspend all commands and we don't start the timers.
        // Here we're running a check on the configuration file, if the timings loaded are no good
        if (!baCore.timingsAreValid() && !baFeature.commandsSuspended) {
            baFeature.commandsSuspended = true;
            log.error("Configuration File Timings Are Not Valid");
            log.warn("Commands are now Suspended");
        }
        else if (baCore.timingsAreValid() && baFeature.commandsSuspended && baFeature.isReload) {
            baFeature.commandsSuspended = false;
            log.info("Configuration File Timings are now valid again - Restarting Timers...");
        }
        // If the init method was initiated from a restart then this'll run.
        if (discord.getRestartValue() == 1 && baFeature.commandsSuspended) {
            embed.setAsStop("Restart Error","**Restart Complete but the Data File is Still Not Usable**");
            embed.sendToChannel(null, mainConfig.discussionChannel);
        }
        // If init was initiated from a config reload then this'll run.
        if (baFeature.isReload) {
            service = Executors.newScheduledThreadPool(2);
            log.warn("Timer Thread Pool Whipped and waiting for new scheduled services");
        }
        // If the timers aren't running and commands aren't suspended then this'll run. Or... if a reload came in.
        if ((!expiryTimer.isTimerRunning() || !roleScanningTimer.isTimerRunning()) && (!baFeature.commandsSuspended || !baFeature.timersSuspended)) {
            // Check to see if one timer is running and the other isn't, if that's true then we restart the bot.
            if (!(!expiryTimer.isTimerRunning() && !roleScanningTimer.isTimerRunning()) &&
                    !(expiryTimer.isTimerRunning() && roleScanningTimer.isTimerRunning())) {
                try {
                    embed.setAsStop("Timer Error", "One Timer is Running and the Other Isn't..." +
                            "\n\n**Restarting To Fix the Issue... Please Wait...**");
                    embed.sendToLogChannel();
                    discord.restartBot(false);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
            baFeature.timersSuspended = false;

            service.scheduleAtFixedRate(expiryTimer, 0, 1, TimeUnit.SECONDS);
            service.scheduleAtFixedRate(roleScanningTimer, 1, botConfig.getRoleScannerInterval(), TimeUnit.MINUTES);
            log.info("Timers are Running");
        }
        else if (baFeature.commandsSuspended) {
            try {
                embed.setAsStop("Commands Suspended", ":x: **Either the Data File has been Damaged or there's configuration problems**" +
                        "\n\n**Commands Are Suspended**");
                embed.sendToChannel(null, mainConfig.discussionChannel);
            }
            catch (NullPointerException ex) {
                log.fatal("Definitely Configuration Problems - When trying to send the stop message something " +
                        "threw a NullPointerException");
            }
        }
        baFeature.restartValue = 0;
        baFeature.isReload = false;
    }

    void stopAllTimers() {
        service.shutdown();
        expiryTimer.stopTimer();
        roleScanningTimer.stopTimer();
    }

    ExpiryTimer getExpiryTimer() {
        return expiryTimer;
    }

    RoleScanningTimer getRoleScanningTimer() {
        return roleScanningTimer;
    }
}