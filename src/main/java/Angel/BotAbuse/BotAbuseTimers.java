package Angel.BotAbuse;

import Angel.DiscordBotMain;
import Angel.EmbedHandler;
import Angel.MainConfiguration;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

class BotAbuseTimers implements Runnable {
    private final Logger log = LogManager.getLogger(BotAbuseTimers.class);
    private Guild guild;
    Timer timer;
    Timer timer2;
    private BotAbuseMain baFeature;
    private EmbedHandler embed;
    private MainConfiguration mainConfig;
    private DiscordBotMain discord;

    BotAbuseTimers(Guild guild, BotAbuseMain baFeature, EmbedHandler embed, MainConfiguration mainConfig, DiscordBotMain discord) {
        this.guild = guild;
        this.baFeature = baFeature;
        this.embed = embed;
        this.mainConfig = mainConfig;
        this.discord = discord;
    }

    @Override
    public void run() {
        // Here we're running an integrity check on the data that was loaded, if the data loaded is no good...
        // then we suspend all commands and we don't start the timers.
        if (!baFeature.baCore.arraySizesEqual() && !baFeature.commandsSuspended) {
            baFeature.commandsSuspended = true;
            baFeature.timersSuspended = true;
            log.fatal("Data File Damaged on Initiation");
            log.warn("Commands are now Suspended");
        }
        // Here we're running a check on the configuration file, if the timings loaded are no good
        if (!baFeature.baCore.timingsAreValid() && !baFeature.commandsSuspended) {
            baFeature.commandsSuspended = true;
            log.error("Configuration File Timings Are Not Valid");
            log.warn("Commands are now Suspended");
        }
        else if (baFeature.baCore.timingsAreValid() && baFeature.commandsSuspended && baFeature.isReload) {
            baFeature.commandsSuspended = false;
            log.info("Configuration File Timings are now valid again - Restarting Timers...");
        }
        // If the init method was initiated from a restart then this'll run.
        if (baFeature.restartValue == 1 && baFeature.commandsSuspended) {
            embed.setAsStop("Restart Error","**Restart Complete but the Data File is Still Not Usable**");
            embed.sendToChannel(null, mainConfig.discussionChannel);
        }
        // If init was initiated from a config reload then this'll run.
        if (baFeature.isReload) {
            embed.setAsSuccess("Reload Complete", "**Reloading Config was Successfully Completed!**");
            embed.sendToChannel(null, mainConfig.discussionChannel);
        }
        // If the timers aren't running and commands aren't suspended then this'll run. Or... if a reload came in.
        if ((!baFeature.timer1Running || !baFeature.timer2Running) && (!baFeature.commandsSuspended || !baFeature.timersSuspended)) {
            // Check to see if one timer is running and the other isn't, if that's true then we restart the bot.
            if (!(!baFeature.timer1Running && !baFeature.timer2Running) && !(baFeature.timer1Running && baFeature.timer2Running)) {
                try {
                    embed.setAsStop("Timer Error", "One Timer is Running and the Other Isn't..." +
                            "\n\n**Restarting To Fix the Issue... Please Wait...**");
                    embed.sendToLogChannel();
                    discord.restartBot();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
            baFeature.timersSuspended = false;
            timer = new Timer();
            timer2 = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    baFeature.timer1Running = true;
                    // Timer for executing the checkExpiredBotAbuse method each second.
                    long removedID = 0;
                    try {
                        removedID = baFeature.baCore.checkExpiredBotAbuse();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (removedID != 0) {
                        try {
                            // For Printing in the Console and in Discord A Bot Abuse role has been removed.
                            embed.setAsSuccess("Successfully Removed Expired Bot Abuse","**:white_check_mark: [System] Removed Expired Bot Abuse for "
                                    + guild.getMemberById(removedID).getAsMention() + "**");
                            embed.sendToLogChannel();
                            guild.removeRoleFromMember(removedID,
                                    baFeature.botConfig.botAbuseRole).queue();
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
            // Configurable Periodic Scan of Players that should be Bot Abused to ensure that they have the role
            // Followed by a Scan of All Players to look for any Bot Abuse roles that did not get removed when they should have
            timer2.schedule(new TimerTask() {
                @Override
                public void run() {
                    baFeature.timer2Running = true;
                    List<Member> serverMembers = guild.getMembers();
                    String defaultTitle = "Role Scanner Information";
                    baFeature.baCore.currentBotAbusers.forEach(id -> {
                        if (guild.isMember(guild.getJDA().retrieveUserById(id).complete())) {
                            Member m = guild.retrieveMemberById(id).complete();
                            if (serverMembers.contains(m) &&
                                    !m.getRoles().contains(baFeature.botConfig.botAbuseRole)) {
                                guild.addRoleToMember(m,
                                        baFeature.botConfig.botAbuseRole).queue();
                                embed.setAsInfo(defaultTitle, "[System - Role Scanner] Added Bot Abuse Role to "
                                        + m.getAsMention() +
                                        " because they didn't have the role... and they're supposed to have it.");
                                embed.sendToLogChannel();
                                log.info("Added Bot Abuse to " + m.getEffectiveName()
                                        + " because they didn't have the role... and they're supposed to have it.");
                            }
                        }
                    });
                    serverMembers.forEach(m -> {
                        if (m.getRoles().contains(baFeature.botConfig.botAbuseRole)
                                && !baFeature.baCore.botAbuseIsCurrent(m.getIdLong())) {
                            guild.removeRoleFromMember(m, baFeature.botConfig.botAbuseRole).queue();
                            embed.setAsInfo(defaultTitle, "[System - Role Scanner] Removed Bot Abuse Role from "
                                    + m.getAsMention() + " because they had the role... " +
                                    "and they weren't supposed to have it.");
                            embed.sendToLogChannel();
                            log.info("Removed Bot Abuse Role from " + m.getEffectiveName() +
                                    " because they had the role... and they were not supposed to have it");
                        }
                    });
                }
            }, 0, baFeature.botConfig.roleScannerInterval * 60000);
            log.info("Timers are Running");
        }
        else if (baFeature.commandsSuspended) {
            try {
                embed.setAsStop("Commands Suspended", ":x: **[System] Either the Data File has been Damaged or there's configuration problems**" +
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
}