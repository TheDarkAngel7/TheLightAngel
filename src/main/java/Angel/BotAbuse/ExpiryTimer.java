package Angel.BotAbuse;

import Angel.AngelExceptionHandler;
import Angel.EmbedEngine;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;

class ExpiryTimer implements Runnable {
    private final Logger log = LogManager.getLogger(ExpiryTimer.class);
    private final AngelExceptionHandler aue = new AngelExceptionHandler();
    private final BotAbuseMain baFeature;
    private final BotAbuseCore baCore;
    private final EmbedEngine embed;
    private final Guild guild;
    private boolean timerRunning = false;

    ExpiryTimer(Guild guild, BotAbuseMain baFeature, EmbedEngine embed) {
        this.guild = guild;
        this.baFeature = baFeature;
        this.baCore = baFeature.getCore();
        this.embed = embed;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Expiry Timer");
        timerRunning = true;
        baCore.calendarAdvance();

        // This section is for specifically for going through the records of the players with the role. We don't
        guild.findMembersWithRoles(baFeature.getConfig().getBotAbuseRole()).onSuccess(botAbusers -> {
            botAbusers.forEach(m -> {
                BotAbuseRecord r = baCore.getLastRecord(m.getIdLong());
                long targetDiscordID = r.getDiscordID();

                // This Catches the program from trying to remove a permanent Bot Abuse
                if (baCore.botAbuseIsPermanent(targetDiscordID)) {
                    // Take No Action
                }
                // If the current time is after the target date then remove the bot abuse for that discord ID
                else if (baCore.getCurrentTime().isAfter(r.getExpiryDate())) {
                    // If the role got accidently readded then we log what may've happened and remove it now instead of the generic expiry message
                    if (baCore.getCurrentTime().isAfter(r.getExpiryDate().plusHours(1)) && m.getRoles().contains(baFeature.getConfig().getBotAbuseRole())) {
                        guild.removeRoleFromMember(m, baFeature.getConfig().getBotAbuseRole()).reason("Automatically Removed as Records Indicate They're Not Supposed to Have the Bot Abuse Role")
                                .submit().whenComplete(new BiConsumer<Void, Throwable>() {
                                    @Override
                                    public void accept(Void unused, Throwable throwable) {
                                        if (throwable == null) {
                                            log.warn("Bot Abuse Role Successfully Removed from " + m.getEffectiveName() + " (Discord ID: " + m.getUser().getIdLong() + ") " +
                                                    "because they had the role and it should've been removed more than 1 hour ago");
                                            embed.setAsWarning("Old Bot Abuse Role Removed", "**:warning: Removed Bot Abuse Role for " + m.getAsMention() + " but this role should've been removed" +
                                                    " more than 1 hour ago.**" +
                                                    "\n\n**NOTE:** This could be an indication that discord never responded to my commands to remove the role. If I needed to be stopped and restarted recently that's " +
                                                    "most likely the reason and this log entry can be discarded.");
                                            embed.sendToLogChannel();
                                        }
                                        else {
                                            aue.logCaughtException(Thread.currentThread(), throwable);
                                        }
                                    }
                                });
                    }
                    else {
                        try {
                            guild.retrieveMember(UserSnowflake.fromId(m.getIdLong())).useCache(false).submit().whenComplete(new BiConsumer<Member, Throwable>() {
                                @Override
                                public void accept(Member m, Throwable throwable) {
                                    guild.removeRoleFromMember(User.fromId(m.getIdLong()),
                                            baFeature.getConfig().getBotAbuseRole()).reason("Bot Abuse Expired Normally").submit().whenComplete(new BiConsumer<Void, Throwable>() {
                                        @Override
                                        public void accept(Void unused, Throwable throwable) {
                                            if (throwable == null) {
                                                // For Printing in the Console and in Discord A Bot Abuse role has been removed.
                                                log.info("Bot Abuse Role Successfully Removed from " + m.getEffectiveName() + " (Discord ID: " + m.getUser().getIdLong() + ") because it has expired");

                                                embed.setAsSuccess("Successfully Removed Expired Bot Abuse", "**:white_check_mark: Removed Expired Bot Abuse for "
                                                        + m.getAsMention() + "**");
                                                embed.sendToLogChannel();
                                            }
                                            else {
                                                aue.logCaughtException(Thread.currentThread(), throwable);
                                            }
                                        }
                                    });
                                }
                            });
                        }
                        catch (ErrorResponseException ex) {
                            // For Printing in Console and in Discord the Role couldn't be removed
                            // because the role was not found on the player.
                            embed.setAsWarning("Expired Bot Abuse Warning",
                                    "**Bot Abuse just expired for " + guild.getMemberById(m.getIdLong()).getAsMention()
                                            + " and they did not have the Bot Abuse role");
                            embed.sendToLogChannel();
                            log.warn("Bot Abuse just expired for " + guild.getMemberById(m.getIdLong()).getEffectiveName()
                                    + " and they did not have the Bot Abuse role");
                        }
                        catch (NullPointerException ex) {
                            // The Player whos Bot Abuse role was supposed to expire does not exist in the server
                            embed.setAsWarning("Expired Bot Abuse Warning", "**Successfully Removed Expired Bot Abuse for <@!"
                                    + m.getIdLong() + "> but they did not exist in the discord server!**");
                            embed.sendToLogChannel();
                            log.warn("Successfully Removed Expired Bot Abuse for "
                                    + m.getIdLong() + " but they did not exist in the discord server!");
                        }
                    }

                }
            });
        }).onError(throwable -> {
            log.warn("An Error Occurred when requesting members with Bot Abuse Roles", throwable);
        });
    }
    void stopTimer() {
        timerRunning = false;
    }

    boolean isTimerRunning() {
        return timerRunning;
    }
}