package Angel.BotAbuse;

import Angel.EmbedEngine;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

class ExpiryTimer implements Runnable {
    private final Logger log = LogManager.getLogger(ExpiryTimer.class);
    private BotAbuseMain baFeature;
    private EmbedEngine embed;
    private Guild guild;
    private boolean timerRunning = false;

    ExpiryTimer(Guild guild, BotAbuseMain baFeature, EmbedEngine embed) {
        this.guild = guild;
        this.baFeature = baFeature;
        this.embed = embed;
    }

    @Override
    public void run() {
        timerRunning = true;
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
                embed.setAsSuccess("Successfully Removed Expired Bot Abuse", "**:white_check_mark: Removed Expired Bot Abuse for "
                        + guild.getMemberById(removedID).getAsMention() + "**");
                embed.sendToLogChannel();
                guild.removeRoleFromMember(User.fromId(removedID),
                        baFeature.getConfig().getBotAbuseRole()).reason("Bot Abuse Expired Normally").queue();
                log.info("Successfully Removed the Bot Abuse role from " +
                        guild.getMemberById(removedID).getEffectiveName());
            }
            catch (ErrorResponseException ex) {
                // For Printing in Console and in Discord the Role couldn't be removed
                // because the role was not found on the player.
                embed.setAsWarning("Expired Bot Abuse Warning",
                        "**Bot Abuse just expired for " + guild.getMemberById(removedID).getAsMention()
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
    void stopTimer() {
        timerRunning = false;
    }

    boolean isTimerRunning() {
        return timerRunning;
    }
}
