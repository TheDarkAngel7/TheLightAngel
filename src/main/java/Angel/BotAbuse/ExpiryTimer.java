package Angel.BotAbuse;

import Angel.EmbedHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

class ExpiryTimer extends Timer {
    private final Logger log = LogManager.getLogger(ExpiryTimer.class);
    private BotAbuseMain baFeature;
    private EmbedHandler embed;
    private Guild guild;
    private boolean timerRunning = false;

    ExpiryTimer(Guild guild, BotAbuseMain baFeature, EmbedHandler embed) {
        this.guild = guild;
        this.baFeature = baFeature;
        this.embed = embed;
    }

    void startTimer() {
        log.info("Bot Abuse Expiry Timer startTimer() executed");
        timerRunning = true;
        this.schedule(new TimerTask() {
            @Override
            public void run() {
                // Timer for executing the checkExpiredBotAbuse method each second.
                long removedID = 0;
                try {
                    removedID = baFeature.getCore().checkExpiredBotAbuse();
                }
                catch (IOException e) {
                    log.error("Bot Abuse Core - Check Expired Bot Abuse", e);
                }
                if (removedID != 0) {
                    try {
                        // For Printing in the Console and in Discord A Bot Abuse role has been removed.
                        embed.setAsSuccess("Successfully Removed Expired Bot Abuse","**:white_check_mark: Removed Expired Bot Abuse for "
                                + guild.getMemberById(removedID).getAsMention() + "**");
                        embed.sendToLogChannel();
                        guild.removeRoleFromMember(removedID,
                                baFeature.getConfig().getBotAbuseRole()).queue();
                        log.info("Successfully Removed the Bot Abuse role from "  +
                                guild.getMemberById(removedID).getEffectiveName());
                    }
                    catch (ErrorResponseException ex) {
                        // For Printing in Console and in Discord the Role couldn't be removed
                        // because the role was not found on the player.
                        embed.setAsWarning("Expired Bot Abuse Warning",
                                "**Bot Abuse just expired for " +  guild.getMemberById(removedID).getAsMention()
                                        + " and they did not have the Bot Abuse role");
                        embed.sendToLogChannel();
                        log.warn("Bot Abuse just expired for " + guild.getMemberById(removedID).getEffectiveName()
                                + " and they did not have the Bot Abuse role");
                    }
                    catch (NullPointerException ex) {
                        // The Player whos Bot Abuse role was supposed to expire does not exist in the server
                        embed.setAsWarning("Expired Bot Abuse Warning", "**Successfully Removed Expired Bot Abuse for <@!"
                                + removedID + "> but they did not exist in the discord server!**");
                        embed.sendToLogChannel();
                        log.warn("Successfully Removed Expired Bot Abuse for "
                                + removedID + " but they did not exist in the discord server!");
                    }
                }
            }
        }, 0, 1000);
    }
    void stopTimer() {
        timerRunning = false;
        this.cancel();
        this.purge();
    }

    boolean isTimerRunning() {
        return timerRunning;
    }
}
