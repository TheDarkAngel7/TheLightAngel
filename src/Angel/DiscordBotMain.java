package Angel;

import Angel.BotAbuse.BotAbuseMain;
import Angel.Nicknames.NicknameMain;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.DisconnectEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ReconnectedEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class DiscordBotMain extends ListenerAdapter {
    MainConfiguration mainConfig;
    EmbedHandler embed;
    FileHandler fileHandler;
    Guild guild;
    private NicknameMain nickFeature;
    private BotAbuseMain baFeature;
    private boolean isRestart;
    private final Logger log = LogManager.getLogger(DiscordBotMain.class);
    private boolean commandsSuspended = false;
    private ArrayList<Date> pingCooldownOverTimes = new ArrayList<>();
    private ArrayList<Long> pingCooldownDiscordIDs = new ArrayList<>();
    public List<String> mainCommands = new ArrayList<>();


    DiscordBotMain(boolean isRestart, MainConfiguration mainConfig, EmbedHandler embed, FileHandler fileHandler) {
        this.mainConfig = mainConfig;
        this.fileHandler = fileHandler;
        this.embed = embed;
        this.isRestart = isRestart;
        mainCommands.addAll(Arrays.asList("reload", "restart", "ping", "status"));
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        JDA jda = event.getJDA();
        guild = jda.getGuilds().get(0);
        mainConfig.guild = this.guild;
        if (!mainConfig.discordGuildConfigurationsExist()) {
            log.fatal("One or More of the Discord Configurations Don't Exist - Commands have been suspended in all features");
            commandsSuspended = true;
        }
        else mainConfig.discordSetup();

        try {
            nickFeature = new NicknameMain(commandsSuspended, mainConfig, embed, guild, this);
            baFeature = new BotAbuseMain(commandsSuspended, isRestart, mainConfig, embed, guild, this);
            log.info("All Features Successfully Initalized");
        }
        catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
        jda.addEventListener(nickFeature, baFeature);
        log.info("All Features Successfully Added as Event Listeners");
    }

    @Override
    public void onReconnect(@Nonnull ReconnectedEvent event) {
        baFeature.resumeBot();
        nickFeature.resumeBot();
    }

    @Override
    public void onDisconnect(@Nonnull DisconnectEvent event) {
        baFeature.saveDatabase();
        nickFeature.saveDatabase();
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (event.getMessage().getContentRaw().charAt(0) == mainConfig.commandPrefix) {
            if (event.getMessage().getChannelType() == ChannelType.PRIVATE) {
                log.info(event.getAuthor().getAsTag() + "@DM: " + event.getMessage().getContentRaw());
            }
            else {
                log.info(event.getAuthor().getAsTag() + "@" + event.getMessage().getChannel().getName() + ": " +
                        event.getMessage().getContentRaw());
            }
        }
        Message msg = event.getMessage();
        String[] args = event.getMessage().getContentRaw().substring(1).split(" ");

        if (msg.getMentionedMembers().contains(guild.getSelfMember())) {
            msg.getChannel().sendMessage("<:blobnomping:" + mainConfig.blobNomPingID + ">").queue();
        }
        else if (msg.getContentRaw().charAt(0) == mainConfig.commandPrefix) {
            if (args[0].equalsIgnoreCase("restart")
                    && (isStaffMember(event.getAuthor().getIdLong()) || event.getAuthor() == mainConfig.owner)) {
                msg.delete().complete();
                try {
                    embed.setAsWarning("Restart Initiated", "**Restart Initiated by " + msg.getMember().getAsMention()
                            + "\nPlease Allow up to 10 seconds for this to complete**");
                    log.warn(msg.getMember().getEffectiveName() + " Invoked a Restart");
                    embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
                    Thread.sleep(5000);
                    restartBot();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else if (args[0].equalsIgnoreCase("reload")
                    && (isStaffMember(event.getAuthor().getIdLong()) || event.getMember() == mainConfig.owner)) {
                embed.setAsWarning("Reloading Configuration", "**Reloading Configuration... Please Wait a Few Moments...**");
                embed.sendToTeamDiscussionChannel(msg.getChannel(), null);
                try {
                    if (mainConfig.reload(fileHandler.getMainConfig())) {
                        baFeature.reload(msg);
                        nickFeature.reloadConfig(msg);
                        log.info("Successfully Reloaded All Configurations");
                    }
                    else {
                        baFeature.commandsSuspended = true;
                        nickFeature.commandsSuspended = true;
                        log.fatal("Discord Configurations Not Found - All Commands Suspended");
                    }
                }
                catch (IOException ex) {

                }
            }
            else if (args[0].equalsIgnoreCase("ping")) {
                embed.setAsInfo("My Ping Info", ":ping_pong: **Pong!**" +
                        "\nMy Ping Time to Discord's Gateway: **" + msg.getJDA().getGatewayPing() + "ms**");
                if (isTeamMember(event.getAuthor().getIdLong())) {
                    embed.sendToTeamDiscussionChannel(msg.getChannel(),null);
                }
                else {
                    try {
                        // If they use /ping before their cooldown time is over then we send them the ping information in a DM
                        if (Calendar.getInstance().getTime()
                                .before(pingCooldownOverTimes.get(pingCooldownDiscordIDs.lastIndexOf(msg.getMember().getIdLong())))
                                && msg.getChannel() != mainConfig.botSpamChannel) {
                            embed.sendDM(msg.getMember().getUser());
                        }
                        // Otherwise we can send them this in the help channel.
                        else {
                            pingHandler(msg.getMember().getIdLong());
                            embed.sendToHelpChannel(msg.getChannel(), msg.getMember());
                        }
                    }
                    // This would run if their discord ID wasn't found in pingCooldownDiscordIDs,
                    // a -1 would throw this exception
                    catch (IndexOutOfBoundsException ex) {
                        pingHandler(msg.getMember().getIdLong());
                        embed.sendToHelpChannel(msg.getChannel(), msg.getMember());
                    }
                }
            }
            else if (args[0].equalsIgnoreCase("status")) {
                String defaultTitle = "My Status";
                String defaultOutput = "*__Bot Abuse Feature__*" +
                        "\nCommand Status: **" + !baFeature.commandsSuspended +
                        "**\n\nTimer 1 Status: **" + (baFeature.timer1Running && !baFeature.timersSuspended) +
                        "**\n*Timer 1 is what ticks every second. Each second the bot checks all the expiry times against the current time.*" +
                        "\n\nTimer 2 Status: **" + (baFeature.timer2Running && !baFeature.timersSuspended) +
                        "**\n*Timer 2 Ticks Every Amount of Minutes Configured and checks the integrity of the Bot Abuse roles.*" +

                        "\n\n\n*__Nickname Feature__*" +
                        "\nCommand Status: **" + !nickFeature.commandsSuspended + "**";

                if (!defaultOutput.contains("true")) {
                    defaultOutput = defaultOutput.replaceAll("false", "Offline");
                    embed.setAsError(defaultTitle, defaultOutput);
                }
                else if (defaultOutput.contains("false")) {
                    defaultOutput = defaultOutput.replaceAll("false", ":warning: Suspended :warning:");
                    defaultOutput = defaultOutput.replaceAll("true", "Operational");
                    embed.setAsWarning(defaultTitle, defaultOutput);
                }
                else {
                    defaultOutput = defaultOutput.replaceAll("true", "Operational");
                    embed.setAsSuccess(defaultTitle, defaultOutput);
                }
                embed.sendToChannel(event.getMessage().getChannel());
            }
        }
        if (!msg.getMentionedMembers().contains(guild.getSelfMember())
                && msg.getChannel() != mainConfig.botSpamChannel && msg.getChannel() != mainConfig.managementChannel
                && msg.getContentRaw().charAt(0) == mainConfig.commandPrefix && msg.getAttachments().isEmpty()) {
            event.getMessage().delete().queue();
        }
    }

    public void restartBot() throws IOException {
        log.warn("Program Restarting...");
        new ProcessBuilder().command("cmd.exe", "/c", "start", "/D", mainConfig.systemPath, "/MIN", "TheLightAngel Restarted", "restart.bat").start();
        System.exit(1);
    }
    // Permission Checkers that this class and the other features use:
    public boolean isTeamMember(long targetDiscordID) {
        return guild.getMemberById(targetDiscordID).getRoles().contains(mainConfig.teamRole);
    }
    public boolean isStaffMember(long targetDiscordID) {
        return guild.getMemberById(targetDiscordID).getRoles().contains(mainConfig.staffRole) ||
                guild.getMemberById(targetDiscordID).getRoles().contains(mainConfig.adminRole);
    }
    // This Method handles adding discord IDs to the cooldown arrays, since this code can be initiated two separate ways
    // best just to create a separate method for it.
    private void pingHandler(long targetDiscordID) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MINUTE, mainConfig.pingCoolDown);
        pingCooldownDiscordIDs.add(targetDiscordID);
        pingCooldownOverTimes.add(c.getTime());
    }

    // This method handles anytime an integrity check fails within one of the features
    public void failedIntegrityCheck(String obj, Member author, String cause, MessageChannel channel) {
        embed.setAsStop("FATAL ERROR",
                "**Ouch! That Really Didn't Go Well! **" +
                        "\n**You may use *" + mainConfig.commandPrefix + "restart* to try to restart me. If you don't feel comfortable doing that... " + mainConfig.owner.getAsMention()
                        + " has been notified.**" +
                        "\n\n**Cause: " + cause + "**" +
                        "\n\n**Bot Abuse Commands have Been Suspended**");
        embed.sendToTeamDiscussionChannel(channel, author);
        log.fatal("Integrity Check on ArrayList Objects Failed - Cause: " + cause);
        if (obj.contains("BotAbuseMain")) {
            baFeature.commandsSuspended = true;
            baFeature.stopTimers();
        }
        else if (obj.contains("NicknameMain")) {
            nickFeature.commandsSuspended = true;
        }
        else commandsSuspended = true;
    }
}