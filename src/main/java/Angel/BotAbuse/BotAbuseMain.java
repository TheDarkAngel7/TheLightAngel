package Angel.BotAbuse;

import Angel.*;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.*;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BotAbuseMain extends ListenerAdapter {
    private final Logger log = LogManager.getLogger(BotAbuseMain.class);
    private Thread timerThread;
    private BotAbuseTimers baTimers;
    Guild guild;
    private Angel.BotAbuse.FileHandler fileHandler;
    private Angel.FileHandler mainFileHandler;
    MainConfiguration mainConfig;
    public BotAbuseConfiguration botConfig;
    public BotAbuseCore BACore;
    // embed calls the EmbedHandler class
    public EmbedHandler embed;
    String fieldHeader;
    private DiscordBotMain discord;
    private Help help;
    public boolean timer1Running = false;
    public boolean timer2Running = false;
    public boolean commandsSuspended = false;
    public boolean timersSuspended = false;
    public boolean isConnected = false;
    public boolean isBusy = false;
    public User commandUser;
    boolean isRestart;
    boolean isReload = false;
    public List<String> commands = new ArrayList<>(Arrays.asList("botAbuse", "ba", "permBotAbuse", "pba", "undo", "check",
            "checkHistory", "clear", "transfer", "reasonsmanager", "rmgr"));

    BotAbuseMain(boolean getCommandsSuspended, boolean isRestart, MainConfiguration importMainConfig, EmbedHandler importEmbed, Guild importGuild, DiscordBotMain importDiscordBot) throws IOException, TimeoutException {
        commandsSuspended = getCommandsSuspended;
        BACore = new BotAbuseCore();
        discord = importDiscordBot;
        this.fileHandler = BACore.fileHandler;
        mainConfig = importMainConfig;
        BACore.mainConfig = importMainConfig;
        this.guild = importGuild;
        botConfig = new ModifyBotAbuseConfiguration(fileHandler.getConfig(), this, fileHandler, mainConfig);
        botConfig.guild = importGuild;
        botConfig.initialSetup();
        BACore.setBotConfig(botConfig);
        BACore.startup();
        this.fieldHeader = mainConfig.fieldHeader;
        this.isRestart = isRestart;
        this.embed = importEmbed;
        this.help = new Help(this, embed, mainConfig);
        baTimers = new BotAbuseTimers(guild, this, embed, mainConfig, discord);
        timerThread = new Thread(baTimers);
        timerThread.setName("Bot Abuse Timer Thread");
        log.info("All Classes Constructed");

        if (!botConfig.configsExist() && !commandsSuspended) {
            commandsSuspended = true;
            timersSuspended = true;
            log.fatal("Not All of the Configuration Settings were found in the discord server! Please verify the IDs of" +
                    " all of the channels, roles, and the Owner's Discord ID in the configuration file. " +
                    "Commands have been suspended, when you fix the configuration file " +
                    "you may use \"/reload\" to reload the file or \"/restart\" to restart the bot");
        }
        if (!commandsSuspended) {
            botConfig.discordSetup();
            startTimers();
        }
        else log.fatal("Commands are Suspended from Parent Class");

    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        isConnected = true;
    }

    @Override
    public void onReconnect(@NotNull ReconnectedEvent event) {
        isConnected = true;
    }

    @Override
    public void onDisconnect(@NotNull DisconnectEvent event) {
        isConnected = false;
    }

    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        isBusy = true;
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // If they're supposed to be Bot Abused and they don't have the role on join
        if (BACore.botAbuseIsCurrent(event.getMember().getIdLong()) &&
                !event.getMember().getRoles().contains(botConfig.botAbuseRole)) {
            guild.addRoleToMember(event.getMember().getIdLong(),
                   botConfig.botAbuseRole).queueAfter(50, TimeUnit.MILLISECONDS);
            embed.setAsInfo("Join Event Information", "**[System - Join Event] Added the Bot Abuse Role to "
                    + event.getMember().getAsMention() +
                    " since according to the data file they should have the Bot Abuse role**");
            embed.sendToLogChannel();
            log.info("Join Event - Added Bot Abuse Role to " + event.getMember().getEffectiveName());
        }
        // If they're not supposed to be Bot Abused and they do have the role
        else if (!BACore.botAbuseIsCurrent(event.getMember().getIdLong()) &&
                event.getMember().getRoles().contains(botConfig.botAbuseRole)) {
            guild.removeRoleFromMember(event.getMember().getIdLong(),
                    botConfig.botAbuseRole).queueAfter(50, TimeUnit.MILLISECONDS);
            embed.setAsInfo("Join Event Information", "**[System - Join Event] Removed the Bot Abuse Role from "
                    + event.getMember().getAsMention() +
                    " since according to the data file they shouldn't have it**");
            embed.sendToLogChannel();
            log.info("Join Event - Removed Bot Abuse Role from " + event.getMember().getEffectiveName());
        }
        isNotBusy();
    }
    public void saveDatabase() {
        try {
            log.error("Disconneted from Discord Websocket - Saving Data for Bot Abuse...");
            if (BACore.arraySizesEqual()) {
                fileHandler.saveDatabase();
            }
            else {
                log.fatal("Datafile is damaged on Disconnect - Reloading to Prevent Damage");
                BACore.startup();
            }
        }
        catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }
    public void resumeBot() {
        try {
            fileHandler.getDatabase();
            startTimers();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        isConnected = true;
        if (event.getAuthor().isBot()) return;
        Message msg = event.getMessage();
        Member author = event.getMember();
        String[] args = msg.getContentRaw().substring(1).split(" ");
        if (msg.getChannelType().equals(ChannelType.PRIVATE) || discord.mainCommands.contains(args[0]) ||
                !isCommand(args[0])) return;
        isBusy(msg);

        boolean isTeamMember = discord.isTeamMember(event.getAuthor().getIdLong());
        boolean isStaffMember = discord.isStaffMember(event.getAuthor().getIdLong());


        if (msg.getContentRaw().charAt(0) == mainConfig.commandPrefix && !commandsSuspended)  {
            // Command Syntax /botabuse <Mention or Discord ID> <Reason (kick, offline, or staff)> <proof url>
            if (args[0].equalsIgnoreCase("botabuse") || args[0].equalsIgnoreCase("ba")) {
                if (isTeamMember &&
                        (args.length == 3 || args.length == 4)) {
                    setBotAbuse(msg);
                }
                else if ((args.length < 3 || args.length > 4) && isTeamMember) {
                    embed.setAsError("Error - Invalid Number of Arguements", "**[System] You Entered an Invalid Number of Arguments**");
                    embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                }
                else { // If they Don't have the Team role then it returns an error message
                    embed.setAsError("Error - No Permissions", ":x: **[System] You Lack Permissions to do that!**");
                    embed.sendToHelpChannel(msg, msg.getMember());
                }
            }
            else if (args[0].equalsIgnoreCase("permbotabuse") || args[0].equalsIgnoreCase("pba")) { // /permbotabuse <Mention or Discord ID> [Image]
                if (isStaffMember && (args.length == 2 || args.length == 3)) {
                    permBotAbuse(msg);
                }
                else if (isStaffMember && (args.length < 2 || args.length > 3)) {
                    embed.setAsError("Error - Invalid Number of Arguements", ":x: **[System] You Entered an Invalid Number of Arguments**");
                    embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                }
                else {
                    embed.setAsError("Error - No Permissions", ":x: **[System] You Lack Permissions to do that!**");
                    embed.sendToHelpChannel(msg, msg.getMember());
                }
            }
            else if (args[0].equalsIgnoreCase("undo")) {
                if (isTeamMember) {
                    undoCommand(msg);
                }
                else {
                    embed.setAsError("Error - No Permissions", ":x: **[System] You Lack Permissions to do that!**");
                    embed.sendToHelpChannel(msg, msg.getMember());
                }
            }
            else if (args[0].equalsIgnoreCase("check")) {
                // This handles a /check for someone to check their own Bot Abuse status or someone else's.
                checkCommand(msg, isTeamMember);
            }
            else if (args[0].equalsIgnoreCase("transfer")) { // /transfer <Old Mention or Discord ID> <New Mention or Discord ID>
                if (isStaffMember) {
                    try {
                        transferRecords(msg);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    embed.setAsError("Error - No Permissions", ":x: **[System] You Lack Permissions to do that!**");
                    embed.sendToHelpChannel(msg, msg.getMember());
                }
            }
            else if (args[0].equalsIgnoreCase("clear")) {
                if (isStaffMember) {
                    clearCommand(msg);
                }
                else {
                    embed.setAsError("Error - No Permissions", "**:x: [System] You Lack Permissions to do that!**");
                    embed.sendToHelpChannel(msg, msg.getMember());
                }
            }
            else if (args[0].equalsIgnoreCase("checkhistory")) {
                checkHistory(msg, isTeamMember);
            }
            else if (args[0].equalsIgnoreCase("reasonsmanager") || args[0].equalsIgnoreCase("rmgr")
            || args[0].equalsIgnoreCase("reasons") || args[0].equalsIgnoreCase("r")) {
                try {
                    if (!args[0].equalsIgnoreCase("reasons")) {
                        reasonsCommand(msg, msg.getContentRaw().substring(1).split(" "), isTeamMember, isStaffMember);
                    }
                    else {
                        String[] strings = new String[2];
                        strings[0] = "rmgr";
                        strings[1] = "list";
                        reasonsCommand(msg, strings, isTeamMember, isStaffMember);
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // Commands Above this line will not run while commands are suspended
        // Commands Below this line will run even while commands are suspended
        else if (commandsSuspended) {
            try {
                if (!isTeamMember) {
                    embed.setAsStop("Commands Suspended",
                            "**Commands are Temporarily Suspended on the Bot Abuse Feature side...** \n**Sorry for the inconvience...**");
                    embed.sendToHelpChannel(msg, msg.getMember());
                }
                else {
                    embed.setAsStop("Commands Suspended", "**Commands are Temporarily Suspended on the Bot Abuse Feature side... **" +
                            "\n**Please Post The Action you were trying to do in either a DM with" + mainConfig.owner.getAsMention() + " or in this channel.**");
                   embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                }
            }
            catch (NullPointerException ex) {
                // Take No Action - This is handled elsewhere
            }
        }
        isNotBusy();
    }
    private void startTimers() {
        if (discord.isStarting) {
            timerThread.start();
        }
        else {
            timersSuspended = false;
        }
    }
    public void stopTimers() {
        timersSuspended = true;
        baTimers.timer.cancel();
        baTimers.timer.purge();
        baTimers.timer2.cancel();
        baTimers.timer2.purge();
        timer1Running = false;
        timer2Running = false;
    }
    ///////////////////////////////////////////////////////////////////
    // Divider Between Event Handlers and Command Handlers
    //////////////////////////////////////////////////////////////////
    private void setBotAbuse(Message msg) {

        String[] args = msg.getContentRaw().substring(1).split(" ");
        String defaultTitle = "Successful Bot Abuse";
        if (args[1].isEmpty()) {
            msg.getChannel().sendMessage(":x: " + msg.getAuthor().getAsMention() + " [System] I was expecting a target player").queue();
        }
        else if (msg.getMentionedMembers().isEmpty()) {
            try {
                if (msg.getAttachments().isEmpty()) {
                    String result = BACore.setBotAbuse(Long.parseLong(args[1]), false, args[2], args[3], msg.getMember().getAsMention());
                    if (result.contains("FATAL ERROR")) {
                        discord.failedIntegrityCheck(this.getClass().getName(), msg, "Bot Abuse: setBotAbuse - No Picture Attachment and Long Member Value");
                    }
                    else if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess(defaultTitle, result);
                        embed.sendToLogChannel();
                        guild.addRoleToMember(guild.getMemberById(Long.parseLong(args[1])),
                                botConfig.botAbuseRole).queueAfter(5, TimeUnit.MILLISECONDS);
                        embed.setAsSuccess(defaultTitle,":white_check_mark: " + " Successfully Bot Abused "
                                + guild.getMemberById(Long.parseLong(args[1])).getAsMention());
                        embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                        log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + guild.getMemberById(Long.parseLong(args[1])).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setAsError("Whoops... Something went wrong", result);
                        embed.sendToTeamDiscussionChannel(msg, null);
                    }
                }
                else if (msg.getAttachments().size() == 1 &&
                        (msg.getChannel().equals(mainConfig.discussionChannel) || msg.getChannel().equals(mainConfig.managementChannel))) {
                    String result = BACore.setBotAbuse(Long.parseLong(args[1]),
                            false, args[2], msg.getAttachments().get(0).getProxyUrl(), msg.getMember().getAsMention());
                    if (result.contains("FATAL ERROR")) {
                        embed.setAsStop("FATAL ERROR", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                                " while I reload and then you can try to run that command again**");
                        embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                        discord.failedIntegrityCheck(this.getClass().getName(), msg, "Bot Abuse: setBotAbuse - Picture Attachment Found and Long Member Value");
                    }
                    else if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess(defaultTitle, result);
                        embed.sendToLogChannel();
                        guild.addRoleToMember(guild.getMemberById(Long.parseLong(args[1])),
                                botConfig.botAbuseRole).queueAfter(5, TimeUnit.MILLISECONDS);
                        embed.setAsSuccess(defaultTitle,":white_check_mark: " + " Successfully Bot Abused "
                                + guild.getMemberById(Long.parseLong(args[1])).getAsMention() +
                                "\n\n :warning: Image Attachment Detected, **Do Not Delete the original command!**");
                        embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                        log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + guild.getMemberById(Long.parseLong(args[1])).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setAsError("Whoops... Something went wrong", result);
                        embed.sendToTeamDiscussionChannel(msg, null);
                    }
                }
                else if (msg.getAttachments().size() == 1
                        && msg.getChannel() != mainConfig.discussionChannel && msg.getChannel() != mainConfig.managementChannel) {
                    embed.setAsError("Channel Error for This Action",
                            "**That was the Wrong Channel to attach an image to this command. " +
                            "Do that again but please use this channel.**" +
                            "\n\n**:warning: DO NOT delete the command for me as your command will not be deleted " +
                            "in order for the attachment to be saved by Discord's servers**");
                    embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                }
            }
            catch (NumberFormatException ex) {
                embed.setAsError("Error While Setting Bot Abuse", ":x: [System] The Discord ID cannot contain any letters or special characters");
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
            }
            catch (NullPointerException ex) {
                embed.clearFields();
                embed.setAsWarning("Exception Caught but Successful Bot Abuse", "Caught a NullPointerException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Bot Abuse for " + Long.parseLong(args[1]) + " to the Database**");
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused " + args[1]);
            }
            catch (IllegalArgumentException ex) {
                embed.clearFields();
                embed.setAsWarning("Exception Caught but Successful Bot Abuse", "Caught a IllegalArgumentException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Bot Abuse for " + Long.parseLong(args[1]) + " to the Database**");
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                        + args[1]);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() == 1) {
            try {
                if (msg.getAttachments().isEmpty()) {
                    String result = BACore.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                            false, args[2], args[3], msg.getMember().getAsMention());
                    guild.addRoleToMember(msg.getMentionedMembers().get(0),
                            botConfig.botAbuseRole).queueAfter(5, TimeUnit.MILLISECONDS);
                    if (result.contains("FATAL ERROR")) {
                        discord.failedIntegrityCheck(this.getClass().getName(), msg, "Bot Abuse: setBotAbuse - No Picture Attachment and Mention Member Value");
                    }
                    else if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess(defaultTitle, result);
                        embed.sendToLogChannel();
                        embed.setAsSuccess(defaultTitle,"**:white_check_mark: " +
                                " Successfully Bot Abused " + msg.getMentionedMembers().get(0).getAsMention() + "**");
                        embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                        log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setAsError("Whoops... Something went wrong", result);
                        embed.sendToTeamDiscussionChannel(msg, null);
                    }
                }
                else if (msg.getAttachments().size() == 1 &&
                        (msg.getChannel().equals(mainConfig.discussionChannel) || msg.getChannel().equals(mainConfig.managementChannel))) {
                    String result = BACore.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                            false, args[2], msg.getAttachments().get(0).getProxyUrl(), msg.getMember().getAsMention());
                    guild.addRoleToMember(msg.getMentionedMembers().get(0),
                            botConfig.botAbuseRole).queueAfter(5, TimeUnit.MILLISECONDS);
                    if (result.contains("FATAL ERROR")) {
                        discord.failedIntegrityCheck(this.getClass().getName(), msg, "Bot Abuse: setBotAbuse - Picture Attachment Found and Mention Member Value");
                    }
                    else if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess("Successful Bot Abuse", result);
                        embed.sendToLogChannel();
                        embed.setAsSuccess(defaultTitle, "**:white_check_mark: " + msg.getAuthor().getAsMention()
                                + " Successfully Bot Abused " + msg.getMentionedMembers().get(0).getAsMention() + "**"
                        + "\n\n :warning: Image Attachment Detected, **Do Not Delete the original command!**");
                        embed.sendToTeamDiscussionChannel(msg, null);
                        log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setAsError("Whoops... Something went wrong", result);
                        embed.sendToTeamDiscussionChannel(msg, null);
                    }
                }
                else if (msg.getAttachments().size() == 1
                        && msg.getChannel() != mainConfig.discussionChannel && msg.getChannel() != mainConfig.managementChannel) {
                    embed.setAsError("Channel Error for This Action",
                            "**That was the Wrong Channel to attach an image to this command. " +
                            "Do that again but please use this channel.**" +
                            "\n\n**:warning: DO NOT delete the command for me as your command will not be deleted " +
                            "in order for the attachment to be saved by Discord's servers**");
                    embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() > 1 ) {
            embed.setAsError("Target ID Error", ":x: [System] Too many Target IDs");
            embed.sendToTeamDiscussionChannel(msg,msg.getMember());
        }
    }
    private void permBotAbuse(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        String defaultTitle = "Successful Perm Bot Abuse";

        // If length is 3, then an image url was provided.
        if (msg.getMentionedMembers().isEmpty() && args.length == 3) {
            try {
                embed.setAsSuccess(defaultTitle, BACore.setBotAbuse(Long.parseLong(args[1]),
                        true, "staff", args[2] , msg.getMember().getAsMention()));
                embed.sendToLogChannel();
                guild.addRoleToMember(guild.getMemberById(Long.parseLong(args[1])),
                        botConfig.botAbuseRole).queueAfter(5, TimeUnit.MILLISECONDS);
                embed.setAsSuccess(defaultTitle, msg.getAuthor().getAsMention() + " Permanently Bot Abused " +
                        guild.getMemberById(Long.parseLong(args[1])).getAsMention());
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                log.info("[Admin Override] " + msg.getMember().getEffectiveName() +
                        " Successfully Permanently Bot Abused " + guild.getMemberById(Long.parseLong(args[1])).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                embed.setAsError("Error While Setting Perm Bot Abuse", "[System] Invalid User ID!");
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
            }
            catch (ArrayIndexOutOfBoundsException ex) {
                embed.setAsError("Error While Setting Perm Bot Abuse", "[System] Invalid Number of Arguments!");
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
            }
            catch (NullPointerException ex) {
                embed.clearFields();
                embed.setAsWarning("Exception Caught but Successful Perm Bot Abuse",
                        "Caught a NullPointerException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Perm Bot Abuse for that Discord ID to the Database**");
                embed.sendToTeamDiscussionChannel(msg, null);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() == 1 && args.length == 3) {
            try {
                embed.setAsSuccess(defaultTitle,
                BACore.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                        true, "staff", args[2], msg.getMember().getAsMention()));
                embed.sendToLogChannel();
                guild.addRoleToMember(msg.getMentionedMembers().get(0),
                        botConfig.botAbuseRole).queueAfter(5, TimeUnit.MILLISECONDS);
                log.info("[Admin Override] " + msg.getMember().getEffectiveName()
                        + " Successfully Permanently Bot Abused " + msg.getMentionedMembers().get(0).getEffectiveName());
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2) {
            try {
                embed.setAsSuccess(defaultTitle,
                        BACore.setBotAbuse(Long.parseLong(args[1]), true,"staff", null, msg.getMember().getAsMention()));
                embed.sendToLogChannel();
                guild.addRoleToMember(guild.getMemberById(Long.parseLong(args[1])),
                        botConfig.botAbuseRole).queueAfter(5, TimeUnit.MILLISECONDS);
                log.info("[Admin Override] " + msg.getMember().getEffectiveName()
                        + " Successfully Permanently Bot Abused " + guild.getMemberById(args[1]).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                embed.setAsError("Error in Setting Perm Bot Abuse", "**:x: [System] Invalid User ID!**");
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
            }
            catch (ArrayIndexOutOfBoundsException ex) {
                embed.setAsError("Error in Setting Perm Bot Abuse", "**:x: [System] Invalid Number of Arguements!**");
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
            }
            catch (NullPointerException ex) {
                embed.clearFields();
                embed.setAsWarning("Exception Caught but Successful Perm Bot Abuse",
                        ":white_check_mark: " + msg.getMember().getEffectiveName()
                                + " Permanently Bot Abused " + args[1] + " who does not exist on the Discord Server");
                embed.sendToTeamDiscussionChannel(msg, null);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() == 1 && args.length == 2) {
            try {
                embed.setAsSuccess(defaultTitle, BACore.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                        true, "staff", null, msg.getMember().getAsMention()));
                embed.sendToLogChannel();
                guild.addRoleToMember(msg.getMentionedMembers().get(0),
                        botConfig.botAbuseRole).queueAfter(5, TimeUnit.MILLISECONDS);
                log.info("[Admin Override] " + msg.getMember().getEffectiveName()
                        + " Successfully Permanently Bot Abused " + msg.getMentionedMembers().get(0).getEffectiveName());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            embed.setAsError("Mentioned Players Error", "**:x: [System] Too Many Mentioned Players!**");;
            embed.sendToTeamDiscussionChannel(msg,msg.getMember());
        }
    }
    private void undoCommand(Message msg) {

        String[] args = msg.getContentRaw().substring(1).split(" ");
        String defaultTitle = "Successful Undo";
        long lastDiscordID = 0;
        try {
            lastDiscordID = BACore.discordID.get(BACore.issuingTeamMember.lastIndexOf(msg.getMember().getAsMention()));
            if (args.length == 1) {
                guild.removeRoleFromMember(guild.getMemberById(BACore.discordID.get(BACore.issuingTeamMember.lastIndexOf(msg.getMember().getAsMention()))),
                        botConfig.botAbuseRole).complete();
                String result = BACore.undoBotAbuse(msg.getMember().getAsMention(), true,  0 );
                if (result.contains("FATAL ERROR")) {
                    discord.failedIntegrityCheck(this.getClass().getName(), msg, "Bot Abuse: undo Command - No Member of who to Undo");
                }
                else if (result.contains(":x:")) {
                    embed.setAsError("Error While Undoing", result);
                    embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                }
                else {
                    embed.setAsSuccess(defaultTitle, result);
                    log.info(msg.getMember().getEffectiveName() + " just undid their last Bot Abuse");
                    embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                }
            }
            else if (args.length == 2 && msg.getMentionedMembers().isEmpty()) {
                guild.removeRoleFromMember(Long.parseLong(args[1]),
                        botConfig.botAbuseRole).complete();
                String result = BACore.undoBotAbuse(msg.getMember().getAsMention(), false, Long.parseLong(args[1]));
                if (result.contains("FATAL ERROR")) {
                    discord.failedIntegrityCheck(this.getClass().getName(), msg, "Bot Abuse: undo Command - Long Value of Member to Undo");
                }
                else if (result.contains(":x:")) {
                    embed.setAsError("Error While Undoing", result);
                   embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                }
                else {
                    embed.setAsSuccess(defaultTitle, result);
                    log.info(msg.getMember().getEffectiveName() + " just undid the Bot Abuse for "
                    + guild.getMemberById(args[1]).getEffectiveName());
                   embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                }
            }
            else if (args.length == 2 && msg.getMentionedMembers().size() == 1) {
                guild.removeRoleFromMember(msg.getMentionedMembers().get(0),
                        botConfig.botAbuseRole).complete();
                String result = BACore.undoBotAbuse(msg.getMember().getAsMention(), false, msg.getMentionedMembers().get(0).getIdLong());
                if (result.contains("FATAL ERROR")) {
                    discord.failedIntegrityCheck(this.getClass().getName(), msg, "undo Command - Mention Value of who to Undo");
                }
                if (result.contains(":x:")) {
                    embed.setAsError("Error While Undoing", result);
                    embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                }
                else {
                    embed.setAsSuccess(defaultTitle, result);
                    log.info(msg.getMember().getEffectiveName() + " just undid the Bot Abuse for "
                            + msg.getMentionedMembers().get(0).getEffectiveName());
                    embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                }
            }
        }
        catch (NullPointerException ex) {
            embed.clearFields();
            embed.setAsWarning("Exception Caught but Successful Undo",
                    "Caught a NullPointerException" +
                            "\n**[System] The Bot Abuse role could not be undone to that Discord ID as they Don't Exist in the Server!**" +
                            "\n **Successfully Removed A Bot Abuse for " + lastDiscordID + " from the Database**");
            embed.sendToTeamDiscussionChannel(msg,msg.getMember());
            log.info(msg.getMember().getEffectiveName() + " Successfully Undid Bot Abuse for " + lastDiscordID);
        }
        catch (IllegalArgumentException ex) {
            embed.clearFields();
            embed.setAsWarning("Exception Caught but Successful Undo",
                    "Caught a IllegalArgumentException" +
                            "\n**[System] The Bot Abuse role could not be undone to that Discord ID as they Don't Exist in the Server!**" +
                            "\n **Successfully Removed A Bot Abuse for " + lastDiscordID + " from the Database**");
            embed.sendToTeamDiscussionChannel(msg,msg.getMember());
            log.info(msg.getMember().getEffectiveName() + " Successfully Undid Bot Abuse for " + lastDiscordID);
        }
        catch (IndexOutOfBoundsException ex) {
            embed.setAsError("Nothing to Undo", "**No Records found for undoing**");
           embed.sendToTeamDiscussionChannel(msg, msg.getMember());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        embed.clearFields();
    }
    private void checkCommand(Message msg, boolean isTeamMember) {
        // Thoughout this Method, a 100 is a placeholder in the timeOffset arguement of core.getInfo
        // 100 = No Time Offset Provided

        String[] args = msg.getContentRaw().substring(1).split(" ");
        String defaultTitle = "Bot Abuse Information";

        // This handles a /check for someone to check their own Bot Abuse status
        if (msg.getMentionedMembers().isEmpty() && args.length == 1) {
            String result = BACore.getInfo(msg.getMember().getIdLong(), 100, false);
            if (result.contains(":white_check_mark:")) {
                embed.setAsSuccess("You Are Not Bot Abused", result);
            }
            else embed.setAsInfo(defaultTitle, result);
            embed.sendToHelpChannel(msg, msg.getMember());
            log.info(msg.getMember().getEffectiveName() + " just checked on their own Bot Abuse status");
        }
        // This handles if the player opts for a Direct Message instead "/check dm"
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2 && args[1].equalsIgnoreCase("dm")) {
            String result = BACore.getInfo(msg.getMember().getIdLong(), 100, false);
            if (result.contains(":white_check_mark:")) {
                embed.setAsSuccess("You Are Not Bot Abused", result);
            }
            else embed.setAsInfo(defaultTitle, result);
            embed.sendDM(msg, msg.getAuthor());
            log.info(msg.getMember().getEffectiveName() + " just checked their own Bot Abuse status and opted for a DM");
        }
        // /check <Discord ID>
        else if (isTeamMember && args.length == 2 && msg.getMentionedMembers().isEmpty()) {
            try {
                String result = BACore.getInfo(Long.parseLong(args[1]), 100 ,true);
                if (result.contains(":white_check_mark:")) {
                    embed.setAsError("Player Not Bot Abused",":x: **This Player is Not Bot Abused**");
                }
                else
                    embed.setAsInfo(defaultTitle, result);
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                log.info(msg.getMember().getEffectiveName() + " just checked on "+
                        guild.getMemberById(Long.parseLong(args[1])).getEffectiveName() + "'s Bot Abuse Status");
            }
            catch (NumberFormatException ex) {
                embed.setAsError("Check Info Error", ":x: **Invalid Input**");
               embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                log.info("Team Member " + msg.getMember().getEffectiveName() + " just entered an invalid input for the Discord ID");
            }
        }
        // /check <Mention>
        else if ((msg.getMentionedMembers().size() == 1 && isTeamMember) && args.length == 2) {
            String result = BACore.getInfo(msg.getMentionedMembers().get(0).getIdLong(), 100 ,true);
            if (result.contains(":white_check_mark:")) {
                embed.setAsError("Player Not Bot Abused", ":x: **This Player is Not Bot Abused**");
                log.error(msg.getMember().getEffectiveName() + " just checked on " +
                        msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status but they were not Bot Abused");
            }
            else {
                embed.setAsInfo(defaultTitle, result);
                log.info(msg.getMember().getEffectiveName() + " just checked on "+
                        msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status");
            }
            embed.sendToTeamDiscussionChannel(msg, null);
            log.info(msg.getMember().getEffectiveName() + " just checked on "+
                    msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status");

        }
        // /check <Timezone Offset>
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2 && !isTeamMember) {
            if (BACore.checkOffset(args[1])) {
                String result = BACore.getInfo(msg.getMember().getIdLong(), Double.parseDouble(args[1]), false);
                if (result.contains(":white_check_mark:")) {
                    embed.setAsSuccess("You Are Not Bot Abused", result);
                }
                else embed.setAsInfo(defaultTitle, result);
                embed.sendToHelpChannel(msg, msg.getMember());
                log.info(msg.getMember().getEffectiveName() +
                        " just checked on their own Bot Abuse status using TimeZone offset " + args[1]);
            }
            else {
                embed.setAsError("Check Info Error", ":x: **Invalid Timezone Offset**");
                embed.sendToHelpChannel(msg, null);
                log.error(msg.getMember().getEffectiveName() + " just entered an invalid TimeZone offset into /check");
            }
        }
        // /check [dm] <Timezone Offset>
        else if (msg.getMentionedMembers().isEmpty() && args.length == 3 && args[1].equalsIgnoreCase("dm")) {
            if (BACore.checkOffset(args[2])) {
                String result = BACore.getInfo(msg.getMember().getIdLong(), Float.parseFloat(args[2]), false);
                if (result.contains(":white_check_mark:")) {
                    embed.setAsSuccess("You Are Not Bot Abused", result);
                }
                else embed.setAsInfo(defaultTitle, result);
                embed.sendDM(msg, msg.getAuthor());
                log.info(msg.getMember().getEffectiveName() +
                        " just checked on their own Bot Abuse status while opting for a DM");
            }
            else {
                embed.setAsError("TimeZone Offset Error", ":x: " +
                        msg.getAuthor().getAsMention() + " **Invalid Timezone Offset**");
                embed.sendToHelpChannel(msg, null);
                log.error(msg.getMember().getEffectiveName() +
                        " just entered an invalid Discord ID while opting for a DM");
            }
        }
        // /check <Timezone Offset> <Mention or Discord ID>
        else if (isTeamMember && args.length == 3) {
            if (BACore.checkOffset(args[1])) {
                if (msg.getMentionedMembers().isEmpty()) {
                    try {
                        String result = BACore.getInfo(Long.parseLong(args[2]), Double.parseDouble(args[1]), true);
                        if (result.contains(":white_check_mark:")) {
                            embed.setAsError("Player Not Bot Abused", ":x: **This Player is Not Bot Abused**");
                            log.error(msg.getMember().getEffectiveName() + " just checked on " +
                                    msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status but they were not Bot Abused");
                        }
                        else embed.setAsInfo(defaultTitle, result);
                        embed.sendToTeamDiscussionChannel(msg, null);
                        log.info(msg.getMember().getEffectiveName() +
                                " just checked on " + guild.getMemberById(Long.parseLong(args[2])).getEffectiveName()
                                + "'s Bot Abuse status using TimeZone offset " + args[1]);
                    }
                    catch (NumberFormatException f) {
                        embed.setAsError("Invalid Mention", ":x: **The Mention You Entered is Invalid**" +
                                "\nTry right clicking on their name and click mention, copy and paste that mention into that argument");
                       embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                    }
                }
                else if (msg.getMentionedMembers().size() == 1) {
                    String result = BACore.getInfo(msg.getMentionedMembers().get(0).getIdLong(), Double.parseDouble(args[1]), true);
                    if (result.contains(":white_check_mark:")) {
                        embed.setAsError("Player Not Bot Abused", ":x: **This Player is Not Bot Abused**");
                    }
                    else embed.setAsInfo(defaultTitle, result);

                    embed.sendToTeamDiscussionChannel(msg, null);
                    log.info(msg.getMember().getEffectiveName() +
                            " just checked on " + msg.getMentionedMembers().get(0).getEffectiveName()
                            + "'s Bot Abuse status using TimeZone offset " + args[1]);
                }
            }
            else {
                embed.setAsError("Check Info Error", ":x: **[System] Invalid Timezone Offset**");
                embed.sendToTeamDiscussionChannel(msg, null);
                log.error("Team Member " + msg.getMember().getEffectiveName() + " just entered an invalid TimeZone offset.");
            }

        }
        else {
            embed.setAsError("Permission Error", "You Don't have Permission to check on someone else's Bot Abuse status");
            embed.sendToHelpChannel(msg, msg.getMember());
            log.info("Successfully Denied Permission to " + msg.getMember().getEffectiveName() +
                    " for checking on someone else's Bot Abuse status");
        }
    }
    private void clearCommand(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        String defaultTitle = "Successfully Cleared Records";
        int index = 0;
        // This Handles the list of mentioned members
        while (index < msg.getMentionedMembers().size()) {

            // We now check if they have the Bot Abuse role, if they do then it's removed.
            if (msg.getMentionedMembers().get(index).getRoles().contains(botConfig.botAbuseRole)) {
                guild.removeRoleFromMember(msg.getMentionedMembers().get(index).getIdLong(),
                        botConfig.botAbuseRole).queueAfter(50, TimeUnit.MILLISECONDS);
                embed.setAsInfo("Bot Abuse Role Removed", "**Successfully Removed Bot Abuse Role from "
                        + msg.getMentionedMembers().get(index).getAsMention() + " as their Records just got Cleared**");
                embed.sendToLogChannel();
                log.info("Successfully Removed Bot Abuse Role from " +
                         msg.getMentionedMembers().get(index).getEffectiveName() + " as their Records just got Cleared");
            }
            try {
                int clearedRecords = BACore.clearRecords(msg.getMentionedMembers().get(index).getIdLong());
                if (clearedRecords == -1) {
                    discord.failedIntegrityCheck(this.getClass().getName(), msg, "Bot Abuse: clear Command - Mentioned Members");
                    break;
                }
                else if (clearedRecords == 0) {
                    embed.setAsError("No Records Cleared",
                            "**[System] No Records Found for " + msg.getMentionedMembers().get(0).getEffectiveName() + "**");
                    embed.sendToTeamDiscussionChannel(msg,msg.getMember());
                    log.error("No Records Cleared for " + msg.getMentionedMembers().get(index).getEffectiveName());
                }
                else {
                    embed.setAsSuccess(defaultTitle, ":white_check_mark: **[System] Successfully Cleared " +
                            clearedRecords + " Records from " + msg.getMentionedMembers().get(index).getAsMention() + "**");
                    embed.sendToLogChannel();
                    log.info("Successfully Cleared " + clearedRecords + " Records from " +
                            msg.getMentionedMembers().get(index).getEffectiveName());
                }
                index++;
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        index = 0;
        // We check for any plain discord IDs with this, we don't take any action on a NumberFormatException as that would indicate
        // a mention in that argument, which was already handled, so they're ignored
        while (index < args.length) {
            try {
                if (guild.getMemberById(Long.parseLong(args[index])).getRoles().contains(botConfig.botAbuseRole)) {
                    guild.removeRoleFromMember(Long.parseLong(args[index]),
                            botConfig.botAbuseRole).queueAfter(50, TimeUnit.MILLISECONDS);
                    embed.setAsInfo("Bot Abuse Role Removed",
                            "**Successfully Removed Bot Abuse Role from "
                                    + guild.getMemberById(Long.parseLong(args[index])).getEffectiveName() +
                                    " as their Records just got Cleared**");
                    log.info("Successfully Removed Bot Abuse Role from " +
                            guild.getMemberById(Long.parseLong(args[index])).getEffectiveName() +
                            " as their Records just got Cleared");
                }
                int clearedRecords = BACore.clearRecords(Long.parseLong(args[index]));
                if (clearedRecords == -1) {
                    discord.failedIntegrityCheck(this.getClass().getName(), msg, "Bot Abuse: clear Command - Long Value of Member");
                    break;
                }
                if (clearedRecords == 0) {
                    embed.setAsError("No Records Cleared",
                            "**[System] No Records Found for " + args[index] + "**");
                   embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                    log.error("No Records Cleared for " + args[index]);
                }
                else {
                    embed.setAsSuccess(defaultTitle,
                            ":white_check_mark: **[System] Successfully Cleared " +
                            clearedRecords + " Records from " +
                            guild.getMemberById(Long.parseLong(args[index])).getAsMention());
                    embed.sendToLogChannel();
                    log.info("Successfully Cleared " + clearedRecords + " Records from " +
                            guild.getMemberById(Long.parseLong(args[index])).getEffectiveName());
                }
            }
            catch (NumberFormatException ex) {
                // Take No Action
            }
            catch (NullPointerException ex) {
                // Handles if the Player is no longer in the Discord Server
                try {
                    int clearedRecords = BACore.clearRecords(Long.parseLong(args[index]));
                    if (clearedRecords == -1) {
                        discord.failedIntegrityCheck(this.getClass().getName(), msg, "Bot Abuse: clear Command - Player Is Not in Discord Server");
                        break;
                    }
                    if (clearedRecords == 0) {
                        embed.setAsError("Error in Clearing Records", "No Records Cleared for " + args[index]);
                       embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                        log.error("No Records Cleared for " + args[index]);
                    }
                    else {
                        embed.setAsSuccess(defaultTitle,":white_check_mark: **[System] Successfully Cleared " +
                                clearedRecords + " Records from " + args[index] + "**");
                        embed.sendToLogChannel();
                        log.info("Successfully Cleared " + clearedRecords + " Records from " + args[index]);
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
            index++;
        }
    }
    private void transferRecords(Message msg) throws IOException {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        String defaultTitle = "Successful Transfer of Records";

        if (args.length == 3) {
            if (msg.getMentionedMembers().size() == 2) {
                if (BACore.botAbuseIsCurrent(msg.getMentionedMembers().get(0).getIdLong())) {
                    guild.addRoleToMember(msg.getMentionedMembers().get(1).getIdLong(),
                            botConfig.botAbuseRole).queueAfter(50, TimeUnit.MILLISECONDS);
                    guild.removeRoleFromMember(msg.getMentionedMembers().get(0).getIdLong(),
                            botConfig.botAbuseRole).queueAfter(50, TimeUnit.MILLISECONDS);
                }
                log.info(msg.getMember().getEffectiveName() + " Successfully Transferred the Records of "
                        + msg.getMentionedMembers().get(0).getEffectiveName() + " to " + msg.getMentionedMembers().get(1).getEffectiveName());
                embed.setAsSuccess(defaultTitle, BACore.transferRecords(msg.getMentionedMembers().get(0).getIdLong(), msg.getMentionedMembers().get(1).getIdLong()));
                embed.sendToLogChannel();
            }
            else if (msg.getMentionedMembers().size() == 1) {
                try {
                    // If they provide a Discord ID First and a Mention Last
                    if (BACore.botAbuseIsCurrent(Long.parseLong(args[1]))) {
                        guild.addRoleToMember(msg.getMentionedMembers().get(0).getIdLong(),
                                botConfig.botAbuseRole).queueAfter(50, TimeUnit.MILLISECONDS);
                        try {
                            guild.removeRoleFromMember(Long.parseLong(args[1]),
                                    botConfig.botAbuseRole).queueAfter(50, TimeUnit.MILLISECONDS);
                        }
                        catch (ErrorResponseException ex) {
                            embed.setAsWarning("Exception Caught - Player Does Not Exist",
                                    "**[System] Could Not Remove the Bot Abuse Role from "
                                            + args[1] + " because they do not exist in the Discord Server**");
                            embed.sendToLogChannel();
                        }
                    }
                    embed.setAsSuccess(defaultTitle,
                            BACore.transferRecords(Long.parseLong(args[1]), msg.getMentionedMembers().get(0).getIdLong()));
                    embed.sendToLogChannel();
                    try {
                        log.info(msg.getMember().getEffectiveName() + " Successfully Transferred the Records of "
                                + guild.getMemberById(Long.parseLong(args[1])).getEffectiveName()
                                + " to " + msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                    catch (NullPointerException e) {
                        log.info(msg.getMember().getEffectiveName() + " Successfully Transferred the Records of " + args[1]
                                + " to " + msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                }
                catch (NumberFormatException ex) {
                    // If they provide a mention first and a Discord ID Last
                    if (BACore.botAbuseIsCurrent(msg.getMentionedMembers().get(0).getIdLong())) {
                        try {
                            guild.addRoleToMember(Long.parseLong(args[2]),
                                    botConfig.botAbuseRole).queueAfter(50, TimeUnit.MILLISECONDS);
                        }
                        catch (ErrorResponseException e) {
                            embed.setAsWarning("Exception Caught - Player Does Not Exist",
                                    "**[System] Could Not Add the Bot Abuse Role to "
                                    + args[2] + " because they do not exist in the Discord Server**");
                            embed.sendToLogChannel();
                        }
                        guild.removeRoleFromMember(msg.getMentionedMembers().get(0).getIdLong(),
                                botConfig.botAbuseRole).queueAfter(50, TimeUnit.MILLISECONDS);
                    }
                    embed.setAsSuccess("Successful Transfer of Records",
                            BACore.transferRecords(msg.getMentionedMembers().get(0).getIdLong(), Long.parseLong(args[2])));
                    embed.sendToLogChannel();
                    try {
                        log.info(msg.getMember().getEffectiveName() + " Successfully Transferred the Records of "
                                + msg.getMentionedMembers().get(0).getEffectiveName() + " to " +
                                guild.getMemberById(Long.parseLong(args[2])).getEffectiveName());
                    }
                    catch (NullPointerException e) {
                        log.info(msg.getMember().getEffectiveName() +
                                " Successfully Transferred the Records of "
                                + msg.getMentionedMembers().get(0).getEffectiveName() + " to " + args[2]);
                    }
                }
            }
            else if (msg.getMentionedMembers().isEmpty()) {
                if (BACore.botAbuseIsCurrent(Long.parseLong(args[1]))) {
                    try {
                        guild.addRoleToMember(Long.parseLong(args[2]),
                                botConfig.botAbuseRole).queueAfter(50, TimeUnit.MILLISECONDS);
                    }
                    catch (ErrorResponseException ex) {
                        embed.setAsWarning("Exception Caught - Player Does Not Exist",
                                "**[System] Could Not Add the Bot Abuse Role to "
                                        + args[2] + " because they do not exist in the Discord Server**");
                        embed.sendToLogChannel();
                        log.warn("Could Not Add the Bot Abuse Role to " +
                                args[2] + " because they do not exist in the Discord Server");
                    }
                    try {
                        guild.removeRoleFromMember(Long.parseLong(args[1]),
                                botConfig.botAbuseRole).queueAfter(50, TimeUnit.MILLISECONDS);
                    }
                    catch (ErrorResponseException ex) {
                        embed.setAsWarning("Exception Caught - Player Does Not Exist",
                                "**[System] Could Not Remove the Bot Abuse Role from "
                                        + args[1] + " because they do not exist in the Discord Server**");
                        embed.sendToLogChannel();
                        log.warn("Could Not Remove the Bot Abuse Role from " + args[1] +
                                " because they do not exist in the Discord Server");
                    }
                }
                embed.setAsSuccess("Successful Transfer of Records",
                        BACore.transferRecords(Long.parseLong(args[1]), Long.parseLong(args[2])));
                embed.sendToLogChannel();
                try {
                    log.info(msg.getMember().getEffectiveName() + " Successfully Transferred the Records of "
                            + guild.getMemberById(Long.parseLong(args[1])).getEffectiveName() + " to " +
                            guild.getMemberById(Long.parseLong(args[2])).getEffectiveName());
                }
                catch (NullPointerException ex) {
                    log.info(msg.getMember().getEffectiveName() + " Successfully Transferred the Records of " +
                            args[1] + " to " + args[2]);
                }

            }
            else {
                embed.setAsError("Error while Parsing Transfer Command",
                        "[System] Invalid Number of Mentions!" +
                        "\nUsage: /transfer <Old Mention or Discord ID> <New Mention or Discord ID>");
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
            }
        }
        else {
            embed.setAsError("Error while Parsing Transfer Command",
                    "[System] Invalid Number of Arguments!" +
                            "\nUsage: /transfer <Old Mention or Discord ID> <New Mention or Discord ID>");
            embed.sendToChannel(msg.getChannel());
        }
    }
    private void checkHistory(Message msg, boolean isTeamMember) {
        // Thoughout this Method, a 100 is a placeholder in the timeOffset arguement of core.seeHistory
        // 100 = No Time Offset Provided
        Member author = guild.getMember(msg.getAuthor());

        String[] args = msg.getContentRaw().substring(1).split(" ");
        String defaultTitle = "Bot Abuse History Information";

        // /checkhistory <Mention or Discord ID>
        if (isTeamMember && args.length == 2) {
            try {
                // If the user provides a Discord ID
                String result = BACore.seeHistory(Long.parseLong(args[1]), 100, true);
                if (result.contains(":x:")) {
                    embed.setAsError(defaultTitle, result);
                }
                else embed.setAsInfo(defaultTitle, result);
                embed.sendToTeamDiscussionChannel(msg, null);
                embed.setAsInfo(defaultTitle,":information_source: **[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                        guild.getMemberById(Long.parseLong(args[1])).getAsMention() + "**");
                embed.sendToLogChannel();
                log.info(author.getEffectiveName() + " just checked the history of " +
                        guild.getMemberById(Long.parseLong(args[1])).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                try {
                    // The code above would throw a NumberFormatException if it's a mention
                    String result = BACore.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), 100, true);
                    if (result.contains(":x:")) {
                        embed.setAsError(defaultTitle, result);
                    }
                    else embed.setAsInfo(defaultTitle, result);
                    embed.sendToTeamDiscussionChannel(msg, null);
                    embed.setAsInfo(defaultTitle, ":information_source: **[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                            msg.getMentionedMembers().get(0).getAsMention() + "**");
                    embed.sendToLogChannel();
                }
                // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
                catch (IllegalArgumentException e) {
                    try {
                        this.lengthyHistory(
                                BACore.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), 100, true),
                                msg, null);
                        log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                    catch (IndexOutOfBoundsException f) {
                        embed.setAsError("Invalid Mention", ":x: **The Mention You Entered is Invalid**" +
                                "\nTry right clicking on their name and click mention, copy and paste that mention into that argument");
                       embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                    }
                }
            }
            // The Try code would throw a NullPointerException if the Discord ID Provided does not exist on the server.
            catch (NullPointerException f) {
                embed.setAsWarning("Bot Abuse History - Player Does Not Exist in the Server",
                        "**[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                                args[1] + " who currently does not exist within the Discord Server**");
                embed.sendToLogChannel();
                log.info(msg.getMember().getEffectiveName() + " just checked the history of "  +
                        args[1] + " who currently does not exist within the Discord Server");
            }
            // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
            catch (IllegalArgumentException h) {
                this.lengthyHistory(BACore.seeHistory(Long.parseLong(args[1]), 100,true),
                        msg, null);
            }
            catch (IndexOutOfBoundsException j) {
                mainConfig.discussionChannel.sendMessage(msg.getMember().getAsMention() +
                        "**You shouldn't need to check your own Bot Abuse History... you're a Team Member!**").queue();
            }
        }
        // /checkhistory
        // Get the history of the player who used the command.
        else if (args.length == 1) {
            try {
                String result = BACore.seeHistory(msg.getMember().getIdLong(), 100,false);
                if (result.contains(":white_check_mark:")) {
                    embed.setAsSuccess("Your Bot Abuse History", result);
                }
                embed.setAsInfo(defaultTitle, result);
                embed.sendDM(msg, msg.getAuthor());
                log.info(msg.getMember().getEffectiveName() + " just checked their own Bot Abuse History");
            }
            // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
            catch (IllegalArgumentException ex) {
                this.lengthyHistory(BACore.seeHistory(msg.getMember().getIdLong(), 100,false),
                        msg, null);
                log.info(msg.getMember().getEffectiveName() + " just checked their own Bot Abuse History");
            }
        }
        // /checkhistory <timeOffset>
        else if (args.length == 2) {
            try {
                if (BACore.checkOffset(args[1])) {
                    String result = BACore.seeHistory(msg.getMember().getIdLong(), Double.parseDouble(args[1]), false);
                    embed.setAsInfo("Your Bot Abuse History", result);
                    if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess("Your Bot Abuse History", result);
                    }
                    embed.sendDM(msg, msg.getAuthor());
                    log.info(msg.getMember().getEffectiveName() + " just checked their own Bot Abuse History" +
                            " using TimeZone offset " + args[1]);
                }
                else {
                    embed.setAsError("Error while Parsing Command", ":x: **Invalid Timezone Offset**");
                    embed.sendToHelpChannel(msg, msg.getMember());
                }
            }
            catch (IllegalArgumentException ex) {
                this.lengthyHistory(BACore.seeHistory(msg.getMember().getIdLong(), Double.parseDouble(args[1]), false),
                        msg, args[1]);
                log.info(msg.getMember().getEffectiveName() + " just checked their own Bot Abuse History" +
                        " using TimeZone offset " + args[1]);
            }
        }

        // /checkhistory <timeOffset> <Mention or Discord ID>
        else if (args.length == 3 && isTeamMember) {
            defaultTitle = "Bot Abuse History";
            if (BACore.checkOffset(args[1])) {
                try {
                    if (msg.getMentionedMembers().size() == 1) {
                        String result = BACore.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), Double.parseDouble(args[1]), true);
                        if (result.contains(":x:")) {
                            embed.setAsError(defaultTitle, result);
                        }
                        else embed.setAsInfo(defaultTitle, result);
                        embed.sendToTeamDiscussionChannel(msg, null);
                        log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getMentionedMembers().get(0).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                    else if (msg.getMentionedMembers().isEmpty()) {
                        String result = BACore.seeHistory(Long.parseLong(args[2]), Double.parseDouble(args[1]), true);
                        if (result.contains(":x:")) {
                            embed.setAsError(defaultTitle, result);
                        }
                        else embed.setAsInfo(defaultTitle, result);
                        embed.sendToTeamDiscussionChannel(msg, null);
                        log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                guild.getMemberById(Long.parseLong(args[2])).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                }
                catch (IllegalArgumentException ex) {
                    try {
                        this.lengthyHistory(BACore.seeHistory(Long.parseLong(args[2]), Double.parseDouble(args[1]), true),
                                msg, args[1]);
                        log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                guild.getMemberById(Long.parseLong(args[2])).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                    catch (NumberFormatException e) {
                        try {
                            this.lengthyHistory(BACore.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), Double.parseDouble(args[1]), true),
                                    msg, args[1]);
                            log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                    msg.getMentionedMembers().get(0).getEffectiveName() + " using TimeZone offset " + args[1]);
                        }
                        catch (IndexOutOfBoundsException f) {
                            embed.setAsError("Invalid Mention", ":x: **The Mention You Entered is Invalid**" +
                                    "\nTry right clicking on their name and click mention, copy and paste that mention into that argument");
                           embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                        }
                    }
                }
            }
        }
        // No Permissions to check on someone elses Bot Abuse history
        else if (args.length > 1 && !isTeamMember) {
            embed.setAsError("Error - No Permissions",
                    ":x: " + msg.getAuthor().getAsMention() +
                            " **[System] You Don't Have Permission to check on someone elses Bot Abuse History**");
            embed.sendToHelpChannel(msg, msg.getMember());
            log.warn(msg.getMember().getEffectiveName() +
                    " just tried to check someone elses Bot Abuse History but they did not have permission to");
        }
        else {
            embed.setAsStop("FATAL ERROR", ":x: **[System] Something went Seriously wrong when that happened**");
            embed.sendToChannel(msg.getChannel());
        }
    }
    public void helpCommand(Message msg, boolean isTeamMember) {

        String[] args = msg.getContentRaw().substring(1).split(" ");
        if (args.length == 1) {
            embed.setAsInfo("About " + mainConfig.commandPrefix + "help",
                    "Syntax: `" + mainConfig.commandPrefix + "help <Command Name>`");
            if (isTeamMember) {
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
            }
            else {
                embed.sendToHelpChannel(msg, msg.getMember());
            }
        }
        else if (args.length == 2) {
            if ((args[1].equalsIgnoreCase("botAbuse") || args[1].equalsIgnoreCase("ba"))
                    && isTeamMember) {
                help.botAbuseCommand();
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
            }
            else if ((args[1].equalsIgnoreCase("permBotAbuse") || args[1].equalsIgnoreCase("pba"))
                    && isTeamMember) {
                help.permBotAbuseCommand();
                embed.sendToTeamDiscussionChannel(msg, msg.getMember());
            }
            else if (args[1].equalsIgnoreCase("undo") && isTeamMember) {
                help.undoCommand();
                embed.sendToTeamDiscussionChannel(msg, msg.getMember());
            }
            else if (args[1].equalsIgnoreCase("transfer") && isTeamMember) {
                help.transferCommand();
                embed.sendToTeamDiscussionChannel(msg, msg.getMember());
            }
            else if (args[1].equalsIgnoreCase("clear") && isTeamMember) {
                help.clearCommand();
                embed.sendToTeamDiscussionChannel(msg, msg.getMember());
            }
            else if (args[1].equalsIgnoreCase("check")) {
                help.checkCommand(isTeamMember);
                if (!isTeamMember) {
                    embed.sendToHelpChannel(msg, null);
                }
                else {
                    embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                }
            }
            else if (args[1].equalsIgnoreCase("checkHistory")) {
                help.checkHistoryCommand();
                if (!isTeamMember) {
                    embed.sendToHelpChannel(msg, null);
                }
                else {
                    embed.sendToTeamDiscussionChannel(msg, msg.getMember());
                }
            }
            else if ((args[1].equalsIgnoreCase("reasonsmanager") || args[1].equalsIgnoreCase("rmgr")) && isTeamMember) {
                help.reasonManagementCommand();
                embed.sendToTeamDiscussionChannel(msg, msg.getMember());
            }
            else if (isCommand(args[1]) && !isTeamMember) {
                embed.setAsError("No Permissions to Get This Help",
                        ":x: **[System] You Do Not Have Permissions to See This Help**");
                embed.sendToHelpChannel(msg, msg.getMember());
            }
            else {
                embed.setAsError("Misspelled Command",
                        ":x: **[System] You Likely Misspelled the Name of the " +
                                "Command You Are Wanting to Lookup**");
                embed.sendToChannel(msg.getChannel());
            }
        }
        else if (args.length > 2) {
            embed.setAsError("Error While Fetching Help",
                    ":x: **[System] Too Many Arguements**");
            if (!isTeamMember) {
                embed.sendToHelpChannel(msg, msg.getMember());
            }
            else {
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
            }
        }
    }
    private void reasonsCommand(Message msg, String[] args, boolean isTeamMember, boolean isStaffMember) throws IOException {
        // /rmgr addreason <key> <Reason (multiple Args)>
        if (args[1].equalsIgnoreCase("addreason") && isStaffMember) {
            int index = 3;
            String reason = "";
            while (index < args.length) {
                reason = reason.concat(args[index]);
                if (index != args.length - 1) {
                    reason = reason.concat(" ");
                }
                index++;
            }
            embed.setAsSuccess("Successful Reason Addition", BACore.addReason(false, args[2], reason));
            embed.sendToLogChannel();
            log.info("Successful Reason Addition for \"" + reason + "\" mapped to the key \"" + args[2] + "\"");
        }
        // /rmgr addkeymap <newKey> <existingKey>
        else if (args[1].equalsIgnoreCase("addkeymap") && isStaffMember) {
            embed.setAsSuccess("Successful Key Mapping", BACore.addReason(true, args[2], args[3]));
            embed.sendToLogChannel();
            log.info("Successful Key Map of \"" + args[2] + "\" to \"" + args[3] + "\"");
        }
        // /rmgr remove <existingKey>
        // /rmgr del <existingKey>
        // /rmgr delete <existingKey>
        else if (args[1].equalsIgnoreCase("remove") || args[1].contains("del") && isStaffMember) {
            String result = BACore.deleteReason(args[2]);
            if (result.contains(":white_check_mark:")) {
                embed.setAsSuccess("Successful Reason Deletion", result);
                embed.sendToLogChannel();
            }
            else {
                embed.setAsError("Error - Reason Removal", result);
                embed.sendToTeamDiscussionChannel(msg,msg.getMember());
            }
        }
        else if (args[1].equalsIgnoreCase("list") && isTeamMember) {
            String result = "";
            Iterator<String> keys = BACore.reasonsDictionary.keys().asIterator();
            Iterator<String> elements = BACore.reasonsDictionary.elements().asIterator();
            do {
                result = result.concat("**Key: *" + keys.next()
                        + "* :arrow_right: Reason: *" + elements.next() + "* **\n");

            } while (keys.hasNext());
            embed.setAsInfo("Reasons Dictionary", result);
           embed.sendToTeamDiscussionChannel(msg, msg.getMember());
            log.info(msg.getMember().getEffectiveName() + " just requested the reasons dictionary list");
        }
        else {
            embed.setAsError("Error - No Permissions", "**:x: [System] You Lack Permissions to do that!**");
            if (isTeamMember) {
                embed.sendToTeamDiscussionChannel(msg, null);
            }
            else {
                embed.sendToHelpChannel(msg, msg.getMember());
            }
            log.error(msg.getMember().getEffectiveName()
                    + " tried to run the reason manager and did not have permission to");
        }
    }
    private void isBusy(Message msg) {
        commandUser = msg.getAuthor();
        isBusy = true;
    }
    private void isNotBusy() {
        commandUser = null;
        isBusy = false;
    }
    ///////////////////////////////////////////////////////////
    // Miscellaneous Methods
    ///////////////////////////////////////////////////////////
    private void lengthyHistory(String stringToSplit, Message msg, @Nullable String timeZoneOffset) {
        String[] splitString = stringToSplit.split("\n\n");
        int index = 0;
        while (index < splitString.length) {
            embed.setAsInfo("Bot Abuse History", splitString[index]);
            // Sometimes the addfield doesn't add the splitString correctly and there isn't a field,
            // so we restart the loop from the beginning if that happens.
            if (!discord.isTeamMember(msg.getAuthor().getIdLong())) {
                embed.sendDM(msg, msg.getAuthor());
            }
            else {
                embed.sendToTeamDiscussionChannel(msg, msg.getMember());
            }
            index++;
        }
        String logString = msg.getMember().getEffectiveName() + " just checked their own Bot Abuse History";
        if (timeZoneOffset != null) {
            logString = logString.concat(" using TimeZone offset " + timeZoneOffset);
        }
        else {
            logString = logString.concat(" using the default TimeZone offset");
        }
        log.info(logString);
    }
    public boolean isCommand(String cmd) {
        int index = 0;
        while (index < commands.size()) {
            if (cmd.equalsIgnoreCase(commands.get(index))) return true;
            index++;
        }
        return false;
    }

    public void reload(Message msg) {
        try {
            JsonObject newConfig = BACore.fileHandler.getConfig();
            if (botConfig.reload(newConfig)) {
                stopTimers();
                isReload = true;
                startTimers();
                log.info("Successfully Reloaded Bot Abuse Configuration");
            }
            else {
                log.fatal("Bot Abuse Configuration Problem Found on Reload - One or More of the Configurations Don't Exist" +
                        " in the discord server");
            }
        }
        catch (FileNotFoundException e) {
            embed.setAsStop("Bot Abuse Config File Not Found", "**:x: [System] Configuration File Not Found**");
            log.error("Configuration File Not Found on Reload");
           embed.sendToTeamDiscussionChannel(msg, msg.getMember());
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    public int getRoleScannerInterval() {
        return botConfig.roleScannerInterval;
    }
    BotAbuseMain getThis() {
        return this;
    }
}