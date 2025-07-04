package Angel.BotAbuse;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;

class ExpiryTimer implements Runnable, BotAbuseLogic {
    private final Logger log = LogManager.getLogger(ExpiryTimer.class);
    private final Guild guild = getGuild();
    private boolean timerRunning = false;

    @Override
    public void run() {
        Thread.currentThread().setName("Expiry Timer");
        timerRunning = true;

        // This section is for specifically for going through the records of the players with the role. We don't
        guild.findMembersWithRoles(botConfig.getBotAbuseRole()).onSuccess(botAbusers -> {
            botAbusers.forEach(m -> {
                BotAbuseRecord r = baCore.getLastRecord(m.getIdLong());
                long targetDiscordID = 0;
                
                try {
                    targetDiscordID = r.getDiscordID();
                }
                catch (NullPointerException ex) {
                    // If the player with the bot abuse has no record of a bot abuse, the role may've gotten accidently added
                    // r.getDiscordID() would throw a NullPointerException if this is the case
                
                    guild.removeRoleFromMember(m, botConfig.getBotAbuseRole())
                            .reason("Automatically Removed as Records Indicate They're Not Supposed to Have the Role").submit().whenComplete(new BiConsumer<Void, Throwable>() {
                                @Override
                                public void accept(Void unused, Throwable throwable) {
                                    if (throwable == null) {
                                        log.warn("Bot Abuse Role Successfully Removed from " + m.getEffectiveName() + " (Discord ID: " + m.getUser().getIdLong() + ") " +
                                                "because they had the role and they never should have had it");
                                        embed.setAsWarning("Old Bot Abuse Role Removed", "**:warning: Removed Bot Abuse Role for "
                                                + m.getAsMention() + " because they should never have had the role.**");
                                        embed.sendToLogChannel();
                                    }
                                    else {
                                        aue.logCaughtException(Thread.currentThread(), throwable);
                                    }
                                }
                            });
                }

                // This Catches the program from trying to remove a permanent Bot Abuse
                if (baCore.botAbuseIsPermanent(targetDiscordID)) {
                    // Take No Action
                }
                // If the current time is after the target date then remove the bot abuse for that discord ID
                else if (baCore.getCurrentTime().isAfter(r.getExpiryDate())) {
                    // If the role got accidently readded then we log what may've happened and remove it now instead of the generic expiry message
                    if (baCore.getCurrentTime().isAfter(r.getExpiryDate().plusHours(1)) && m.getRoles().contains(botConfig.getBotAbuseRole())) {
                        guild.removeRoleFromMember(m, botConfig.getBotAbuseRole()).reason("Automatically Removed as Records Indicate They're Not Supposed to Have the Bot Abuse Role")
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
                                            botConfig.getBotAbuseRole()).reason("Bot Abuse Expired Normally").submit().whenComplete(new BiConsumer<Void, Throwable>() {
                                        @Override
                                        public void accept(Void unused, Throwable throwable) {
                                            if (throwable == null) {
                                                // For Printing in the Console and in Discord A Bot Abuse role has been removed.
                                                log.info("Bot Abuse Role Successfully Removed from " + m.getEffectiveName() + " (Discord ID: " + m.getUser().getIdLong() + ") because it has expired");

                                                embed.setAsSuccess("Successfully Removed Expired Bot Abuse", "**:white_check_mark: Removed Expired Bot Abuse for "
                                                        + m.getAsMention() + "**\n\n" +
                                                        "**Original Bot Abuse Info:**\n" +
                                                        "ID: **" + r.getId() + "**\n" +
                                                        "Issued By: **<@" + r.getIssuingTeamMember() + ">**\n" +
                                                        "Issued: **" + getDiscordTimeTag(r.getIssuedDate()) + "**\n" +
                                                        "Expired: **" + getDiscordTimeTag(r.getExpiryDate()) + "**\n" +
                                                        "Reason: **" + r.getReason() + "**");
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