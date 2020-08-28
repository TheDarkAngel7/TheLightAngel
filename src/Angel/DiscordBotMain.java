package Angel;

import Angel.BotAbuse.BotAbuseInit;
import Angel.BotAbuse.BotAbuseMain;
import Angel.Nicknames.NicknameMain;
import Angel.Nicknames.NicknamesInit;
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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class DiscordBotMain extends ListenerAdapter {
    MainConfiguration mainConfig;
    EmbedHandler embed;
    FileHandler fileHandler;
    Guild guild;
    private NicknameMain nickFeature;
    private NicknamesInit nickInit;
    private BotAbuseMain baFeature;
    private BotAbuseInit baInit;
    private boolean isRestart;
    private final Logger log = LogManager.getLogger(DiscordBotMain.class);
    private boolean commandsSuspended = false;
    private ArrayList<Date> pingCooldownOverTimes = new ArrayList<>();
    private ArrayList<Long> pingCooldownDiscordIDs = new ArrayList<>();
    public List<String> mainCommands = new ArrayList<>();
    private Ping ping = new Ping();

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
        else {
            mainConfig.discordSetup();
            String defaultTitle = "Startup Complete";

            if (isRestart) defaultTitle = defaultTitle.replace("Startup", "Restart");

            embed.setAsSuccess(defaultTitle,
                    "**Please wait for all my features to finish loading and connecting.**" +
                    "\n**Check on their status with `" + mainConfig.commandPrefix + "status`**");
            embed.sendToChannel(mainConfig.discussionChannel);
        }
        Thread.currentThread().setName("Main Thread");
        nickInit = new NicknamesInit(commandsSuspended, mainConfig, embed, guild, this);
        baInit = new BotAbuseInit(commandsSuspended, isRestart, mainConfig, embed, guild, this);
        Thread tNickFeature = new Thread(nickInit);
        Thread tBotAbuseFeature = new Thread(baInit);
        tNickFeature.start();
        tNickFeature.setName("Nickname Thread");
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        nickFeature = nickInit.getNickFeature();
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        tBotAbuseFeature.start();
        tBotAbuseFeature.setName("Bot Abuse Thread");
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        baFeature = baInit.getBaFeature();
        log.info("All Features Successfully Initalized");
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
        Message msg = event.getMessage();
        String[] args = event.getMessage().getContentRaw().substring(1).split(" ");

        if (event.getMessage().getContentRaw().charAt(0) == mainConfig.commandPrefix && isValidCommand(args[0])) {
            if (event.getMessage().getChannelType() == ChannelType.PRIVATE) {
                log.info(event.getAuthor().getAsTag() + "@DM: " + event.getMessage().getContentRaw());
            }
            else {
                log.info(event.getAuthor().getAsTag() + "@" + event.getMessage().getChannel().getName() + ": " +
                        event.getMessage().getContentRaw());
            }
        }

        if (msg.getChannel().equals(mainConfig.managementChannel) && !isValidCommand(args[0])) {
            if (msg.getContentRaw().charAt(0) != mainConfig.commandPrefix) {
                embed.setAsWarning("No Messages Here",
                        "You Cannot send messages in this channel, *hence the name*... **You can only send commands!**" +
                                "\n\nTake discussion to " +
                                guild.getTextChannelById(mainConfig.discussionChannel.getIdLong()).getAsMention());
            }
            else {
                embed.setAsError("No Messages Here",
                        "*I know what you're trying to do*... what did you think I only base this filter on the first character?");
            }
            embed.sendToChannel(msg.getChannel());
            msg.delete().queue();
            return;
        }

        if (msg.getMentionedMembers().contains(guild.getSelfMember())) {
            msg.getChannel().sendMessage("<:blobnomping:" + mainConfig.blobNomPingID + ">").queue();
            log.info("Nommed the Ping from " + msg.getAuthor().getAsTag());
        }
        else if (msg.getContentRaw().charAt(0) == mainConfig.commandPrefix) {
            if (args[0].equalsIgnoreCase("restart")
                    && (isStaffMember(event.getAuthor().getIdLong()))) {
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
                    && isStaffMember(event.getAuthor().getIdLong())) {
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
                        "\nMy Ping to Discord's Gateway: **" + ping.getGatewayNetPing() + "ms**" +

                        "\n\n*__Request to Ack Pings__*" +
                        "\nMain Thread: **" + msg.getJDA().getGatewayPing() + "ms**" +
                        "\nBot Abuse Thread: **" + baInit.getPing() + "ms**" +
                        "\nNickname Thread: **" + nickInit.getPing() + "ms**");
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
                String defaultOutput = "*__Bot Abuse Feature__*";
                defaultOutput = defaultOutput.concat("\nStatus: **?**");
                System.out.println("Is Connected: " + baFeature.isConnected +
                        "\nIs Busy: " + baFeature.isBusy +
                        "\nJDA Status:" + baInit.jda.getStatus().name());

                if (baFeature.commandsSuspended && !baFeature.isBusy && baFeature.isConnected) defaultOutput =
                        defaultOutput.replace("?", "Limited");
                else if (baInit.jda.getGatewayPing() >= mainConfig.highPingTime && baFeature.isConnected) defaultOutput =
                        defaultOutput.replace("?", ":warning: High Ping");
                else if (baFeature.isConnected && !baFeature.isBusy && !baFeature.commandsSuspended) defaultOutput =
                        defaultOutput.replace("?", "Waiting for Command...");
                else if (baFeature.isBusy) defaultOutput = defaultOutput.replace("?", ":warning: Busy");
                else if (!baFeature.isConnected &&
                        (baInit.jda.getStatus() == JDA.Status.ATTEMPTING_TO_RECONNECT ||
                                baInit.jda.getStatus() == JDA.Status.CONNECTING_TO_WEBSOCKET ||
                                baInit.jda.getStatus() == JDA.Status.LOGGING_IN ||
                                baInit.jda.getStatus() == JDA.Status.AWAITING_LOGIN_CONFIRMATION ||
                                baInit.jda.getStatus() == JDA.Status.WAITING_TO_RECONNECT)) {
                    defaultOutput = defaultOutput.replace("?", ":warning: Connecting");
                }
                else if (!baFeature.isConnected && (baInit.jda.getStatus() == JDA.Status.INITIALIZED ||
                        baInit.jda.getStatus() == JDA.Status.INITIALIZING)) {
                    defaultOutput = defaultOutput.replace("?", ":warning: Starting");
                }
                else if (!baFeature.isConnected && baInit.jda.getStatus() == JDA.Status.DISCONNECTED) {
                    defaultOutput = defaultOutput.replace("?", ":warning: Disconnected");
                }
                else if (!baFeature.isConnected && baInit.jda.getStatus() == JDA.Status.RECONNECT_QUEUED) {
                    defaultOutput = defaultOutput.replace("?", ":warning: Connection Queued");
                }
                else defaultOutput = defaultOutput.replace("?", "Unknown");

                defaultOutput = defaultOutput.concat(
                        "\nCommand Status: **" + !baFeature.commandsSuspended +
                                "**\nPing Time: **" + baInit.jda.getGatewayPing() + "ms" +
                                "**\n\nTimer 1 Status: **" + (baFeature.timer1Running && !baFeature.timersSuspended) +
                                "**\n*Timer 1 is what ticks every second. Each second the bot checks all the expiry times against the current time.*" +
                                "\n\nTimer 2 Status: **" + (baFeature.timer2Running && !baFeature.timersSuspended) +
                                "**\n*Timer 2 Runs Every " + baFeature.getRoleScannerInterval() +
                                " Minutes and checks the integrity of the Bot Abuse roles each time it runs.*" +

                                "\n\n\n*__Nickname Feature__*" +
                                "\nStatus: **?**" +
                                "\nCommand Status: **" + !nickFeature.commandsSuspended +
                                "**\nPing Time: **" + nickInit.jda.getGatewayPing() + "ms**");

                if (nickFeature.commandsSuspended && !nickFeature.isBusy && nickFeature.isConnected) defaultOutput =
                        defaultOutput.replace("?", "Limited");
                else if (nickInit.jda.getGatewayPing() >= mainConfig.highPingTime && baFeature.isConnected) defaultOutput =
                        defaultOutput.replace("?", ":warning: High Ping");
                else if (nickFeature.isConnected && !nickFeature.isBusy && !nickFeature.commandsSuspended) defaultOutput =
                        defaultOutput.replace("?", "Waiting for Command...");
                else if (nickFeature.isBusy) defaultOutput = defaultOutput.replace("?", ":warning: Busy");
                else if (!nickFeature.isConnected && 
                        (nickInit.jda.getStatus() == JDA.Status.ATTEMPTING_TO_RECONNECT || 
                                nickInit.jda.getStatus() == JDA.Status.CONNECTING_TO_WEBSOCKET || 
                                nickInit.jda.getStatus() == JDA.Status.LOGGING_IN ||
                                nickInit.jda.getStatus() == JDA.Status.AWAITING_LOGIN_CONFIRMATION ||
                                nickInit.jda.getStatus() == JDA.Status.WAITING_TO_RECONNECT)) {
                    defaultOutput = defaultOutput.replace("?", ":warning: Connecting");
                }
                else if (!nickFeature.isConnected && (nickInit.jda.getStatus() == JDA.Status.INITIALIZED ||
                        nickInit.jda.getStatus() == JDA.Status.INITIALIZING)) {
                    defaultOutput = defaultOutput.replace("?", ":warning: Starting");
                }
                else if (!nickFeature.isConnected && nickInit.jda.getStatus() == JDA.Status.DISCONNECTED) {
                    defaultOutput = defaultOutput.replace("?", ":warning: Disconnected");
                }
                else if (!nickFeature.isConnected && nickInit.jda.getStatus() == JDA.Status.RECONNECT_QUEUED) {
                    defaultOutput = defaultOutput.replace("?", ":warning: Connection Queued");
                }
                else defaultOutput = defaultOutput.replace("?", "Unknown");

                if (!defaultOutput.contains("true")) {
                    defaultOutput = defaultOutput.replaceAll("false", "Offline");
                    embed.setAsError(defaultTitle, defaultOutput);
                }
                else if (defaultOutput.contains("false") || defaultOutput.contains(":warning:") || defaultOutput.contains("Unknown")) {
                    defaultOutput = defaultOutput.replaceAll("false", ":warning: Suspended :warning:");
                    defaultOutput = defaultOutput.replaceAll("true", "Operational");
                    embed.setAsWarning(defaultTitle, defaultOutput);
                }
                else {
                    defaultOutput = defaultOutput.replaceAll("true", "Operational");
                    embed.setAsSuccess(defaultTitle, defaultOutput);
                }
                if (!isTeamMember(event.getAuthor().getIdLong())) embed.sendDM(event.getAuthor());
                else embed.sendToTeamDiscussionChannel(event.getChannel(), event.getMember());
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
        return guild.getMemberById(targetDiscordID).getRoles().contains(mainConfig.teamRole) ||
                guild.getMemberById(targetDiscordID).equals(mainConfig.owner);
    }
    public boolean isStaffMember(long targetDiscordID) {
        return guild.getMemberById(targetDiscordID).getRoles().contains(mainConfig.staffRole) ||
                guild.getMemberById(targetDiscordID).getRoles().contains(mainConfig.adminRole) ||
                guild.getMemberById(targetDiscordID).equals(mainConfig.owner);
    }
    private boolean isValidCommand(String cmd) {
        int index = 0;
        while (index < baFeature.commands.size()) {
            if (cmd.equalsIgnoreCase(baFeature.commands.get(index++))) return true;
        }
        index = 0;
        while (index < nickFeature.commands.size()) {
            if (cmd.equalsIgnoreCase(nickFeature.commands.get(index++))) return true;
        }
        index = 0;
        while (index < mainCommands.size()) {
            if (cmd.equalsIgnoreCase(mainCommands.get(index++))) return true;
        }
        return false;
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