package Angel;

import com.google.gson.JsonObject;
import net.dv8tion.jda.api.EmbedBuilder;
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
import java.awt.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class DiscordBotMain extends ListenerAdapter {
    private final Logger log = LogManager.getLogger(DiscordBotMain.class);
    Guild guild;
    BotConfiguration botConfig;
    private Core core = new Core();
    private FileHandler fileHandler = core.fileHandler;
    // embed calls things from the EmbedDesigner class, which houses the urls for the thumbnails, colors,
    // and each method in this class needs a title as an arguement.
    // embedBuilder calls the builder directly, which calls for adding fields and clearing them.
    EmbedBuilder embedBuilder = new EmbedBuilder();
    EmbedDesigner embed;
    // We have separate instances of Embed Builder so that the timers that are running in the background
    // don't interfere with the main builder
    private EmbedBuilder timerEmbed = new EmbedBuilder();
    private EmbedBuilder timerEmbed2 = new EmbedBuilder();
    String fieldHeader;
    private Help help;
    private boolean timerRunning = false;
    private boolean commandsSuspended = false;
    private boolean isRestart;
    private boolean isReload = false;
    private List<String> commands = new ArrayList<>();
    private ArrayList<Long> pingCooldownDiscordIDs = new ArrayList<>();
    private ArrayList<Date> pingCooldownOverTimes = new ArrayList<>();
    ArrayList<Thread> threadList = new ArrayList<>();
    private Timer timer;
    private Timer timer2;

    DiscordBotMain(boolean isRestart) throws IOException, TimeoutException {
        core.startup(false);
        botConfig = new BotConfiguration(fileHandler.getConfig()){};
        botConfig.initialSetup();
        this.fieldHeader = botConfig.fieldHeader;
        this.isRestart = isRestart;
        this.embed = new EmbedDesigner(this){};
        this.help = new Help(this);
        log.info("All Classes Constructed");
        commands.addAll(
                Arrays.asList("botAbuse", "ba", "permBotAbuse", "pba", "undo", "check",
                        "checkHistory", "clear", "transfer", "ping", "reasonsmanager", "rmgr"));
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        botConfig.guild = event.getJDA().getGuilds().get(0);
        this.guild = botConfig.guild;
        if (!botConfig.configsExist()) {
            commandsSuspended = true;
            log.fatal("Not All of the Configuration Settings were found in the discord server! Please verify the IDs of" +
                    " all of the channels, roles, and the Owner's Discord ID in the configuration file. " +
                    "Commands have been suspended, when you fix the configuration file " +
                    "you may use \"/reload\" to reload the file or \"/restart\" to restart the bot");
        }
        else botConfig.discordSetup();
        init();
    }

    @Override
    public void onResume(@Nonnull ResumedEvent event) {
        init();
    }

    @Override
    public void onReconnect(@Nonnull ReconnectedEvent event) {
        init();
    }

    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // If they're supposed to be Bot Abused and they don't have the role on join
        if (core.botAbuseIsCurrent(event.getMember().getIdLong()) &&
                !event.getMember().getRoles().contains(botConfig.botAbuseRole)) {
            guild.addRoleToMember(event.getMember().getIdLong(),
                   botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
            embed.setAsInfo("Join Event Information");
            embed.addMessage("**[System - Join Event] Added the Bot Abuse Role to "
                    + event.getMember().getAsMention() +
                    " since according to the data file they should have the Bot Abuse role**");
            sendToLogChannel();
            log.info("Join Event - Added Bot Abuse Role to " + event.getMember().getEffectiveName());
        }
        // If they're not supposed to be Bot Abused and they do have the role
        else if (!core.botAbuseIsCurrent(event.getMember().getIdLong()) &&
                event.getMember().getRoles().contains(botConfig.botAbuseRole)) {
            guild.removeRoleFromMember(event.getMember().getIdLong(),
                    botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
            embed.setAsInfo("Join Event Information");
            embed.addMessage("**[System - Join Event] Removed the Bot Abuse Role from "
                    + event.getMember().getAsMention() +
                    " since according to the data file they shouldn't have it**");
            sendToLogChannel();
            log.info("Join Event - Removed Bot Abuse Role from " + event.getMember().getEffectiveName());
        }
    }
    @Override
    public void onDisconnect(@Nonnull DisconnectEvent event) {
        try {
            log.error("Disconneted from Discord Websocket - Saving Data...");
            if (core.arraySizesEqual()) {
                fileHandler.saveDatabase();
            }
            else {
                log.fatal("Datafile is damaged on Disconnect - Reloading to Prevent Damage");
                core.startup(true);
            }
        }
        catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        Message msg = event.getMessage();
        Member author = event.getMember();
        if (event.getAuthor().isBot() || msg.getChannelType() == ChannelType.PRIVATE) return;
        boolean isTeamMember = msg.getMember().getRoles().contains(botConfig.teamRole);
        boolean isStaffMember = msg.getMember().getRoles().contains(botConfig.staffRole) ||
                msg.getMember().getRoles().contains(botConfig.adminRole);

        String[] args = msg.getContentRaw().substring(1).split(" ");
        if (msg.getContentRaw().charAt(0) == '/' && !commandsSuspended && !args[0].equalsIgnoreCase("help")
                && !args[0].equalsIgnoreCase("restart") && !args[0].equalsIgnoreCase("reload"))  {
            // Command Syntax /botabuse <Mention or Discord ID> <Reason (kick, offline, or staff)> <proof url>
            if (args[0].equalsIgnoreCase("botabuse") || args[0].equalsIgnoreCase("ba")) {
                if ((isTeamMember || author == botConfig.owner) &&
                        (args.length == 3 || args.length == 4)) {
                    setBotAbuse(msg);
                }
                else if ((args.length < 3 || args.length > 4) && (isTeamMember || author == botConfig.owner)) {
                    embed.setAsError("Error - Invalid Number of Arguements");
                    embed.addMessage("**[System] You Entered an Invalid Number of Arguments**");
                    sendToTeamDiscussionChannel(true, msg.getMember());
                }
                else { // If they Don't have the Team role then it returns an error message
                    embed.setAsError("Error - No Permissions");
                    embed.addMessage(":x: **[System] You Lack Permissions to do that!**");
                    sendToHelpChannel(true, msg.getChannel(), msg.getMember());
                }
            }
            else if (args[0].equalsIgnoreCase("permbotabuse") || args[0].equalsIgnoreCase("pba")) { // /permbotabuse <Mention or Discord ID> [Image]
                if ((isStaffMember || author == botConfig.owner) && (args.length == 2 || args.length == 3)) {
                    permBotAbuse(msg);
                }
                else if ((isStaffMember || author == botConfig.owner) && (args.length < 2 || args.length > 3)) {
                    embed.setAsError("Error - Invalid Number of Arguements");
                    embed.addMessage(":x: **[System] You Entered an Invalid Number of Arguments**");
                    sendToTeamDiscussionChannel(true, msg.getMember());
                }
                else {
                    embed.setAsError("Error - No Permissions");
                    embed.addMessage(":x: **[System] You Lack Permissions to do that!**");
                    sendToHelpChannel(true, msg.getChannel(), msg.getMember());
                }
            }
            else if (args[0].equalsIgnoreCase("undo")) {
                if (isTeamMember) {
                    undoCommand(msg);
                }
                else {
                    embed.setAsError("Error - No Permissions");
                    embed.addMessage(":x: **[System] You Lack Permissions to do that!**");
                    sendToHelpChannel(true, msg.getChannel(), msg.getMember());
                }
            }
            else if (args[0].equalsIgnoreCase("check")) {
                // This handles a /check for someone to check their own Bot Abuse status or someone else's.
                checkCommand(msg, isTeamMember);
            }
            else if (args[0].equalsIgnoreCase("transfer")) { // /transfer <Old Mention or Discord ID> <New Mention or Discord ID>
                if (isStaffMember || author == botConfig.owner) {
                    try {
                        transferRecords(msg);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    embed.setAsError("Error - No Permissions");
                    embed.addMessage(":x: **[System] You Lack Permissions to do that!**");
                    sendToHelpChannel(true, msg.getChannel(), msg.getMember());
                }
            }
            else if (args[0].equalsIgnoreCase("clear")) {
                if (isStaffMember || author == botConfig.owner) {
                    clearCommand(msg);
                }
                else {
                    embed.setAsError("Error - No Permissions");
                    embed.addMessage("**:x: [System] You Lack Permissions to do that!**");
                    sendToHelpChannel(true, msg.getChannel(), msg.getMember());
                }
            }
            else if (args[0].equalsIgnoreCase("checkhistory")) {
                checkHistory(msg, isTeamMember);
            }
            else if (args[0].equalsIgnoreCase("ping")) {
                embed.setAsInfo("My Ping Info");
                embed.addMessage(":ping_pong: **Pong!**" +
                        "\nMy Ping Time to Discord's Gateway: **" + msg.getJDA().getGatewayPing() + "ms**");
                if (isTeamMember) {
                    sendToTeamDiscussionChannel(false, null);
                }
                else {
                    try {
                        // If they use /ping before their cooldown time is over then we send them the ping information in a DM
                        if (Calendar.getInstance().getTime()
                                .before(pingCooldownOverTimes.get(pingCooldownDiscordIDs.lastIndexOf(msg.getMember().getIdLong())))
                        && msg.getChannel() != botConfig.botSpamChannel) {
                            sendDM(msg.getMember().getUser());
                        }
                        // Otherwise we can send them this in the help channel.
                        else {
                            pingHandler(msg.getMember().getIdLong());
                            sendToHelpChannel(true, msg.getChannel(), msg.getMember());
                        }
                    }
                    // This would run if their discord ID wasn't found in pingCooldownDiscordIDs,
                    // a -1 would throw this exception
                    catch (IndexOutOfBoundsException ex) {
                        pingHandler(msg.getMember().getIdLong());
                        sendToHelpChannel(true, msg.getChannel(), msg.getMember());
                    }
                }
            }
            else if (args[0].equalsIgnoreCase("reasonsmanager") || args[0].equalsIgnoreCase("rmgr")
            || args[0].equalsIgnoreCase("reasons")) {
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
        else if (msg.getMentionedMembers().contains(guild.getSelfMember())) {
            msg.getChannel().sendMessage(":blobnomping:").queue();
        }
        else if ((msg.getContentRaw().charAt(0) == '/' && args[0].equalsIgnoreCase("restart"))
                && (isStaffMember || author == botConfig.owner)) {
            msg.delete().complete();
            try {
                embed.setAsWarning("Restart Initiated");
                embed.addMessage("**Restart Initiated by " + msg.getMember().getAsMention()
                        + "\nPlease Allow up to 10 seconds for this to complete**");
                log.warn(msg.getMember().getEffectiveName() + " Invoked a Restart");
                sendToTeamDiscussionChannel(false, null);
                Thread.sleep(5000);
                core.startup(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if ((msg.getContentRaw().charAt(0) == '/' && args[0].equalsIgnoreCase("reload"))
                && (isStaffMember || author == botConfig.owner)) {
            embed.setAsWarning("Reloading Configuration");
            embed.addMessage("**Reloading Configuration... Please Wait a Few Moments...**");
            sendToTeamDiscussionChannel(false, null);
            try {
                JsonObject newConfig = core.fileHandler.getConfig();
                if (botConfig.reload(newConfig)) log.info("Successfully Reloaded Discord Bot Configuration");
                else log.fatal("Configuration Problem Found on Reload - One or More of the Configurations Don't Exist" +
                        " in the discord server");
                core.coreConfig.reload(newConfig);
                timer.cancel();
                timer.purge();
                timer2.cancel();
                timer2.purge();
                timerRunning = false;
                isReload = true;
                init();

            }
            catch (FileNotFoundException e) {
                embed.setAsStop("Config File Not Found");
                embed.addMessage("**:x: [System] Configuration File Not Found**");
                log.error("Configuration File Not Found on Reload");
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
        }
        else if (msg.getContentRaw().charAt(0) == '/' && args[0].equalsIgnoreCase("help")) {
            helpCommand(msg, isTeamMember);
        }
        else if (commandsSuspended) {
            embed.setAsStop("Commands Suspended");
            if (!isTeamMember) {
                embed.addMessage("**Commands are Temporarily Suspended... Sorry for the inconvience...**");
                sendToHelpChannel(true, msg.getChannel(), msg.getMember());
            }
            else {
                embed.addMessage("**Commands are Temporarily Suspended... " +
                        "Please Post The Action you were trying to do in either a DM with" + botConfig.owner.getAsMention() + " or in this channel.**");
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
        }
        if (!msg.getMentionedMembers().contains(guild.getSelfMember())
                && msg.getChannel() != botConfig.botSpamChannel && msg.getChannel() != botConfig.managementChannel
                && msg.getContentRaw().charAt(0) == '/' && msg.getAttachments().isEmpty()) {
            msg.delete().queue();
        }
    }
    private void init() {
        // Here we're running an integrity check on the data that was loaded, if the data loaded is no good...
        // then we suspend all commands and we don't start the timers.
        if (!core.arraySizesEqual() && !commandsSuspended) {
            commandsSuspended = true;
            log.fatal("Data File Damaged on Initiation");
            log.warn("Commands are now Suspended");
        }
        // If the init method was initiated from a restart then this'll run.
        if (isRestart) {
            embed.setAsSuccess("Restart Complete");
            if (!commandsSuspended) {
                embed.addMessage("**I'm Back Fellas! Restart is Complete!**");
            }
            else {
                embed.setAsStop("Restart Error");
                embed.addMessage("**Restart Complete but the Data File is Still Not Usable**");
            }
            sendToTeamDiscussionChannel(false, null);
        }
        // If init was initiated from a config reload then this'll run.
        if (isReload) {
            embed.setAsSuccess("Reload Complete");
            embed.addMessage("**Reloading Config was Successfully Completed!**");
            sendToTeamDiscussionChannel(false, null);
        }
        // If the timers aren't running and commands aren't suspended then this'll run. Or... if a reload came in.
        if ((!timerRunning && !commandsSuspended) || isReload) {
            if (!isRestart) {
                botConfig.discussionChannel.sendMessage(":wave: Hey Folks! I'm Ready To Fly!").queue();
            }
            timer = new Timer();
            timer2 = new Timer();
            timerRunning = true;
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    // Timer for executing the checkExpiredBotAbuse method each second.
                    long removedID = 0;
                    try {
                        removedID = core.checkExpiredBotAbuse();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (removedID != 0) {
                        try {
                            // For Printing in the Console and in Discord A Bot Abuse role has been removed.
                            timerEmbed.setColor(Color.GREEN);
                            timerEmbed.setTitle("Successfully Removed Expired Bot Abuse");
                            timerEmbed.setThumbnail(embed.checkIcon);
                            timerEmbed.addField(fieldHeader, "**:white_check_mark: [System] Removed Expired Bot Abuse for "
                                    + guild.getMemberById(removedID).getAsMention() + "**", true);
                            botConfig.logChannel.sendMessage(timerEmbed.build()).queue();
                            guild.removeRoleFromMember(removedID,
                                    botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                            log.info("Successfully Removed the Bot Abuse role from "  +
                                    guild.getMemberById(removedID).getEffectiveName());
                        }
                        catch (ErrorResponseException ex) {
                            // For Printing in Console and in Discord the Role couldn't be removed
                            // because the role was not found on the player.
                            timerEmbed.setColor(Color.YELLOW);
                            timerEmbed.setTitle("Expired Bot Abuse Error");
                            timerEmbed.setThumbnail(embed.warningIcon);
                            timerEmbed.addField(fieldHeader,
                                    "**Bot Abuse just expired for " +  guild.getMemberById(removedID).getAsMention()
                                            + " and they did not have the Bot Abuse role", true);
                            botConfig.logChannel.sendMessage(timerEmbed.build()).queue();
                            log.warn("Bot Abuse just expired for " + guild.getMemberById(removedID).getEffectiveName()
                            + " and they did not have the Bot Abuse role");
                        }
                        catch (NullPointerException ex) {
                            // The Player whos Bot Abuse role was supposed to expire does not exist in the server
                            timerEmbed.setColor(Color.YELLOW);
                            timerEmbed.setTitle("Expired Bot Abuse Error");
                            timerEmbed.setThumbnail(embed.warningIcon);
                            timerEmbed.addField(fieldHeader, "**Successfully Removed Expired Bot Abuse for "
                                    + removedID + " but they did not exist in the discord server!**", true);
                            botConfig.logChannel.sendMessage(timerEmbed.build());
                            log.warn("Successfully Removed Expired Bot Abuse for "
                                    + removedID + " but they did not exist in the discord server!");
                        }
                        timerEmbed.clearFields();
                    }
                }
            }, 0, 1000);
            // Configurable Periodic Scan of Players that should be Bot Abused to ensure that they have the role
            // Followed by a Scan of All Players to look for any Bot Abuse roles that did not get removed when they should have
            timer2.schedule(new TimerTask() {
                @Override
                public void run() {
                    List<Member> serverMembers = guild.getMembers();
                    int index = 0;
                    timerEmbed2.setColor(Color.BLUE);
                    timerEmbed2.setTitle("Role Scanner Information");
                    timerEmbed2.setThumbnail(embed.infoIcon);
                    while (index < core.currentBotAbusers.size()) {
                        if (serverMembers.contains(guild.getMemberById(core.currentBotAbusers.get(index))) &&
                                !guild.getMemberById(core.currentBotAbusers.get(index)).getRoles().contains(botConfig.botAbuseRole)) {
                            guild.addRoleToMember(guild.getMemberById(core.currentBotAbusers.get(index)),
                                    botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                            timerEmbed2.addField(fieldHeader, "[System - Role Scanner] Added Bot Abuse Role to "
                                    + guild.getMemberById(core.currentBotAbusers.get(index)).getAsMention() +
                                    " because they didn't have the role... and they're supposed to have it.",true);
                            botConfig.logChannel.sendMessage(timerEmbed2.build()).queue();
                            log.info("Added Bot Abuse to " +
                                    guild.getMemberById(core.currentBotAbusers.get(index)).getEffectiveName()
                            + " because they didn't have the role... and they're supposed to have it.");
                            timerEmbed2.clearFields();
                        }
                        index++;
                    }
                    index = 0;
                    while (index < serverMembers.size()) {
                        if (serverMembers.get(index).getRoles().contains(botConfig.botAbuseRole)
                                && !core.botAbuseIsCurrent(serverMembers.get(index).getIdLong())) {
                            guild.removeRoleFromMember(serverMembers.get(index).getIdLong(),
                                    botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                            timerEmbed2.addField(fieldHeader, "[System - Role Scanner] Removed Bot Abuse Role from "
                                    + serverMembers.get(index).getAsMention() + " because they had the role... " +
                                    "and they weren't supposed to have it.", true );
                            botConfig.logChannel.sendMessage(timerEmbed2.build()).queue();
                            log.info("Removed Bot Abuse Role from " + serverMembers.get(index).getEffectiveName() +
                                    " because they had the role... and they were not supposed to have it");
                            timerEmbed2.clearFields();
                        }
                        index++;
                    }

                }
            }, 0, botConfig.roleScannerInterval * 60000);
            log.info("Timers are Running");
        }
        else if (commandsSuspended) {
            try {
                embed.setAsStop("Commands Suspended");
                embed.addMessage(":x: **[System] Either the Data File has been Damaged or there's configuration problems" +
                        "\n\nCommands Have Been Suspended**");
                botConfig.discussionChannel.sendMessage(botConfig.owner.getAsMention()).queue();
                sendToTeamDiscussionChannel(false, null);
                embedBuilder.clearFields();
            }
            catch (NullPointerException ex) {
                log.fatal("Definitely Configuration Problems - When trying to send the stop message something " +
                        "threw a NullPointerException");
            }
        }
        isRestart = false;
        isReload = false;
    }
    ///////////////////////////////////////////////////////////////////
    // Divider Between Event Handlers and Command Handlers
    //////////////////////////////////////////////////////////////////
    private void setBotAbuse(Message msg) {

        String[] args = msg.getContentRaw().substring(1).split(" ");

        if (args[1].isEmpty()) {
            msg.getChannel().sendMessage(":x: " + msg.getAuthor().getAsMention() + " [System] I was expecting a target player").queue();
        }
        else if (msg.getMentionedMembers().isEmpty()) {
            try {
                if (msg.getAttachments().isEmpty()) {
                    String result = core.setBotAbuse(Long.parseLong(args[1]), false, args[2], args[3], msg.getMember().getAsMention());
                    if (result.contains("FATAL ERROR")) {
                        failedIntegrityCheck(msg.getMember(), "setBotAbuse - No Picture Attachment and Long Member Value");
                    }
                    else if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess("Successful Bot Abuse");
                        embed.addMessage(result);
                        sendToLogChannel();
                        guild.addRoleToMember(guild.getMemberById(Long.parseLong(args[1])),
                                botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                        embed.addMessage(":white_check_mark: " + " Successfully Bot Abused "
                                + guild.getMemberById(Long.parseLong(args[1])).getAsMention());
                        sendToTeamDiscussionChannel(true, msg.getMember());
                        log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + guild.getMemberById(Long.parseLong(args[1])).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setAsError("Whoops... Something went wrong");
                        embed.addMessage(result);
                        sendToTeamDiscussionChannel(false, null);
                    }
                }
                else if (msg.getAttachments().size() == 1 &&
                        (msg.getChannel() == botConfig.discussionChannel || msg.getChannel() == botConfig.managementChannel)) {
                    String result = core.setBotAbuse(Long.parseLong(args[1]),
                            false, args[2], msg.getAttachments().get(0).getProxyUrl(), msg.getMember().getAsMention());
                    if (result.contains("FATAL ERROR")) {
                        embed.setAsStop("FATAL ERROR");
                        embed.addMessage("**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                                " while I reload and then you can try to run that command again**");
                        sendToTeamDiscussionChannel(true, msg.getMember());
                        failedIntegrityCheck(msg.getMember(), "setBotAbuse - Picture Attachment Found and Long Member Value");
                    }
                    else if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess("Successful Bot Abuse");
                        embed.addMessage(result);
                        sendToLogChannel();
                        guild.addRoleToMember(guild.getMemberById(Long.parseLong(args[1])),
                                botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                        embed.addMessage(":white_check_mark: " + " Successfully Bot Abused "
                                + guild.getMemberById(Long.parseLong(args[1])).getAsMention());
                        sendToTeamDiscussionChannel(true, msg.getMember());
                        log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + guild.getMemberById(Long.parseLong(args[1])).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setAsError("Whoops... Something went wrong");
                        embed.addMessage(result);
                        sendToTeamDiscussionChannel(false, null);
                    }
                }
                else if (msg.getAttachments().size() == 1
                        && msg.getChannel() != botConfig.discussionChannel && msg.getChannel() != botConfig.managementChannel) {
                    embed.setAsError("Channel Error for This Action");
                    embed.addMessage("**That was the Wrong Channel to attach an image to this command. " +
                                    "Do that again but please use this channel.**" +
                                    "\n\n**:warning: DO NOT delete the command for me as your command will not be deleted " +
                                    "in order for the attachment to be saved by Discord's servers**");
                    sendToTeamDiscussionChannel(true, msg.getMember());
                }
            }
            catch (NumberFormatException ex) {
                embed.setAsError("Error While Setting Bot Abuse");
                embed.addMessage(":x: [System] The Discord ID cannot contain any letters or special characters");
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
            catch (NullPointerException ex) {
                embedBuilder.clearFields();
                embed.setAsWarning("Exception Caught but Successful Bot Abuse");
                embed.addMessage("Caught a NullPointerException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Bot Abuse for " + Long.parseLong(args[1]) + " to the Database**");
                sendToTeamDiscussionChannel(true, msg.getMember());
                log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused " + args[1]);
            }
            catch (IllegalArgumentException ex) {
                embedBuilder.clearFields();
                embed.setAsWarning("Exception Caught but Successful Bot Abuse");
                embed.addMessage("Caught a IllegalArgumentException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Bot Abuse for " + Long.parseLong(args[1]) + " to the Database**");
                sendToTeamDiscussionChannel(true, msg.getMember());
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
                    String result = core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                            false, args[2], args[3], msg.getMember().getAsMention());
                    guild.addRoleToMember(msg.getMentionedMembers().get(0),
                            botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                    if (result.contains("FATAL ERROR")) {
                        failedIntegrityCheck(msg.getMember(), "setBotAbuse - No Picture Attachment and Mention Member Value");
                    }
                    else if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess("Successful Bot Abuse");
                        embed.addMessage(result);
                        sendToLogChannel();
                        embed.addMessage("**:white_check_mark: " +
                                " Successfully Bot Abused " + msg.getMentionedMembers().get(0).getAsMention() + "**");
                        sendToTeamDiscussionChannel(true, msg.getMember());
                        log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setAsError("Whoops... Something went wrong");
                        embed.addMessage(result);
                        sendToTeamDiscussionChannel(false, null);
                    }
                }
                else if (msg.getAttachments().size() == 1 &&
                        (msg.getChannel() == botConfig.discussionChannel || msg.getChannel() == botConfig.managementChannel)) {
                    String result = core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                            false, args[2], msg.getAttachments().get(0).getProxyUrl(), msg.getMember().getAsMention());
                    guild.addRoleToMember(msg.getMentionedMembers().get(0),
                            botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                    if (result.contains("FATAL ERROR")) {
                        failedIntegrityCheck(msg.getMember(), "setBotAbuse - Picture Attachment Found and Mention Member Value");
                    }
                    else if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess("Successful Bot Abuse");
                        embed.addMessage(result);
                        sendToLogChannel();
                        embed.addMessage("**:white_check_mark: " + msg.getAuthor().getAsMention()
                                + " Successfully Bot Abused " + msg.getMentionedMembers().get(0).getAsMention() + "**");
                        sendToTeamDiscussionChannel(false, null);
                        log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setAsError("Whoops... Something went wrong");
                        embed.addMessage(result);
                        sendToTeamDiscussionChannel(false, null);
                    }
                }
                else if (msg.getAttachments().size() == 1
                        && msg.getChannel() != botConfig.discussionChannel && msg.getChannel() != botConfig.managementChannel) {
                    embed.setAsError("Channel Error for This Action");
                    embed.addMessage("**That was the Wrong Channel to attach an image to this command. " +
                                    "Do that again but please use this channel.**" +
                                    "\n\n**:warning: DO NOT delete the command for me as your command will not be deleted " +
                                    "in order for the attachment to be saved by Discord's servers**");
                    sendToTeamDiscussionChannel(true, msg.getMember());
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() > 1 ) {
            embed.setAsError("Target ID Error");
            embed.addMessage(":x: [System] Too many Target IDs");
            sendToTeamDiscussionChannel(true, msg.getMember());
        }
    }
    private void permBotAbuse(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");

        // If length is 3, then an image url was provided.
        if (msg.getMentionedMembers().isEmpty() && args.length == 3) {
            try {
                embed.setAsSuccess("Successful Perm Bot Abuse");
                embed.addMessage(core.setBotAbuse(Long.parseLong(args[1]),
                        true, "staff", args[2] , msg.getMember().getAsMention()));
                sendToLogChannel();
                guild.addRoleToMember(guild.getMemberById(Long.parseLong(args[1])),
                        botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                embed.addMessage(msg.getAuthor().getAsMention() + " Permanently Bot Abused " +
                        guild.getMemberById(Long.parseLong(args[1])).getAsMention());
                sendToTeamDiscussionChannel(true, msg.getMember());
                log.info("[Admin Override] " + msg.getMember().getEffectiveName() +
                        " Successfully Permanently Bot Abused " + guild.getMemberById(Long.parseLong(args[1])).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                embed.setAsError("Error While Setting Perm Bot Abuse");
                embed.addMessage("[System] Invalid User ID!");
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
            catch (ArrayIndexOutOfBoundsException ex) {
                embed.setAsError("Error While Setting Perm Bot Abuse");
                embed.addMessage("[System] Invalid Number of Arguments!");
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
            catch (NullPointerException ex) {
                embedBuilder.clearFields();
                embed.setAsWarning("Exception Caught but Successful Perm Bot Abuse");
                embed.addMessage("Caught a NullPointerException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Perm Bot Abuse for that Discord ID to the Database**");
                sendToTeamDiscussionChannel(false, null);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() == 1 && args.length == 3) {
            try {
                embed.setAsSuccess("Successful Perm Bot Abuse");
                embed.addMessage(core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                        true, "staff", args[2], msg.getMember().getAsMention()));
                sendToLogChannel();
                guild.addRoleToMember(msg.getMentionedMembers().get(0),
                        botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                log.info("[Admin Override] " + msg.getMember().getEffectiveName()
                        + " Successfully Permanently Bot Abused " + msg.getMentionedMembers().get(0).getEffectiveName());
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2) {
            try {
                embed.setAsSuccess("Successful Perm Bot Abuse");
                embed.addMessage(core.setBotAbuse(Long.parseLong(args[1]), true,"staff", null, msg.getMember().getAsMention()));
                sendToLogChannel();
                guild.addRoleToMember(guild.getMemberById(Long.parseLong(args[1])),
                        botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                log.info("[Admin Override] " + msg.getMember().getEffectiveName()
                        + " Successfully Permanently Bot Abused " + guild.getMemberById(args[1]).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                embed.setAsError("Error in Setting Perm Bot Abuse");
                embed.addMessage( "**:x: [System] Invalid User ID!**");
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
            catch (ArrayIndexOutOfBoundsException ex) {
                embed.setAsError("Error in Setting Perm Bot Abuse");
                embed.addMessage("**:x: [System] Invalid Number of Arguements!**");
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
            catch (NullPointerException ex) {
                embedBuilder.clearFields();
                embed.setAsWarning("Exception Caught but Successful Perm Bot Abuse");
                embed.addMessage(":white_check_mark: " + msg.getMember().getEffectiveName()
                        + " Permanently Bot Abused " + args[1] + " who does not exist on the Discord Server");
                sendToTeamDiscussionChannel(false, null);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() == 1 && args.length == 2) {
            try {
                embed.setAsSuccess("Successful Perm Bot Abuse");
                embed.addMessage(core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                        true, "staff", null, msg.getMember().getAsMention()));
                sendToLogChannel();
                guild.addRoleToMember(msg.getMentionedMembers().get(0),
                        botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                log.info("[Admin Override] " + msg.getMember().getEffectiveName()
                        + " Successfully Permanently Bot Abused " + msg.getMentionedMembers().get(0).getEffectiveName());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            embed.setAsError("Mentioned Players Error");
            embed.addMessage("**:x: [System] Too Many Mentioned Players!**");
            sendToTeamDiscussionChannel(true, msg.getMember());
        }
    }
    private void undoCommand(Message msg) {

        String[] args = msg.getContentRaw().substring(1).split(" ");
        embed.setAsSuccess("Successful Undo");
        long lastDiscordID = core.discordID.get(core.issuingTeamMember.lastIndexOf(msg.getMember().getAsMention()));
        try {
            if (args.length == 1) {
                guild.removeRoleFromMember(guild.getMemberById(core.discordID.get(core.issuingTeamMember.lastIndexOf(msg.getMember().getAsMention()))),
                        botConfig.botAbuseRole).complete();
                String result = core.undoBotAbuse(msg.getMember().getAsMention(), true,  0 );
                if (result.contains("FATAL ERROR")) {
                    failedIntegrityCheck(msg.getMember(), "undo Command - No Member of who to Undo");
                }
                else if (result.contains(":x:")) {
                    embed.setAsError("Error While Undoing");
                    embed.addMessage(result);
                    sendToTeamDiscussionChannel(true, msg.getMember());
                }
                else {
                    embed.addMessage(result);
                    log.info(msg.getMember().getEffectiveName() + " just undid their last Bot Abuse");
                    sendToTeamDiscussionChannel(true, msg.getMember());
                }
            }
            else if (args.length == 2 && msg.getMentionedMembers().isEmpty()) {
                guild.removeRoleFromMember(Long.parseLong(args[1]),
                        botConfig.botAbuseRole).complete();
                String result = core.undoBotAbuse(msg.getMember().getAsMention(), false, Long.parseLong(args[1]));
                if (result.contains("FATAL ERROR")) {
                    failedIntegrityCheck(msg.getMember(), "undo Command - Long Value of Member to Undo");
                }
                else if (result.contains(":x:")) {
                    embed.setAsError("Error While Undoing");
                    embed.addMessage(result);
                    sendToTeamDiscussionChannel(true, msg.getMember());
                }
                else {
                    embed.addMessage(result);
                    log.info(msg.getMember().getEffectiveName() + " just undid the Bot Abuse for "
                    + guild.getMemberById(args[1]).getEffectiveName());
                    sendToTeamDiscussionChannel(true, msg.getMember());
                }
            }
            else if (args.length == 2 && msg.getMentionedMembers().size() == 1) {
                guild.removeRoleFromMember(msg.getMentionedMembers().get(0),
                        botConfig.botAbuseRole).complete();
                String result = core.undoBotAbuse(msg.getMember().getAsMention(), false, msg.getMentionedMembers().get(0).getIdLong());
                if (result.contains("FATAL ERROR")) {
                    failedIntegrityCheck(msg.getMember(), "undo Command - Mention Value of who to Undo");
                }
                if (result.contains(":x:")) {
                    embed.setAsError("Error While Undoing");
                    embed.addMessage(result);
                    sendToTeamDiscussionChannel(true, msg.getMember());
                }
                else {
                    embed.addMessage(result);
                    log.info(msg.getMember().getEffectiveName() + " just undid the Bot Abuse for "
                            + msg.getMentionedMembers().get(0).getEffectiveName());
                    sendToTeamDiscussionChannel(true, msg.getMember());
                }
            }
        }
        catch (NullPointerException ex) {
            embedBuilder.clearFields();
            embed.setAsWarning("Exception Caught but Successful Undo");
            embed.addMessage("Caught a NullPointerException" +
                    "\n**[System] The Bot Abuse role could not be undone to that Discord ID as they Don't Exist in the Server!**" +
                    "\n **Successfully Removed A Bot Abuse for " + lastDiscordID + " from the Database**");
            sendToTeamDiscussionChannel(true, msg.getMember());
            log.info(msg.getMember().getEffectiveName() + " Successfully Undid Bot Abuse for " + lastDiscordID);
        }
        catch (IllegalArgumentException ex) {
            embedBuilder.clearFields();
            embed.setAsWarning("Exception Caught but Successful Undo");
            embed.addMessage("Caught a IllegalArgumentException" +
                    "\n**[System] The Bot Abuse role could not be undone to that Discord ID as they Don't Exist in the Server!**" +
                    "\n **Successfully Removed A Bot Abuse for " + lastDiscordID + " from the Database**");
            sendToTeamDiscussionChannel(true, msg.getMember());
            log.info(msg.getMember().getEffectiveName() + " Successfully Undid Bot Abuse for " + lastDiscordID);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        embedBuilder.clearFields();
    }
    private void checkCommand(Message msg, boolean isTeamMember) {
        // Thoughout this Method, a 100 is a placeholder in the timeOffset arguement of core.getInfo
        // 100 = No Time Offset Provided

        String[] args = msg.getContentRaw().substring(1).split(" ");
        embed.setAsInfo("Bot Abuse Information");

        // This handles a /check for someone to check their own Bot Abuse status
        if (msg.getMentionedMembers().isEmpty() && args.length == 1) {
            String result = core.getInfo(msg.getMember().getIdLong(), 100, false);
            if (result.contains(":white_check_mark:")) {
                embed.setAsSuccess("You Are Not Bot Abused");
            }
            embed.addMessage(result);
            sendToHelpChannel(true, msg.getChannel(), msg.getMember());
            log.info(msg.getMember().getEffectiveName() + " just checked on their own Bot Abuse status");
        }
        // This handles if the player opts for a Direct Message instead "/check dm"
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2 && args[1].equalsIgnoreCase("dm")) {
            String result = core.getInfo(msg.getMember().getIdLong(), 100, false);
            if (result.contains(":white_check_mark:")) {
                embed.setAsSuccess("You Are Not Bot Abused");
            }
            embed.addMessage(result);
            sendDM(msg.getAuthor());
            log.info(msg.getMember().getEffectiveName() + " just checked their own Bot Abuse status and opted for a DM");
        }
        // /check <Discord ID>
        else if (isTeamMember && args.length == 2 && msg.getMentionedMembers().isEmpty()) {
            try {
                String result = core.getInfo(Long.parseLong(args[1]), 100 ,true);
                if (result.contains(":white_check_mark:")) {
                    embed.setAsError("Player Not Bot Abused");
                    embed.addMessage(":x: **This Player is Not Bot Abused**");
                }
                else {
                    embed.addMessage(result);
                }
                sendToTeamDiscussionChannel(true, msg.getMember());
                log.info(msg.getMember().getEffectiveName() + " just checked on "+
                        guild.getMemberById(Long.parseLong(args[1])).getEffectiveName() + "'s Bot Abuse Status");
            }
            catch (NumberFormatException ex) {
                embed.setAsError("Check Info Error");
                embed.addMessage(":x: **Invalid Discord ID**");
                sendToTeamDiscussionChannel(true, msg.getMember());
                log.info("Team Member " + msg.getMember().getEffectiveName() + " just entered an invalid Discord ID");
            }
        }
        // /check <Mention>
        else if ((msg.getMentionedMembers().size() == 1 && isTeamMember) && args.length == 2) {
            String result = core.getInfo(msg.getMentionedMembers().get(0).getIdLong(), 100 ,true);
            if (result.contains(":white_check_mark:")) {
                embed.setAsError("Player Not Bot Abused");
                embed.addMessage(":x: **This Player is Not Bot Abused**");
                log.error(msg.getMember().getEffectiveName() + " just checked on " +
                        msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status but they were not Bot Abused");
            }
            else {
                embed.addMessage(result);
                log.info(msg.getMember().getEffectiveName() + " just checked on "+
                        msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status");
            }
            sendToTeamDiscussionChannel(false, null);
            log.info(msg.getMember().getEffectiveName() + " just checked on "+
                    msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status");

        }
        // /check <Timezone Offset>
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2 && !isTeamMember) {
            if (core.checkOffset(args[1])) {
                String result = core.getInfo(msg.getMember().getIdLong(), Double.parseDouble(args[1]), false);
                if (result.contains(":white_check_mark:")) {
                    embed.setAsSuccess("You Are Not Bot Abused");
                }
                embed.addMessage(result);
                sendToHelpChannel(true, msg.getChannel(), msg.getMember());
                log.info(msg.getMember().getEffectiveName() +
                        " just checked on their own Bot Abuse status using TimeZone offset " + args[1]);
            }
            else {
                embed.setAsError("Check Info Error");
                embed.addMessage(":x: **Invalid Timezone Offset**");
                sendToHelpChannel(false, msg.getChannel(), null);
                log.error(msg.getMember().getEffectiveName() + " just entered an invalid TimeZone offset into /check");
            }
        }
        // /check [dm] <Timezone Offset>
        else if (msg.getMentionedMembers().isEmpty() && args.length == 3 && args[1].equalsIgnoreCase("dm")) {
            if (core.checkOffset(args[2])) {
                String result = core.getInfo(msg.getMember().getIdLong(), Float.parseFloat(args[2]), false);
                if (result.contains(":white_check_mark:")) {
                    embed.setAsSuccess("You Are Not Bot Abused");
                }
                embed.addMessage(result);
                sendDM(msg.getAuthor());
                log.info(msg.getMember().getEffectiveName() +
                        " just checked on their own Bot Abuse status while opting for a DM");
            }
            else {
                embed.setAsError("TimeZone Offset Error");
                embed.addMessage(":x: " + msg.getAuthor().getAsMention() + " **Invalid Timezone Offset**");
                sendToHelpChannel(false, msg.getChannel(), null);
                log.error(msg.getMember().getEffectiveName() +
                        " just entered an invalid Discord ID while opting for a DM");
            }
        }
        // /check <Timezone Offset> <Mention or Discord ID>
        else if (isTeamMember && args.length == 3) {
            if (core.checkOffset(args[1])) {
                if (msg.getMentionedMembers().isEmpty()) {
                    String result = core.getInfo(Long.parseLong(args[2]), Double.parseDouble(args[1]), true);
                    if (result.contains(":white_check_mark:")) {
                        embed.setAsError("Player Not Bot Abused");
                        embed.addMessage(":x: **This Player is Not Bot Abused**");
                        log.error(msg.getMember().getEffectiveName() + " just checked on " +
                                msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status but they were not Bot Abused");
                    }
                    else {
                        embed.addMessage(result);
                    }
                    sendToTeamDiscussionChannel(false, null);
                    log.info(msg.getMember().getEffectiveName() +
                            " just checked on " + guild.getMemberById(Long.parseLong(args[2])).getEffectiveName()
                            + "'s Bot Abuse status using TimeZone offset " + args[1]);
                }
                else if (msg.getMentionedMembers().size() == 1) {
                    String result = core.getInfo(msg.getMentionedMembers().get(0).getIdLong(), Double.parseDouble(args[1]), true);
                    if (result.contains(":white_check_mark:")) {
                        embed.setAsError("Player Not Bot Abused");
                        embed.addMessage(":x: **This Player is Not Bot Abused**");
                    }
                    else {
                        embed.addMessage(result);
                    }
                    sendToTeamDiscussionChannel(false, null);
                    log.info(msg.getMember().getEffectiveName() +
                            " just checked on " + msg.getMentionedMembers().get(0).getEffectiveName()
                            + "'s Bot Abuse status using TimeZone offset " + args[1]);
                }
            }
            else {
                embed.setAsError("Check Info Error");
                embed.addMessage(":x: **[System] Invalid Timezone Offset**");
                sendToTeamDiscussionChannel(false, null);
                log.error("Team Member " + msg.getMember().getEffectiveName() + " just entered an invalid TimeZone offset.");
            }

        }
        else {
            embed.setAsError("Permission Error");
            embed.addMessage("You Don't have Permission to check on someone else's Bot Abuse status");
            sendToHelpChannel(true, msg.getChannel(), msg.getMember());
            log.info("Successfully Denied Permission to " + msg.getMember().getEffectiveName() +
                    " for checking on someone else's Bot Abuse status");
        }
    }
    private void clearCommand(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");

        int index = 0;
        // This Handles the list of mentioned members
        while (index < msg.getMentionedMembers().size()) {

            // We now check if they have the Bot Abuse role, if they do then it's removed.
            if (msg.getMentionedMembers().get(index).getRoles().contains(botConfig.botAbuseRole)) {
                guild.removeRoleFromMember(msg.getMentionedMembers().get(index).getIdLong(),
                        botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                embed.setAsInfo("Bot Abuse Role Removed");
                embed.addMessage("**Successfully Removed Bot Abuse Role from "
                        + msg.getMentionedMembers().get(index).getAsMention() + " as their Records just got Cleared**");
                sendToLogChannel();
                log.info("Successfully Removed Bot Abuse Role from " +
                         msg.getMentionedMembers().get(index).getEffectiveName() + " as their Records just got Cleared");
            }
            embed.setAsSuccess("Successfully Cleared Records");
            try {
                int clearedRecords = core.clearRecords(msg.getMentionedMembers().get(index).getIdLong());
                if (clearedRecords == -1) {
                    failedIntegrityCheck(msg.getMember(), "clear Command - Mentioned Members");
                    break;
                }
                else if (clearedRecords == 0) {
                    embed.setAsError("No Records Cleared");
                    embed.addMessage("**[System] No Records Found for " + msg.getMentionedMembers().get(0).getEffectiveName() + "**");
                    sendToTeamDiscussionChannel(true, msg.getMember());
                    log.error("No Records Cleared for " + msg.getMentionedMembers().get(index).getEffectiveName());
                }
                else {
                    embed.addMessage(":white_check_mark: **[System] Successfully Cleared " +
                            clearedRecords + " Records from " + msg.getMentionedMembers().get(index).getAsMention() + "**");
                    sendToLogChannel();
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
            embed.setAsSuccess("Successfully Cleared Records");
            try {
                if (guild.getMemberById(Long.parseLong(args[index])).getRoles().contains(botConfig.botAbuseRole)) {
                    guild.removeRoleFromMember(Long.parseLong(args[index]),
                            botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                    embed.setAsInfo("Bot Abuse Role Removed");
                    embed.addMessage("**Successfully Removed Bot Abuse Role from "
                            + guild.getMemberById(Long.parseLong(args[index])).getEffectiveName() +
                            " as their Records just got Cleared**");
                    log.info("Successfully Removed Bot Abuse Role from " +
                            guild.getMemberById(Long.parseLong(args[index])).getEffectiveName() +
                            " as their Records just got Cleared");
                }
                int clearedRecords = core.clearRecords(Long.parseLong(args[index]));
                if (clearedRecords == -1) {
                    failedIntegrityCheck(msg.getMember(), "clear Command - Long Value of Member");
                    break;
                }
                if (clearedRecords == 0) {
                    embed.setAsError("No Records Cleared");
                    embed.addMessage("**[System] No Records Found for " + args[index] + "**");
                    sendToTeamDiscussionChannel(true, msg.getMember());
                    log.error("No Records Cleared for " + args[index]);
                }
                else {
                    embed.setAsSuccess("Successfully Cleared Records");
                    botConfig.logChannel.sendMessage(":white_check_mark: **[System] Successfully Cleared " +
                            clearedRecords + " Records from " +
                            guild.getMemberById(Long.parseLong(args[index])).getAsMention()).queue();
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
                    int clearedRecords = core.clearRecords(Long.parseLong(args[index]));
                    if (clearedRecords == -1) {
                        failedIntegrityCheck(msg.getMember(), "clear Command - Player Is Not in Discord Server");
                        break;
                    }
                    if (clearedRecords == 0) {
                        embed.setAsError("Error in Clearing Records");
                        sendToTeamDiscussionChannel(true, msg.getMember());
                        log.error("No Records Cleared for " + args[index]);
                    }
                    else {
                        embed.addMessage(":white_check_mark: **[System] Successfully Cleared " +
                                clearedRecords + " Records from " + args[index] + "**");
                        sendToLogChannel();
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
        embed.setAsSuccess("Successful Transfer of Records");

        if (args.length == 3) {
            if (msg.getMentionedMembers().size() == 2) {
                if (core.botAbuseIsCurrent(msg.getMentionedMembers().get(0).getIdLong())) {
                    guild.addRoleToMember(msg.getMentionedMembers().get(1).getIdLong(),
                            botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                    guild.removeRoleFromMember(msg.getMentionedMembers().get(0).getIdLong(),
                            botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                }
                log.info(msg.getMember().getEffectiveName() + " Successfully Transferred the Records of "
                        + msg.getMentionedMembers().get(0).getEffectiveName() + " to " + msg.getMentionedMembers().get(1).getEffectiveName());
                embed.addMessage(core.transferRecords(msg.getMentionedMembers().get(0).getIdLong(), msg.getMentionedMembers().get(1).getIdLong()));
                sendToLogChannel();
            }
            else if (msg.getMentionedMembers().size() == 1) {
                try {
                    // If they provide a Discord ID First and a Mention Last
                    if (core.botAbuseIsCurrent(Long.parseLong(args[1]))) {
                        guild.addRoleToMember(msg.getMentionedMembers().get(0).getIdLong(),
                                botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                        try {
                            guild.removeRoleFromMember(Long.parseLong(args[1]),
                                    botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                        }
                        catch (ErrorResponseException ex) {
                            embed.setAsWarning("Exception Caught - Player Does Not Exist");
                            embed.addMessage("**[System] Could Not Remove the Bot Abuse Role from "
                                    + args[1] + " because they do not exist in the Discord Server**" );
                            sendToLogChannel();
                        }
                    }
                    embed.setAsSuccess("Successful Transfer of Records");
                    embed.addMessage(core.transferRecords(Long.parseLong(args[1]), msg.getMentionedMembers().get(0).getIdLong()));
                    sendToLogChannel();
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
                    if (core.botAbuseIsCurrent(msg.getMentionedMembers().get(0).getIdLong())) {
                        try {
                            guild.addRoleToMember(Long.parseLong(args[2]),
                                    botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                        }
                        catch (ErrorResponseException e) {
                            embed.setAsWarning("Exception Caught - Player Does Not Exist");
                            embed.addMessage("**[System] Could Not Add the Bot Abuse Role to "
                                    + args[2] + " because they do not exist in the Discord Server**");
                            sendToLogChannel();
                        }
                        guild.removeRoleFromMember(msg.getMentionedMembers().get(0).getIdLong(),
                                botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                    }
                    embed.setAsSuccess("Successful Transfer of Records");
                    embed.addMessage(core.transferRecords(msg.getMentionedMembers().get(0).getIdLong(), Long.parseLong(args[2])));
                    sendToLogChannel();
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
                if (core.botAbuseIsCurrent(Long.parseLong(args[1]))) {
                    try {
                        guild.addRoleToMember(Long.parseLong(args[2]),
                                botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                    }
                    catch (ErrorResponseException ex) {
                        embed.setAsWarning("Exception Caught - Player Does Not Exist");
                        embed.addMessage("**[System] Could Not Add the Bot Abuse Role to "
                                + args[2] + " because they do not exist in the Discord Server**");
                        sendToLogChannel();
                        log.warn("Could Not Add the Bot Abuse Role to " +
                                args[2] + " because they do not exist in the Discord Server");
                    }
                    try {
                        guild.removeRoleFromMember(Long.parseLong(args[1]),
                                botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                    }
                    catch (ErrorResponseException ex) {
                        embed.setAsWarning("Exception Caught - Player Does Not Exist");
                        embed.addMessage("**[System] Could Not Remove the Bot Abuse Role from "
                                + args[1] + " because they do not exist in the Discord Server**");
                        sendToLogChannel();
                        log.warn("Could Not Remove the Bot Abuse Role from " + args[1] +
                                " because they do not exist in the Discord Server");
                    }
                }
                embed.setAsSuccess("Successful Transfer of Records");
                botConfig.logChannel.sendMessage(core.transferRecords(Long.parseLong(args[1]), Long.parseLong(args[2]))).queue();
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
                embed.setAsError("Error while Parsing Transfer Command");
                embed.addMessage("[System] Invalid Number of Mentions!" +
                                "\nUsage: /transfer <Old Mention or Discord ID> <New Mention or Discord ID>");
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
        }
        else {
            embed.setAsError("Error while Parsing Transfer Command");
            embed.addMessage("[System] Invalid Number of Arguments!" +
                            "\nUsage: /transfer <Old Mention or Discord ID> <New Mention or Discord ID>");
            msg.getChannel().sendMessage(embedBuilder.build()).queue();
        }
    }
    private void checkHistory(Message msg, boolean isTeamMember) {
        // Thoughout this Method, a 100 is a placeholder in the timeOffset arguement of core.seeHistory
        // 100 = No Time Offset Provided
        Member author = guild.getMember(msg.getAuthor());

        String[] args = msg.getContentRaw().substring(1).split(" ");
        embed.setAsInfo("Bot Abuse History Information");

        // /checkhistory <Mention or Discord ID>
        if (isTeamMember && args.length == 2) {
            try {
                // If the user provides a Discord ID
                String result = core.seeHistory(Long.parseLong(args[1]), 100, true);
                if (result.contains(":x:")) {
                    embed.setAsError("Bot Abuse History Information");
                }
                embed.addMessage(result);
                sendToTeamDiscussionChannel(false, null);
                embed.addMessage(":information_source: **[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                        guild.getMemberById(Long.parseLong(args[1])).getAsMention() + "**");
                sendToLogChannel();
                log.info(author.getEffectiveName() + " just checked the history of " +
                        guild.getMemberById(Long.parseLong(args[1])).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                try {
                    // The code above would throw a NumberFormatException if it's a mention
                    String result = core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), 100, true);
                    if (result.contains(":x:")) {
                        embed.setAsError("Bot Abuse History Information");
                    }
                    embed.addMessage(result);
                    sendToTeamDiscussionChannel(false, null);
                    embed.addMessage(":information_source: **[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                            msg.getMentionedMembers().get(0).getAsMention() + "**");
                    sendToLogChannel();
                }
                // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
                catch (IllegalArgumentException e) {
                    this.lengthyHistory(
                            core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), 100, true),
                            msg.getMember(), isTeamMember, null);
                }
                log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                        msg.getMentionedMembers().get(0).getEffectiveName());
            }
            // The Try code would throw a NullPointerException if the Discord ID Provided does not exist on the server.
            catch (NullPointerException f) {
                embed.setAsWarning("Bot Abuse History - Player Does Not Exist in the Server");
                embed.addMessage("**[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                        args[1] + " who currently does not exist within the Discord Server**");
                sendToLogChannel();
                log.info(msg.getMember().getEffectiveName() + " just checked the history of "  +
                        args[1] + " who currently does not exist within the Discord Server");
            }
            // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
            catch (IllegalArgumentException h) {
                this.lengthyHistory(core.seeHistory(Long.parseLong(args[1]), 100,true),
                        msg.getMember(), isTeamMember, null);
            }
            catch (IndexOutOfBoundsException j) {
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention() +
                        "**You shouldn't need to check your own Bot Abuse History... you're a Team Member!**").queue();
            }
        }
        // /checkhistory
        // Get the history of the player who used the command.
        else if (args.length == 1) {
            try {
                String result = core.seeHistory(msg.getMember().getIdLong(), 100,false);
                if (result.contains(":white_check_mark:")) {
                    embed.setAsSuccess("Your Bot Abuse History");
                }
                embed.addMessage(result);
                sendDM(msg.getAuthor());
                log.info(msg.getMember().getEffectiveName() + " just checked their own Bot Abuse History");
            }
            // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
            catch (IllegalArgumentException ex) {
                this.lengthyHistory(core.seeHistory(msg.getMember().getIdLong(), 100,false),
                        msg.getMember(), isTeamMember, null);
            }
        }
        // /checkhistory <timeOffset>
        else if (args.length == 2) {
            try {
                if (core.checkOffset(args[1])) {
                    String result = core.seeHistory(msg.getMember().getIdLong(), Double.parseDouble(args[1]), false);
                    embed.setAsInfo("Your Bot Abuse History");
                    if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess("Your Bot Abuse History");
                    }
                    embed.addMessage(result);
                    sendDM(msg.getAuthor());
                    log.info(msg.getMember().getEffectiveName() + " just checked their own Bot Abuse History" +
                            " using TimeZone offset " + args[1]);
                }
                else {
                    embed.setAsError("Error while Parsing Command");
                    embed.addMessage(":x: **Invalid Timezone Offset**");
                    sendToHelpChannel(true, msg.getChannel(), msg.getMember());
                }
            }
            catch (IllegalArgumentException ex) {
                this.lengthyHistory(core.seeHistory(msg.getMember().getIdLong(), Double.parseDouble(args[1]), false),
                        msg.getMember(), isTeamMember, args[1]);
            }
        }

        // /checkhistory <timeOffset> <Mention or Discord ID>
        else if (args.length == 3 && isTeamMember) {
            embed.setAsInfo("Bot Abuse History");
            if (core.checkOffset(args[1])) {
                try {
                    if (msg.getMentionedMembers().size() == 1) {
                        String result = core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), Double.parseDouble(args[1]), true);
                        if (result.contains(":x:")) {
                            embed.setAsError("Bot Abuse History");
                        }
                        embed.addMessage(result);
                        sendToTeamDiscussionChannel(false, null);
                        log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getMentionedMembers().get(0).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                    else if (msg.getMentionedMembers().isEmpty()) {
                        String result = core.seeHistory(Long.parseLong(args[2]), Double.parseDouble(args[1]), true);
                        if (result.contains(":x:")) {
                            embed.setAsError("Bot Abuse History");
                        }
                        embed.addMessage(result);
                        sendToTeamDiscussionChannel(false, null);
                        log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                guild.getMemberById(Long.parseLong(args[2])).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                }
                catch (IllegalArgumentException ex) {
                    try {
                        this.lengthyHistory(core.seeHistory(Long.parseLong(args[2]), Double.parseDouble(args[1]), true),
                                msg.getMember(), isTeamMember, args[1]);
                        log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                guild.getMemberById(Long.parseLong(args[2])).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                    catch (NumberFormatException e) {
                        this.lengthyHistory(core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), Double.parseDouble(args[1]), true),
                                msg.getMember(), isTeamMember, args[1]);
                        log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getMentionedMembers().get(0).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                }
            }
        }
        // No Permissions to check on someone elses Bot Abuse history
        else if (args.length > 1 && !isTeamMember) {
            embed.setAsError("Error - No Permissions");
            embed.addMessage(":x: " + msg.getAuthor().getAsMention() +
                    " **[System] You Don't Have Permission to check on someone elses Bot Abuse History**");
            sendToHelpChannel(true, msg.getChannel(), msg.getMember());
            log.warn(msg.getMember().getEffectiveName() +
                    " just tried to check someone elses Bot Abuse History but they did not have permission to");
        }
        else {
            embed.setAsStop("FATAL ERROR");
            embed.addMessage(":x: **[System] Something went Seriously wrong when that happened**");
            msg.getChannel().sendMessage(embedBuilder.build()).queue();
        }
    }
    private void helpCommand(Message msg, boolean isTeamMember) {

        String[] args = msg.getContentRaw().substring(1).split(" ");
        if (args.length == 1) {
            embed.setAsInfo("About /help");
            embed.addMessage("Syntax: `/help <Command Name>`");
            if (isTeamMember) {
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
            else {
                sendToHelpChannel(true, msg.getChannel(), msg.getMember());
            }
        }
        else if (args.length == 2) {
            if ((args[1].equalsIgnoreCase("botAbuse") || args[1].equalsIgnoreCase("ba"))
                    && isTeamMember) {
                help.botAbuseCommand();
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
            else if ((args[1].equalsIgnoreCase("permBotAbuse") || args[1].equalsIgnoreCase("pba"))
                    && isTeamMember) {
                help.permBotAbuseCommand();
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
            else if (args[1].equalsIgnoreCase("undo") && isTeamMember) {
                help.undoCommand();
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
            else if (args[1].equalsIgnoreCase("transfer") && isTeamMember) {
                help.transferCommand();
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
            else if (args[1].equalsIgnoreCase("clear") && isTeamMember) {
                help.clearCommand();
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
            else if (args[1].equalsIgnoreCase("check")) {
                help.checkCommand(isTeamMember);
                if (!isTeamMember) {
                    sendToHelpChannel(false, msg.getChannel(), null);
                }
                else {
                    sendToTeamDiscussionChannel(true, msg.getMember());
                }
            }
            else if (args[1].equalsIgnoreCase("checkHistory")) {
                help.checkHistoryCommand();
                if (!isTeamMember) {
                    sendToHelpChannel(false, msg.getChannel(), null);
                }
                else {
                    sendToTeamDiscussionChannel(true, msg.getMember());
                }
            }
            else if ((args[1].equalsIgnoreCase("reasonsmanager") || args[1].equalsIgnoreCase("rmgr")) && isTeamMember) {
                help.reasonManagementCommand();
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
            else if (isCommand(args[1]) && !isTeamMember) {
                embed.setAsError("No Permissions to Get This Help");
                embed.addMessage(":x: **[System] You Do Not Have Permissions to See This Help**");
                sendToHelpChannel(true, msg.getChannel(), msg.getMember());
            }
            else {
                embed.setAsError("Misspelled Command");
                embed.addMessage(":x: **[System] You Likely Misspelled the Name of the " +
                        "Command You Are Wanting to Lookup**");
                msg.getChannel().sendMessage(msg.getMember().getAsMention());
                msg.getChannel().sendMessage(embedBuilder.build()).queue();
            }
        }
        else if (args.length > 2) {
            embed.setAsError("Error While Fetching Help");
            embed.addMessage(":x: **[System] Too Many Arguements**");
            if (!isTeamMember) {
                sendToHelpChannel(true, msg.getChannel(), msg.getMember());
            }
            else {
                sendToTeamDiscussionChannel(true, msg.getMember());
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
            embed.setAsSuccess("Successful Reason Addition");
            embed.addMessage(core.addReason(false, args[2], reason));
            sendToLogChannel();
            log.info("Successful Reason Addition for \"" + reason + "\" mapped to the key \"" + args[2] + "\"");
        }
        // /rmgr addkeymap <newKey> <existingKey>
        else if (args[1].equalsIgnoreCase("addkeymap") && isStaffMember) {
            embed.setAsSuccess("Successful Key Mapping");
            embed.addMessage(core.addReason(true, args[2], args[3]));
            sendToLogChannel();
            log.info("Successful Key Map of \"" + args[2] + "\" to \"" + args[3] + "\"");
        }
        // /rmgr remove <existingKey>
        // /rmgr del <existingKey>
        // /rmgr delete <existingKey>
        else if (args[1].equalsIgnoreCase("remove") || args[1].contains("del") && isStaffMember) {
            String result = core.deleteReason(args[2]);
            if (result.contains(":white_check_mark:")) {
                embed.setAsSuccess("Successful Reason Deletion");
                embed.addMessage(result);
                sendToLogChannel();
            }
            else {
                embed.setAsError("Error - Reason Removal");
                embed.addMessage(result);
                sendToTeamDiscussionChannel(true, msg.getMember());
            }
        }
        else if (args[1].equalsIgnoreCase("list") && isTeamMember) {
            String result = "";
            Iterator<String> keys = core.reasonsDictionary.keys().asIterator();
            Iterator<String> elements = core.reasonsDictionary.elements().asIterator();
            do {
                result = result.concat("**Key: *" + keys.next()
                        + "* :arrow_right: Reason: *" + elements.next() + "* **\n");

            } while (keys.hasNext());
            embed.setAsInfo("Reasons Dictionary");
            embed.addMessage(result);
            sendToTeamDiscussionChannel(true, msg.getMember());
            log.info(msg.getMember().getEffectiveName() + " just requested the reasons dictionary list");
        }
        else {
            embed.setAsError("Error - No Permissions");
            embed.addMessage("**:x: [System] You Lack Permissions to do that!**");
            if (isTeamMember) {
                sendToTeamDiscussionChannel(false, null);
            }
            else {
                sendToHelpChannel(true, msg.getChannel(), msg.getMember());
            }
            log.error(msg.getMember().getEffectiveName()
                    + " tried to run the reason manager and did not have permission to");
        }
    }
    ///////////////////////////////////////////////////////////
    // Miscellaneous Methods
    ///////////////////////////////////////////////////////////
    private void lengthyHistory(String stringToSplit, Member member, boolean isTeamMember, @Nullable String timeZoneOffset) {
        String[] splitString = stringToSplit.split("\n\n");
        int index = 0;
        while (index < splitString.length) {
            embed.addMessage(splitString[index]);
            // Sometimes the addfield doesn't add the splitString correctly and there isn't a field,
            // so we restart the loop from the beginning if that happens.
            if (embedBuilder.getFields().isEmpty()) continue;
            if (!isTeamMember) {
                sendDM(member.getUser());
            }
            else {
                sendToTeamDiscussionChannel(false, null);
            }
            index++;
        }
        String logString = member.getEffectiveName() + " just checked their own Bot Abuse History";
        if (timeZoneOffset != null) {
            logString = logString.concat(" using TimeZone offset " + timeZoneOffset);
        }
        else {
            logString = logString.concat(" using the default TimeZone offset");
        }
        log.info(logString);
    }
    private boolean isCommand(String arg) {
        int index = 0;
        while (index < commands.size()) {
            if (arg.equalsIgnoreCase(commands.get(index))) {
                return true;
            }
            index++;
        }
        return false;
    }
    private void failedIntegrityCheck(Member author, String cause) throws IOException {
        embed.setAsStop("FATAL ERROR");
        embed.addMessage("**Ouch! That Really Didn't Go Well! **" +
                "\n**You may use */restart* to try to restart me. If you don't feel comfortable doing that... " + botConfig.owner.getAsMention()
                + " has been notified.**" +
                "\n\n**Cause: " + cause + "**" +
                "\n\n**Commands have Been Suspended**");
        sendToTeamDiscussionChannel(true, author);
        log.fatal("Integrity Check on ArrayList Objects Failed - Cause: " + cause);
        commandsSuspended = true;
    }
    ///////////////////////////////////////////////////////////
    // Output Handler Methods
    // The "Member author" objects in the ToHelpChannel and ToDiscussionChannel methods are annotated as Nullable
    // as in some cases in the code I either don't ping the original command user or
    // I cannot give these methods a Member object
    ///////////////////////////////////////////////////////////
    private void sendDM(User user) {
        user.openPrivateChannel().flatMap(channel -> channel.sendMessage(embedBuilder.build())).queue();
        messageSent();
    }
    private void sendToHelpChannel(boolean tagAuthor, MessageChannel msgChannel, @Nullable Member author) {
        if (msgChannel != botConfig.botSpamChannel) {
            if (tagAuthor) {
                botConfig.helpChannel.sendMessage(author.getAsMention()).queue();
            }
            botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
        }
        else {
            botConfig.botSpamChannel.sendMessage(embedBuilder.build()).queue();
        }
        messageSent();
    }
    private void sendToTeamDiscussionChannel(boolean tagAuthor, @Nullable Member author) {
        if (tagAuthor) {
            botConfig.discussionChannel.sendMessage(author.getAsMention()).queue();
        }
        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
        messageSent();
    }
    // Tagging author is not necessary in the log channel
    private void sendToLogChannel() {
        botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
        messageSent();
    }
    private void messageSent() {
        try {
            embedBuilder.clearFields();
            threadList.get(0).interrupt();
        }
        catch (IndexOutOfBoundsException e) {
            // Take No Action
        }
    }
    // This Method handles adding discord IDs to the cooldown arrays, since this code can be initiated two separate ways
    // best just to create a separate method for it.
    private void pingHandler(long targetDisordID) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MINUTE, botConfig.pingCoolDown);
        pingCooldownDiscordIDs.add(targetDisordID);
        pingCooldownOverTimes.add(c.getTime());
    }
}