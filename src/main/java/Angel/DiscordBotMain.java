package Angel;

import Angel.BotAbuse.BotAbuseInit;
import Angel.BotAbuse.BotAbuseMain;
import Angel.Nicknames.NicknameMain;
import Angel.Nicknames.NicknameInit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.DisconnectEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ReconnectedEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.CloseCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class DiscordBotMain extends ListenerAdapter {
    MainConfiguration mainConfig;
    EmbedHandler embed;
    FileHandler fileHandler;
    Guild guild;
    private NicknameMain nickFeature;
    private NicknameInit nickInit;
    private BotAbuseMain baFeature;
    private BotAbuseInit baInit;
    private boolean isRestart;
    private final Logger log = LogManager.getLogger(DiscordBotMain.class);
    private boolean commandsSuspended = false;
    public boolean isStarting = true;
    private ArrayList<Date> pingCooldownOverTimes = new ArrayList<>();
    private ArrayList<Long> pingCooldownDiscordIDs = new ArrayList<>();
    public List<String> mainCommands = new ArrayList<>(Arrays.asList("reload", "restart", "ping", "status", "help", "set"));
    private Ping ping = new Ping();

    DiscordBotMain(boolean isRestart, MainConfiguration mainConfig, EmbedHandler embed, FileHandler fileHandler) {
        this.mainConfig = mainConfig;
        this.fileHandler = fileHandler;
        this.embed = embed;
        this.isRestart = isRestart;
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
        nickInit = new NicknameInit(commandsSuspended, mainConfig, embed, guild, this);
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
        isStarting = false;
    }

    @Override
    public void onReconnect(@Nonnull ReconnectedEvent event) {
        baFeature.resumeBot();
        nickFeature.resumeBot();
    }

    @Override
    public void onDisconnect(@Nonnull DisconnectEvent event) {
        if (!isStarting) {
            baFeature.saveDatabase();
            nickFeature.saveDatabase();
        }
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        Message msg = event.getMessage();
        String[] args = msg.getContentRaw().substring(1).split(" ");

        if (event.getMessage().getContentRaw().charAt(0) == mainConfig.commandPrefix && isValidCommand(args)) {
            if (event.getMessage().getChannelType().equals(ChannelType.PRIVATE)) {
                log.info(event.getAuthor().getAsTag() + "@DM: " + event.getMessage().getContentRaw());
            }
            else {
                log.info(event.getAuthor().getAsTag() + "@" + event.getMessage().getChannel().getName() + ": " +
                        event.getMessage().getContentRaw());
            }
        }

        if ((msg.getChannel().equals(mainConfig.managementChannel) || msg.getChannel().equals(mainConfig.dedicatedOutputChannel))
                && !isValidCommand(args)) {
            if (msg.getContentRaw().charAt(0) != mainConfig.commandPrefix && isTeamMember(msg.getAuthor().getIdLong())) {
                embed.setAsWarning("No Messages Here",
                        "You Cannot send messages in this channel, *hence the name*... \n**You can only send commands!**" +
                                "\n\nTake discussion to " +
                                mainConfig.discussionChannel.getAsMention());
            }
            else if (msg.getContentRaw().charAt(0) != mainConfig.commandPrefix && !isTeamMember(msg.getAuthor().getIdLong())) {
                embed.setAsWarning("No Messages Here",
                        "You Cannot send messages in this channel, *hence the name*... \n**You can only send commands!**" +
                                "\n\nIf you need help, take that to " +
                                mainConfig.helpChannel.getAsMention());
            }
            else {
                embed.setAsError("No Messages Here",
                        "*I know what you're trying to do*... what did you think I only base this filter on the first character?");
            }
            embed.sendToChannel(msg.getChannel());
            msg.delete().queue();
            return;
        }
        if (!msg.getAuthor().equals(baFeature.commandUser) && !msg.getAuthor().equals(nickFeature.commandUser) &&
                msg.getContentRaw().charAt(0) == mainConfig.commandPrefix) {
            boolean warningNeeded = false;
            if (baFeature.isCommand(args[0])) {
                if (baFeature.isBusy) {
                    embed.setAsWarning("Bot Abuse Feature Busy", "**The Bot Abuse Side is Currently Busy**" +
                            "\n\nYour command was received and will be processed when it's available");
                    warningNeeded = true;
                }
                else if (!baFeature.isConnected) {
                    embed.setAsWarning("Bot Abuse Feature Disconnected",
                            "**The Bot Abuse Side is Not Connected to Discord's Gateway**" +
                                    "\n\nPlease Try Again Later...");
                    warningNeeded = true;
                }
            }
            else if (args.length > 1 && nickFeature.isCommand(args[0], args[1])) {
                if (nickFeature.isBusy) {
                    embed.setAsWarning("Nickname Feature Busy", "**The Bot Abuse Side is Currently Busy**" +
                            "\n\nYour command was received and will be processed when it's available");
                    warningNeeded = true;
                }
                else if (!nickFeature.isConnected) {
                    embed.setAsWarning("Nickname Feature Disconnected",
                            "**The Bot Abuse Side is Not Connected to Discord's Gateway**" +
                                    "\n\nPlease Try Again Later...");
                    warningNeeded = true;
                }
            }
            if (warningNeeded && !isTeamMember(msg.getAuthor().getIdLong())) {
                embed.sendToHelpChannel(msg, msg.getMember());
            }
            else if (warningNeeded && isTeamMember(msg.getAuthor().getIdLong())) {
                embed.sendToTeamDiscussionChannel(msg, msg.getMember());
            }
        }

        if (msg.getMentionedMembers().contains(guild.getSelfMember())) {
            msg.getChannel().sendMessage("<:blobnomping:" + mainConfig.blobNomPingID + ">").queue();
            log.info("Nommed the Ping from " + msg.getAuthor().getAsTag());
        }
        else if (msg.getContentRaw().charAt(0) == mainConfig.commandPrefix) {
            if (args[0].equalsIgnoreCase("restart")
                    && (isStaffMember(event.getAuthor().getIdLong()))) {
                try {
                    msg.delete().queue();
                }
                catch (NoClassDefFoundError ex) {
                    // Take No Action
                }
                try {
                    embed.setAsWarning("Restart Initiated", "**Restart Initiated by " + msg.getMember().getAsMention()
                            + "\nPlease Allow up to 10 seconds for this to complete**");
                    log.warn(msg.getMember().getEffectiveName() + " Invoked a Restart");
                    embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                    Thread.sleep(5000);
                    restartBot();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else if (args[0].equalsIgnoreCase("reload")
                    && isStaffMember(event.getAuthor().getIdLong())) {
                embed.setAsWarning("Reloading Configuration", "**Reloading Configuration... Please Wait a Few Moments...**");
                embed.sendToTeamDiscussionChannel(msg, null);
                try {
                    if (mainConfig.reload(fileHandler.getMainConfig())) {
                        baFeature.reload(msg);
                        nickFeature.reload(msg);
                        log.info("Successfully Reloaded All Configurations");
                        embed.editEmbed(msg, "Configuration Reloaded",
                                "**All Configurations Successfully Reloaded from config files**",
                                false, EmbedType.EMBED_SUCCESS);
                    }
                    else {
                        baFeature.commandsSuspended = true;
                        nickFeature.commandsSuspended = true;
                        log.fatal("Discord Configurations Not Found - All Commands Suspended");
                    }
                }
                catch (IOException ex) {
                    // Take No Action
                }
            }
            else if (args[0].equalsIgnoreCase("ping")) {
                log.info(msg.getAuthor().getAsTag() + " requested my pings");
                String originalOutput = ":ping_pong: **Pong!**" +
                        "\n**Pinging Discord's Gateway... Please Wait...**";
                embed.setAsInfo("My Ping Info", originalOutput);
                if (isTeamMember(event.getAuthor().getIdLong())) {
                    embed.sendToTeamDiscussionChannel(msg,null);
                }
                else {
                    try {
                        // If they use /ping before their cooldown time is over then we send them the ping information in a DM
                        if (Calendar.getInstance().getTime()
                                .before(pingCooldownOverTimes.get(pingCooldownDiscordIDs.lastIndexOf(msg.getMember().getIdLong())))
                                && !msg.getChannel().equals(mainConfig.botSpamChannel)
                                && !msg.getChannel().equals(mainConfig.dedicatedOutputChannel)) {
                            embed.sendDM(msg, msg.getMember().getUser());
                        }
                        // Otherwise we can send them this in the help channel.
                        else {
                            pingHandler(msg.getMember().getIdLong());
                            embed.sendToHelpChannel(msg, msg.getMember());
                        }
                    }
                    // This would run if their discord ID wasn't found in pingCooldownDiscordIDs,
                    // a -1 would throw this exception
                    catch (IndexOutOfBoundsException ex) {
                        pingHandler(msg.getMember().getIdLong());
                        embed.sendToHelpChannel(msg, msg.getMember());
                    }
                }
                embed.editEmbed(msg, null, originalOutput.replace("**Pinging Discord's Gateway... Please Wait...**",
                        "My Ping to Discord's Gateway: **" + ping.getGatewayNetPing() + "ms**" +
                                "\n\n*__Request to Ack Pings__*" +
                                "\nMain Thread: **" + msg.getJDA().getGatewayPing() + "ms**" +
                                "\nBot Abuse Thread: **" + baInit.getPing() + "ms**" +
                                "\nNickname Thread: **" + nickInit.getPing() + "ms**"), true, null);
            }
            else if (args[0].equalsIgnoreCase("status")) {
                if (isTeamMember(msg.getAuthor().getIdLong())) {
                    log.info(msg.getMember().getEffectiveName() + " just requested my status");
                    String defaultTitle = "My Status";
                    String originalOutput = "**Retrieving Status... Please Wait...**";
                    embed.setAsInfo(defaultTitle, originalOutput);
                    embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                    String defaultOutput = "*__Bot Abuse Feature__*";
                    defaultOutput = defaultOutput.concat("\nStatus: **?**");

                    if (baFeature.commandsSuspended && !baFeature.isBusy && baFeature.isConnected) defaultOutput =
                            defaultOutput.replace("?", "Limited");
                    else if (baInit.jda.getGatewayPing() >= mainConfig.highPingTime && baFeature.isConnected)
                        defaultOutput = defaultOutput.replace("?", ":warning: High Ping");
                    else if (baFeature.isConnected && !baFeature.isBusy && !baFeature.commandsSuspended)
                        defaultOutput = defaultOutput.replace("?", "Waiting for Command...");
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
                    else if (nickInit.jda.getGatewayPing() >= mainConfig.highPingTime && baFeature.isConnected)
                        defaultOutput =
                                defaultOutput.replace("?", ":warning: High Ping");
                    else if (nickFeature.isConnected && !nickFeature.isBusy && !nickFeature.commandsSuspended)
                        defaultOutput =
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

                    EmbedType requestedType;

                    if (!defaultOutput.contains("true")) {
                        defaultOutput = defaultOutput.replaceAll("false", "Offline");
                        requestedType = EmbedType.EMBED_ERROR;
                    }
                    else if (defaultOutput.contains("false") || defaultOutput.contains(":warning:") || defaultOutput.contains("Unknown")) {
                        defaultOutput = defaultOutput.replaceAll("false", ":warning: Suspended :warning:");
                        defaultOutput = defaultOutput.replaceAll("true", "Operational");
                        requestedType = EmbedType.EMBED_WARNING;
                    }
                    else {
                        defaultOutput = defaultOutput.replaceAll("true", "Operational");
                        requestedType = EmbedType.EMBED_SUCCESS;
                    }
                    embed.editEmbed(msg, null, defaultOutput, false, requestedType);
                }
                else {
                    log.error(msg.getAuthor().getAsTag() + " just requested my status but did not have permission to");
                    embed.setAsError("No Permissions", ":x: **You Do Not Have Permissions to View My Status**");
                    embed.sendToHelpChannel(msg, msg.getMember());
                }
            }
            else if (args[0].equalsIgnoreCase("set")) {
                if (msg.getChannel().equals(mainConfig.discussionChannel)
                        || msg.getChannel().equals(mainConfig.managementChannel)) {
                    configCommand(msg);
                }
                else {
                    msg.delete().queue();
                    embed.setAsError("Not Usable in This Channel",
                            "**:x: This Command Is Not Usable in <#" + msg.getChannel().getIdLong() + ">!**" +
                            "\n\nPlease use ` " + mainConfig.commandPrefix + "set` in this channel or in " + mainConfig.managementChannel.getAsMention());
                    embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                }
            }
            else if (args[0].equalsIgnoreCase("help")) {
                if (args.length == 2 && baFeature.isCommand(args[1])) {
                    baFeature.helpCommand(msg, isTeamMember(msg.getAuthor().getIdLong()));
                }
                else if (args.length == 3 && nickFeature.isCommand(args[1], args[2])) {
                    nickFeature.helpCommand(msg, isTeamMember(msg.getAuthor().getIdLong()));
                }
                else if (isValidCommand(args)) helpCommand(msg);
                else {
                    embed.setAsError("Invalid Command", ":x: **The Command you asked for help for does not exist anywhere within me...**");
                    embed.sendToChannel(msg.getChannel());
                }
            }
        }
        if (!msg.getMentionedMembers().contains(guild.getSelfMember())
                && !msg.getChannel().equals(mainConfig.botSpamChannel) && !msg.getChannel().equals(mainConfig.managementChannel)
                && msg.getContentRaw().charAt(0) == mainConfig.commandPrefix && msg.getAttachments().isEmpty()
                && mainConfig.deleteOriginalNonStaffCommands) {
            msg.delete().queue();
        }
        else if (!msg.getMentionedMembers().contains(guild.getSelfMember()) &&
                isTeamMember(msg.getAuthor().getIdLong()) && isValidCommand(args) &&
                (!msg.getChannel().equals(mainConfig.discussionChannel) || mainConfig.deleteOriginalStaffCommands) &&
                !msg.getChannel().equals(mainConfig.managementChannel) && msg.getAttachments().isEmpty()) {
            msg.delete().queue();
        }
    }
    private void helpCommand(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");

        if (args.length == 2) {
            switch (args[1].toLowerCase()) {
                case "restart":
                    if (isTeamMember(msg.getAuthor().getIdLong())) {
                        embed.setAsHelp(mainConfig.commandPrefix + "restart Command",
                                "**Does exactly as it sounds, the bot will print that a restart has been initiated, " +
                                        "the bot will then start another instance of itself and terminate the current instance. " +
                                        "This command is usually used by me when deploying new code to the bot. By the way " +
                                        "this will not interfere with zoobot's pictures as the new instance of the bot starts " +
                                        "minimized, but still works exactly like it's supposed to.**");
                    }
                    else {
                        embed.setAsError("No Permissions", ":x: **You Do Not Have Permissions To See This Information**");
                    }
                    break;
                case "reload":
                    if (isTeamMember(msg.getAuthor().getIdLong())) {
                        embed.setAsHelp(mainConfig.commandPrefix + "reload Command",
                                "**If any new configuration changes need to be applied a configuration file, " +
                                        "this command reloads all configuration files when run.**");
                    }
                    else {
                        embed.setAsError("No Permissions", ":x: **You Do Not Have Permissions To See This Information**");
                    }
                    break;
                case "ping":
                    String defaultOutput = "**Pong!** - This command will also return the ping time to discord's gateway and " +
                            "the Request to Acknowledgement Pings of All Features.";
                    if (!isTeamMember(msg.getAuthor().getIdLong())) {
                        embed.setAsHelp(mainConfig.commandPrefix + "ping Command",
                                defaultOutput.concat("\n\n*There is a Cooldown of **" + mainConfig.pingCoolDown + "** minutes " +
                                        "on this command, if used during the cooldown the response will be sent you to via DM instead.*"));
                    }
                    else {
                        embed.setAsHelp(mainConfig.commandPrefix + "ping Command",
                                defaultOutput.concat("\n\n" + "*When the bot is hosted on the same machine FloppyBeaver is hosted, " +
                                        "usually the bot should average a ping between 50ms and 75ms request to acknowledgement and " +
                                        "very low just to the gateway (<20ms)*"));
                    }
                    break;
                case "status":
                    if (isTeamMember(msg.getAuthor().getIdLong())) {
                        embed.setAsHelp(mainConfig.commandPrefix + "status Command",
                                "Prints All Statuses of each features");
                    }
                    else {
                        embed.setAsError("No Permissions", ":x: **You Do Not Have Permissions To See This Information**");
                    }
                    break;
            }
            if (isTeamMember(msg.getAuthor().getIdLong())) embed.sendToTeamDiscussionChannel(msg, msg.getMember());
            else embed.sendToChannel(msg.getChannel());
        }
    }
    private void configCommand(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        boolean successfulRun = false;
        boolean botAbuseTimingChanged = false;
        boolean nameRestrictedRoleUpdated = false;
        String defaultTitle = "Configuration Updated";
        String defaultErrorTitle = "Error Updating Configuration";
        String defaultOutput = "**Successfully Set key to value** - This change is effective immediately" +
                "\n\n***Please Note:* This Command only affected the loaded configuration, it did not affect the configuration file! " +
                "You need to change the configuration file if you want this change to stick after a restart.**";
        String defaultErrorOutput = "**Could Not Update key**" +
                "\n\n*This is Likely because the Configuration Key you Entered Does Not Exist or the Syntax is Invalid*" +
                "\n**Syntax**: `syn`";
        if (isStaffMember(msg.getAuthor().getIdLong())) {
            // /set role <Config Key> <Mentioned Role>
            if (args[1].equalsIgnoreCase("role") && args.length == 4) {
                String roleSyntax = "/set role <\"adminRole\", \"staffRole\", \"teamRole\", or \"botAbuseRole\"> " +
                        "<Role Mention or ID>";
                try {
                    if (mainConfig.isValidConfig(args[2])) {
                        mainConfig.setRoleConfig(args[2], Long.parseLong(args[3]));
                        embed.setAsSuccess(defaultTitle,
                                defaultOutput.replace("key", args[2])
                                        .replace("value", guild.getRoleById(args[3]).getAsMention()));
                        successfulRun = true;

                    }
                    else if (baFeature.botConfig.isValidConfig(args[2])) {
                        if (baFeature.botConfig.setNewBotAbuseRole(Long.parseLong(args[3]))) {
                            embed.setAsSuccess(defaultTitle,
                                    defaultOutput.replace("key", args[2])
                                            .replace("value", guild.getRoleById(args[3]).getAsMention()));
                            successfulRun = true;
                        }
                        else {
                            embed.setAsError(defaultErrorTitle,
                                    defaultErrorOutput.replace("key", args[2]).replace("syn", roleSyntax));
                        }
                    }
                    else {
                        embed.setAsError(defaultErrorTitle,
                                defaultErrorOutput.replace("key", args[2]).replace("syn", roleSyntax));
                    }
                }
                catch (NumberFormatException ex) {
                    if (!msg.getMentionedRoles().isEmpty()) {
                        if (msg.getMentionedRoles().size() == 1) {
                            if (mainConfig.isValidConfig(args[2])) {
                                mainConfig.setRoleConfig(args[2], msg.getMentionedRoles().get(0));
                                embed.setAsSuccess(defaultTitle,
                                        defaultOutput.replace("key", args[2])
                                                .replace("value", msg.getMentionedRoles().get(0).getAsMention()));
                                successfulRun = true;
                            }
                            else if (baFeature.botConfig.isValidConfig(args[2])) {
                                baFeature.botConfig.setNewBotAbuseRole(msg.getMentionedRoles().get(0));
                                embed.setAsSuccess(defaultTitle, defaultOutput.replace("key", args[2])
                                        .replace("value", guild.getRoleById(args[3]).getAsMention()));
                                successfulRun = true;
                            }
                            else {
                                embed.setAsError(defaultErrorTitle,
                                        defaultErrorOutput.replace("key", args[2]).replace("syn", roleSyntax));
                            }
                        }
                        else {
                            embed.setAsError(defaultErrorTitle, "**:x: Too Many Mentioned Roles**");
                        }
                    }
                    else {
                        embed.setAsError(defaultErrorTitle,
                                defaultErrorOutput.replace("key", args[2]).replace("syn", roleSyntax));
                    }
                }
            }
            // /set channel <Config Key> <New Channel Mention or ID>
            else if ((args[1].equalsIgnoreCase("channel") || args[1].equalsIgnoreCase("cha")) && args.length == 4) {
                String channelSyntax = "/set channel <\"helpChannel\", \"botSpamChannel\", \"theLightAngelChannel\", " +
                        "\"teamDiscussionChannel\", \"botManagementChannel\", or \"logChannel\" > " +
                        "<Channel Mention or ID>";
                try {
                    mainConfig.setChannelConfig(args[2], Long.parseLong(args[3]));
                    embed.setAsSuccess(defaultTitle,
                            defaultOutput.replace("key", args[2])
                                    .replace("value", guild.getTextChannelById(args[3]).getAsMention()));
                    successfulRun = true;
                }
                catch (NumberFormatException ex) {
                    if (!msg.getMentionedChannels().isEmpty()) {
                        if (mainConfig.isValidConfig(args[2])) {
                            if (msg.getMentionedChannels().size() == 1) {
                                mainConfig.setChannelConfig(args[2], msg.getMentionedChannels().get(0));
                                embed.setAsSuccess(defaultTitle,
                                        defaultOutput.replace("key", args[2])
                                                .replace("value", msg.getMentionedChannels().get(0).getAsMention()));
                                successfulRun = true;
                            }
                            else {
                                embed.setAsError(defaultErrorTitle, "**:x: Too Many Mentioned Channels**");
                            }
                        }
                        else {
                            embed.setAsError(defaultErrorTitle,
                                    defaultErrorOutput.replace("key", args[2]).replace("syn", channelSyntax));
                        }
                    }
                    else {
                        embed.setAsError(defaultErrorTitle,
                                defaultErrorOutput.replace("key", args[2]).replace("syn", channelSyntax));
                    }
                }
            }
            // /set config <Config Key> <New Value>
            else if (args[1].equalsIgnoreCase("config") && args.length == 4) {
                try {
                    String integerDefaultSyntax = "/set config " +
                            "\n**2nd Argument Options:**" +
                            "\n\n*__Main Configuration__*" +
                            "\n<\"pingCooldown\" (\"pingCD\" for short), or \"highPingTime\" (\"highPing\" for short)> " +
                            "\n\n*__Bot Abuse Configuration__*" +
                            "\n<\"roleScannerInterval\" (\"roleScanInt\" for short), \"hotOffenseMonths\" (\"hotMonths\" for short), or" +
                            "\"maxDaysAllowedForUndo\" (\"maxDaysUndo\" for short)>" +
                            "\n\n*__Nickname Configuration__*" +
                            "\n<\"requestCooldown\" (or \"requestCD\" or \"reqCD\" for short)>" +
                            "\n\nFollowed by: <New Integer Value>";
                    if (mainConfig.isValidConfig(args[2])) {
                        mainConfig.setConfig(args[2], Integer.parseInt(args[3]));
                        embed.setAsSuccess(defaultTitle,
                                defaultOutput.replace("key", args[2]).replace("value", args[3]));
                        successfulRun = true;
                    }
                    else if (baFeature.botConfig.isValidConfig(args[2])) {
                        baFeature.botConfig.setConfig(args[2], Integer.parseInt(args[3]));
                        embed.setAsSuccess(defaultTitle, defaultOutput.replace("key", args[2]).replace("value", args[3]));
                        successfulRun = true;
                    }
                    else if (nickFeature.nickConfig.isValidConfig(args[2])) {
                        nickFeature.nickConfig.setConfig(args[2], Integer.parseInt(args[3]));
                        embed.setAsSuccess(defaultTitle, defaultOutput.replace("key", args[2]).replace("value", args[3]));
                        successfulRun = true;
                    }
                    else {
                        embed.setAsError(defaultErrorTitle,
                                defaultErrorOutput.replace("key", args[2]).replace("syn", integerDefaultSyntax));
                    }
                }
                catch (NumberFormatException ex) {
                    if (args[3].equalsIgnoreCase("false") || args[3].equalsIgnoreCase("true")) {
                        if (mainConfig.isValidConfig(args[2])) {
                            mainConfig.setConfig(args[2], Boolean.valueOf(args[3]));
                            embed.setAsSuccess(defaultTitle,
                                    defaultOutput.replace("key", args[2]).replace("value", args[3]));
                            successfulRun = true;
                        }
                        else if (nickFeature.nickConfig.isValidConfig(args[2])) {
                            nickFeature.nickConfig.setConfig(args[2], Boolean.valueOf(args[3]));
                            embed.setAsSuccess(defaultTitle,
                                    defaultOutput.replace("key", args[2]).replace("value", args[3]));
                            successfulRun = true;
                        }
                        else {
                            embed.setAsError(defaultErrorTitle, defaultErrorOutput.replace("key", args[2]).replace("syn",
                                    "/set config " +
                                            "\n**2nd Argument Options:**" +
                                            "\n\n*__Main Configuration__*" +
                                            "\n<\"deleteOriginalNonStaffCommands\" (\"delNonStaffCmd\" for short), " +
                                            "\"deleteOriginalStaffCommands\" (\"delStaffCmd\" for short), or " +
                                            "\"forceToLightAngelChannel\" (\"forceToLA\" for short)>" +
                                            "\n\n*__Nickname Configuration__*" +
                                            "\n<\"pingStaffOnlineOnRequest\" (or \"pingStaffOnline\" for short)>" +
                                            "\n\nFollowed By: <New Boolean Value>"));
                        }
                    }
                    else {
                        if (mainConfig.isValidConfig(args[2])) {
                            mainConfig.setConfig(args[2], args[3]);
                            successfulRun = true;
                            embed.setAsSuccess(defaultTitle,
                                    defaultOutput.replace("key", args[2]).replace("value", args[3]));
                        }
                        else {
                            embed.setAsError(defaultErrorTitle, defaultErrorOutput.replace("key", args[2]).replace("syn",
                                    "/set config <\"timeZone\", \"checkIconURL\", \"errorIconURL\", " +
                                            "\"infoIconURL\", \"stopIconURL\", \"helpIconURL\", \"blobNomPingID\", " +
                                            "\"commandPrefix\" (\"cmdPrefix\" for short), \"fieldHeader\", " +
                                            "or \"botabuselength\" (to add or remove times to the bot abuse punishments)>" +
                                            "<New String Value>"));
                        }
                    }
                }
            }
            // /set config botabuselength <add/del/remove> <# of Days>
            else if (args[1].equalsIgnoreCase("config") && args[2].equalsIgnoreCase("botabuselength")
                    && args.length == 5) {
                String defaultBASuccessTitle = "Time ? Successfully";
                String defaultSyntax = "/set config botabuselength <add/del/remove> <# of Days>";
                String result = "";
                try {
                    if (args[3].equalsIgnoreCase("add")) {
                        result = baFeature.botConfig.addExpiryTime(Integer.parseInt(args[4]));
                        if (result.contains(":x:")) {
                            embed.setAsError("Error While Adding Time Period", result);
                        }
                        else {
                            embed.setAsSuccess(defaultBASuccessTitle.replace("?", "Added"),
                                    defaultOutput.replace("**Successfully Set key to value** - ", "\n" + result + "\n\n"));
                            successfulRun = true;
                            botAbuseTimingChanged = true;
                            log.info(msg.getMember().getEffectiveName() + " added a " + args[4] + " Days Bot Abuse Length");
                        }
                    }
                    else if (args[3].equalsIgnoreCase("del") || args[3].equalsIgnoreCase("delete")
                            || args[3].equalsIgnoreCase("remove")) {
                        result = baFeature.botConfig.removeExpiryTime(Integer.parseInt(args[4]), false);
                        if (result.contains(":x:")) {
                            embed.setAsError("Error While Removing Time Period", result);
                        }
                        else {
                            embed.setAsSuccess(defaultBASuccessTitle.replace("?", "Deleted"),
                                    defaultOutput.replace("**Successfully Set key to value** - ", "\n" + result + "\n\n"));
                            successfulRun = true;
                            botAbuseTimingChanged = true;
                            log.info(msg.getMember().getEffectiveName() + " removed the " + args[4] + " Days Bot Abuse Length");
                        }
                    }
                    else {
                        embed.setAsError(defaultErrorTitle, defaultErrorOutput.replace("key", args[2])
                                .replace("syn", defaultSyntax));
                    }
                }
                catch (NumberFormatException ex) {
                    embed.setAsError(defaultErrorTitle, defaultErrorOutput.replace("key", args[2])
                            .replace("syn", defaultSyntax));
                }
            }
            // /set config nameRestrictredRoles add/del/remove <Role Mention or ID>
            else if (args[1].equalsIgnoreCase("config") &&
                    (args[2].equalsIgnoreCase("nameRestrictedRoles") || args[2].equalsIgnoreCase("nameRoles"))
                    && args.length == 5) {
                String defaultSuccessTitle = "Name Restricted Role ?";
                String defaultSyntax = "/set config nameRoles/nameRestrictedRoles add/del/remove <Role Mention or ID>";
                String defaultSuccess = "Successfully ? ! The Roles Not Allowed to Change Names";

                if (args[3].equalsIgnoreCase("add")) {
                    try {
                        nickFeature.nickConfig.addNewNameRestrictedRole(Long.parseLong(args[4]));
                        embed.setAsSuccess(defaultSuccessTitle, defaultSuccess.replace("?", "Added")
                            .replace("!", guild.getRoleById(Long.parseLong(args[4])).getAsMention() + " To"));
                        log.info(msg.getMember().getEffectiveName() + " Successfully Added " + guild.getRoleById(Long.parseLong(args[4])).getName() +
                                " to the name restricted roles list");
                        successfulRun = true;
                        nameRestrictedRoleUpdated = true;

                    }
                    catch (NumberFormatException ex) {
                        try {
                            nickFeature.nickConfig.addNewNameRestrictedRole(msg.getMentionedRoles().get(0));
                            embed.setAsSuccess(defaultSuccessTitle, defaultSuccess.replace("?", "Added")
                                    .replace("!", msg.getMentionedRoles().get(0).getAsMention() + " To"));
                            log.info(msg.getMember().getEffectiveName() + " Successfully Added " + msg.getMentionedRoles().get(0).getName() +
                            " to the name restricted roles list");
                            successfulRun = true;
                            nameRestrictedRoleUpdated = true;
                        }
                        catch (IndexOutOfBoundsException e) {
                            embed.setAsError("Error While Adding Role",
                                    ":x: I was Expecting a Role Mention and That Was Not Found...");
                            log.error(msg.getMember().getEffectiveName() + " did not provide a role mention, one was expected.");
                        }
                    }
                    catch (NullPointerException ex) {
                        embed.setAsError("Error While Adding Role",
                                ":x: No Role Exists By That ID in this Discord Server");
                        log.error(msg.getMember().getEffectiveName() +
                                " tried to find a role by ID " + args[4] + " and no role was found by that ID");
                    }
                }
                else if (args[3].equalsIgnoreCase("del") || args[3].equalsIgnoreCase("delete") ||
                args[3].equalsIgnoreCase("remove")) {
                    try {
                        if (nickFeature.nickConfig.removeNewNameRestrictedRole(Long.parseLong(args[4]))) {
                            embed.setAsSuccess(defaultSuccessTitle, defaultSuccess.replace("?", "Removed")
                                    .replace("!", guild.getRoleById(Long.parseLong(args[4])).getAsMention() + " From"));
                            log.info(msg.getMember().getEffectiveName() + " Successfully Removed " + guild.getRoleById(Long.parseLong(args[4])).getName() +
                                    " from the name restricted roles list");
                            successfulRun = true;
                            nameRestrictedRoleUpdated = true;
                        }
                        else {
                            embed.setAsError("Role Removal Error", ":x: **Could Not Remove This Role from the " +
                                    "list of nickname restricted roles as it did not exist there in the first place");
                        }
                    }
                    catch (NumberFormatException ex) {
                        try {
                            if (nickFeature.nickConfig.removeNewNameRestrictedRole(msg.getMentionedRoles().get(0))) {
                                embed.setAsSuccess(defaultSuccessTitle, defaultSuccess.replace("?", "Removed")
                                        .replace("!", msg.getMentionedRoles().get(0).getAsMention() + " From"));
                                log.info(msg.getMember().getEffectiveName() + " Successfully Added " + msg.getMentionedRoles().get(0).getName() +
                                        " from the name restricted roles list");
                                successfulRun = true;
                                nameRestrictedRoleUpdated = true;
                            }
                            else {
                                embed.setAsError("Role Removal Error", ":x: **Could Not Remove This Role from the " +
                                        "list of nickname restricted roles as it did not exist there in the first place");
                            }
                        }
                        catch (IndexOutOfBoundsException e) {
                            embed.setAsError("Error While Removing Role",
                                    ":x: I was Expecting a Role Mention and That Was Not Found...");
                            log.error(msg.getMember().getEffectiveName() + " did not provide a role mention, one was expected.");
                        }
                    }
                    catch (NullPointerException ex) {
                        embed.setAsError("Error While Removing Role",
                                ":x: No Role Exists By That ID in this Discord Server");
                        log.error(msg.getMember().getEffectiveName() +
                                " tried to find a role by ID " + args[4] + " and no role was found by that ID");
                    }
                }
            }
            embed.sendToChannel(msg.getChannel());
            if (successfulRun && (botAbuseTimingChanged || nameRestrictedRoleUpdated)) return;
            else if (successfulRun && !botAbuseTimingChanged) {
                log.info(msg.getMember().getEffectiveName() + " successfully updated configuration key " + args[2] + " to " + args[3]);
                embed.setAsSuccess("Configuration Updated", msg.getAuthor().getAsMention() + " successfully " +
                        "updated configuration key " + args[2] + " to " + args[3]);
                embed.sendToLogChannel();
            }
            else {
                log.error(msg.getMember().getEffectiveName() + " could not update " + args[2]);
            }
        }
        else {
            embed.setAsError("Insufficient Permissions", ":x: **You Lack Permissions to do that!**");
            if (isTeamMember(msg.getAuthor().getIdLong())) embed.sendToTeamDiscussionChannel(msg, msg.getMember());
            else embed.sendToHelpChannel(msg, msg.getMember());
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
    // Is the command array provided a valid command anywhere in the program?
    private boolean isValidCommand(String[] cmd) {
        if (baFeature.isCommand(cmd[0])) return true;
        if (cmd.length > 1 ) {
            if (nickFeature.isCommand(cmd[0], cmd[1])) return true;
        }
        int index = 0;
        while (index < mainCommands.size()) {
            if (cmd[0].equalsIgnoreCase(mainCommands.get(index++))) return true;
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
    public void failedIntegrityCheck(String obj, Message msg, String cause) {
        embed.setAsStop("FATAL ERROR",
                "**Ouch! That Really Didn't Go Well!**" +
                        "\n**You may use *" + mainConfig.commandPrefix + "restart* to try to restart me. " +
                        "If you don't feel comfortable doing that... " + mainConfig.owner.getAsMention()
                        + " has been notified.**" +
                        "\n\n**Cause: " + cause + "**" +
                        "\n\n**Bot Abuse Commands have Been Suspended**");
        embed.sendToTeamDiscussionChannel(msg, msg.getMember());
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