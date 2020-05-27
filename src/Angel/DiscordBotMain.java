package Angel;

import com.google.gson.JsonObject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.*;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nonnull;
import java.awt.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class DiscordBotMain extends ListenerAdapter {
    private final Logger log = LogManager.getLogger(DiscordBotMain.class);
    private Guild guild;
    BotConfiguration botConfig;
    private Core core = new Core();
    private FileHandler fileHandler = core.fileHandler;
    // embed calls things from the EmbedDesigner class, which houses the urls for the thumbnails, colors,
    // and each method in this class needs a title as an arguement.
    // embedBuilder calls the builder directly, which calls for adding fields and clearing them do.
    private EmbedBuilder embedBuilder = new EmbedBuilder();
    private EmbedDesigner embed = new EmbedDesigner(embedBuilder) {};
    // We have separate instances of Embed Builder so that the timers that are running in the background
    // don't interfere with the main builder
    private EmbedBuilder timerEmbed = new EmbedBuilder();
    private EmbedBuilder timerEmbed2 = new EmbedBuilder();
    private Help help = new Help(embedBuilder, embed);
    private boolean timerRunning = false;
    private boolean commandsSuspended = false;
    private boolean isRestart;
    private boolean isReload = false;
    private List<String> commands = new ArrayList<>();
    private Timer timer;
    private Timer timer2;

    DiscordBotMain(boolean isRestart) throws IOException, TimeoutException {
        core.startup(false);
        botConfig = new BotConfiguration(fileHandler.getConfig()) {};
        botConfig.initialSetup();
        this.isRestart = isRestart;
        log.info("All Classes Constructed");
        commands.addAll(Arrays.asList("botAbuse", "permBotAbuse", "undo", "check", "checkHistory", "clear", "transfer"));
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        botConfig.guild = event.getJDA().getGuilds().get(0);
        this.guild = botConfig.guild;
        botConfig.finishSetup();
        init(event);
    }

    @Override
    public void onResume(@Nonnull ResumedEvent event) {
        init(event);
    }

    @Override
    public void onReconnect(@Nonnull ReconnectedEvent event) {
        init(event);
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
            event.getGuild().addRoleToMember(event.getMember().getIdLong(),
                   botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
            embed.setAsInfo("Join Event Information");
            embedBuilder.addField("System Message", "**[System - Join Event] Added the Bot Abuse Role to "
                    + event.getMember().getAsMention() +
                    " since according to the data file they should have the Bot Abuse role**", true);
            botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
            log.info("Join Event - Added Bot Abuse Role to " + event.getMember().getEffectiveName());
        }
        // If they're not supposed to be Bot Abused and they do have the role
        else if (!core.botAbuseIsCurrent(event.getMember().getIdLong()) &&
                event.getMember().getRoles().contains(botConfig.botAbuseRole)) {
            event.getGuild().removeRoleFromMember(event.getMember().getIdLong(),
                    botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
            embed.setAsInfo("Join Event Information");
            embedBuilder.addField("System Message", "**[System - Join Event] Removed the Bot Abuse Role from "
                    + event.getMember().getAsMention() +
                    " since according to the data file they shouldn't have it**", true);
            botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
            log.info("Join Event - Removed Bot Abuse Role from " + event.getMember().getEffectiveName());
        }
        embedBuilder.clearFields();
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
            if (args[0].equalsIgnoreCase("botabuse")) {
                if ((isTeamMember || author == botConfig.owner) &&
                        (args.length == 3 || args.length == 4)) {
                    setBotAbuse(msg);
                }
                else if ((args.length < 3 || args.length > 4) && (isTeamMember || author == botConfig.owner)) {
                    embed.setAsError("Error - Invalid Number of Arguements");
                    embedBuilder.addField("System Message",
                            "**[System] You Entered an Invalid Number of Arguments**", true);
                    botConfig.discussionChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                }
                else { // If they Don't have the Team role then it returns an error message
                    embed.setAsError("Error - No Permissions");
                    embedBuilder.addField("System Message", ":x: **[System] You Lack Permissions to do that!**", true);
                    botConfig.helpChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
            else if (args[0].equalsIgnoreCase("permbotabuse")) { // /permbotabuse <Mention or Discord ID> [Image]
                if ((isStaffMember || author == botConfig.owner) && (args.length == 2 || args.length == 3)) {
                    permBotAbuse(msg);
                }
                else if ((isStaffMember || author == botConfig.owner) && (args.length < 2 || args.length > 3)) {
                    embed.setAsError("Error - Invalid Number of Arguements");
                    embedBuilder.addField("System Message", ":x: **[System] You Entered an Invalid Number of Arguments**", true);
                    botConfig.discussionChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                }
                else {
                    embed.setAsError("Error - No Permissions");
                    embedBuilder.addField("System Message", ":x: **[System] You Lack Permissions to do that!**", true);
                    botConfig.helpChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
            else if (args[0].equalsIgnoreCase("undo")) {
                if (isTeamMember) {
                    undoCommand(msg);
                }
                else {
                    embed.setAsError("Error - No Permissions");
                    embedBuilder.addField("System Message", ":x: **[System] You Lack Permissions to do that!**", true);
                    botConfig.helpChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
            else if (args[0].equalsIgnoreCase("check")) {
                // This handles a /check for someone to check their own Bot Abuse status or someone else's.
                checkCommand(msg, isTeamMember);
            }
            else if (args[0].equalsIgnoreCase("transfer")) { // /transfer <Old Mention or Discord ID> <New Mention or Discord ID>
                if ((isStaffMember) || author == botConfig.owner) {
                    try {
                        transferRecords(msg);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    embed.setAsError("Error - No Permissions");
                    embedBuilder.addField("System Message", ":x: **[System] You Lack Permissions to do that!**", true);
                    botConfig.helpChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
            else if (args[0].equalsIgnoreCase("clear")) {
                if ((isStaffMember) || author == botConfig.owner) {
                    clearCommand(msg);
                }
                else {
                    embed.setAsError("Error - No Permissions");
                    embedBuilder.addField("System Message", ":x: "
                            + msg.getAuthor().getAsMention() + " [System] You Lack Permissions to do that!", true);
                    botConfig.helpChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
            else if (args[0].equalsIgnoreCase("checkhistory")) {
                try {
                    checkHistory(msg, isTeamMember);
                }
                catch (IllegalStateException ex) {
                    // Take No Action
                }
            }
        }
        else if (msg.getMentionedMembers().contains(msg.getGuild().getSelfMember())) {
            msg.getChannel().sendMessage(":blobnomping:").queue();
        }
        else if ((msg.getContentRaw().charAt(0) == '/' && args[0].equalsIgnoreCase("restart"))
                && ((isStaffMember) || author == botConfig.owner)) {
            msg.delete().complete();
            try {
                embed.setAsWarning("Restart Initiated");
                if (author != botConfig.owner) {
                    embedBuilder.addField("System Message", "**Restart Initiated by " + msg.getMember().getAsMention()
                            + "\nPlease Allow up to 10 seconds for this to complete**", true);
                    log.warn("Staff Invoked Restart");
                }
                else {
                    embedBuilder.addField("System Message", "**Restart Initiated by The Angel of Darkness"
                            + "\nPlease Allow up to 10 seconds for this to complete**", true);
                    log.warn("Owner Invoked Restart");
                }
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                Thread.sleep(5000);
                core.startup(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if ((msg.getContentRaw().charAt(0) == '/' && args[0].equalsIgnoreCase("reload"))
                && ((isStaffMember) || author == botConfig.owner)) {
            embed.setAsWarning("Reloading Configuration");
            embedBuilder.addField("System Message",
                    "**Reloading Configuration... Please Wait a Few Moments...**", true);
            botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            embedBuilder.clearFields();
            botConfig.reload();
            timer.cancel();
            timer.purge();
            timer2.cancel();
            timer2.purge();
            timerRunning = false;
            isReload = true;
            init(event);
        }
        else if (msg.getContentRaw().charAt(0) == '/' && args[0].equalsIgnoreCase("help")) {
            helpCommand(msg, isTeamMember);
        }
        else if (commandsSuspended) {
            embed.setAsStop("Commands Suspended");
            if (!isTeamMember) {
                embedBuilder.addField("System Message", "**Commands are Temporarily Suspended... Sorry for the inconvience...**", true);
                botConfig.helpChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
            }
            else {
                embedBuilder.addField("System Message", "**Commands are Temporarily Suspended... " +
                        "Please Post The Action you were trying to do in either a DM with" + botConfig.owner.getAsMention() + " or in this channel.**", true);
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
        }
        if ((args[0].equalsIgnoreCase("botabuse") || args[0].equalsIgnoreCase("permbotabuse"))
                && msg.getChannel() == botConfig.discussionChannel && msg.getAttachments().size() == 1) {
            msg.delete().completeAfter(10, TimeUnit.SECONDS);
        }
        else if (!msg.getMentionedMembers().contains(msg.getGuild().getSelfMember())) {
            msg.delete().complete();
        }
        embedBuilder.clear();
    }
    private void init(Event event) {
        // Here we're running an integrity check on the data that was loaded, if the data loaded is no good...
        // then we suspend all commands and we don't start the timers.
        if (!core.arraySizesEqual()) {
            commandsSuspended = true;;
            log.fatal("Data File Damaged on Initiation");
            log.warn("Commands are now Suspended");
        }
        else {
            commandsSuspended = false;
            log.info("TheLightAngel is Ready");
        }
        if (isRestart) {
            embed.setAsSuccess("Restart Complete");
            if (!commandsSuspended) {
                embedBuilder.addField("System Message", "**I'm Back Fellas! Restart is Complete!**", true);
            }
            else {
                embedBuilder.addField("System Message", "**Restart Complete but Data File Still Is Not Usable**", true);
            }
            botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            embedBuilder.clearFields();
        }
        if (isReload) {
            embed.setAsSuccess("Reload Complete");
            embedBuilder.addField("System Message",
                    "**Reloading Config was Successfully Completed!**",true);
            botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            embedBuilder.clearFields();
        }
        if ((!timerRunning && !commandsSuspended) || isReload) {
            if (!isRestart) {
                botConfig.discussionChannel.sendMessage(":wave: Hey Folks! I'm Ready To Fly!").queue();
            }
            log.info("Timer is Running");
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
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (removedID != 0) {
                        try {
                            // For Printing in the Console and in Discord A Bot Abuse role has been removed.
                            timerEmbed.setColor(Color.GREEN);
                            timerEmbed.setTitle("Successfully Removed Expired Bot Abuse");
                            timerEmbed.setThumbnail(embed.checkIcon);
                            timerEmbed.addField("System Message", "**:white_check_mark: [System] Removed Expired Bot Abuse for "
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
                            timerEmbed.addField("System Message",
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
                            timerEmbed.addField("System Message", "**Successfully Removed Expired Bot Abuse for "
                                    + removedID + " but they did not exist in the discord server!**", true);
                            botConfig.logChannel.sendMessage(timerEmbed.build());
                            log.warn("Successfully Removed Expired Bot Abuse for "
                                    + removedID + " but they did not exist in the discord server!");
                        }
                        timerEmbed.clearFields();
                    }
                }
            }, 0, 1000);
            // 15 Minute Periodic Scan of Players that should be Bot Abused to ensure that they have the role
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
                            timerEmbed2.addField("System Message", "[System - Role Scanner] Added Bot Abuse Role to "
                                    + guild.getMemberById(core.currentBotAbusers.get(index)).getAsMention() +
                                    " because they didn't have the role... and they're supposed to have it." , true);
                            botConfig.logChannel.sendMessage(timerEmbed.build()).queue();
                            log.info("ROLE SCANNER - Added Bot Abuse to " +
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
                            timerEmbed2.addField("System Message", "[System - Role Scanner] Removed Bot Abuse Role from "
                                    + serverMembers.get(index).getAsMention() + " because they had the role... " +
                                    "and they weren't supposed to have it." , true);
                            botConfig.logChannel.sendMessage(timerEmbed2.build()).queue();
                            log.info("[System - Role Scanner] Removed Bot Abuse Role from "
                                    + serverMembers.get(index).getEffectiveName() +
                                    " because they had the role... and they were not supposed to have it");
                            timerEmbed2.clearFields();
                        }
                        index++;
                    }

                }
            }, 0, botConfig.roleScannerInterval);
        }
        else if (commandsSuspended) {
            embed.setAsStop("Commands Suspended");
            embedBuilder.addField("System Message", ":x: **[System] The Data File has been Damaged" +
                    "\n\nCommands Have Been Suspended**", true);
            botConfig.discussionChannel.sendMessage(botConfig.owner.getAsMention()).queue();
            botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            embedBuilder.clearFields();
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
                        embed.setAsStop("FATAL ERROR");
                        embedBuilder.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                                " while I reload and then you can try to run that command again**", true);
                        botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                        failedIntegrityCheck();
                    }
                    else if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess("Successful Bot Abuse");
                        embedBuilder.addField("System Message", result, true);
                        botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                        msg.getGuild().addRoleToMember(msg.getGuild().getMemberById(Long.parseLong(args[1])),
                                botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                        embedBuilder.clearFields();
                        embedBuilder.addField("System Message", ":white_check_mark: " + " Successfully Bot Abused "
                                + msg.getGuild().getMemberById(Long.parseLong(args[1])).getAsMention(), true);
                        botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                        log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setAsError("Whoops... Something went wrong");
                        embedBuilder.addField("System Message", result, true);
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    }
                }
                else if (msg.getAttachments().size() == 1 && msg.getChannel() == botConfig.discussionChannel) {
                    String result = core.setBotAbuse(Long.parseLong(args[1]),
                            false, args[2], msg.getAttachments().get(0).getProxyUrl(), msg.getMember().getAsMention());
                    if (result.contains("FATAL ERROR")) {
                        embed.setAsStop("FATAL ERROR");
                        embedBuilder.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                                " while I reload and then you can try to run that command again**", true);
                        botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                        failedIntegrityCheck();
                    }
                    else if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess("Successful Bot Abuse");
                        embedBuilder.addField("System Message", result, true);
                        botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                        msg.getGuild().addRoleToMember(msg.getGuild().getMemberById(Long.parseLong(args[1])),
                                botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                        embedBuilder.clearFields();
                        embedBuilder.addField("System Message",":white_check_mark: " + " Successfully Bot Abused "
                                + msg.getGuild().getMemberById(Long.parseLong(args[1])).getAsMention(), true);
                        botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                        log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setAsError("Whoops... Something went wrong");
                        embedBuilder.addField("System Message", result, true);
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    }
                }
                else if (msg.getAttachments().size() == 1 && msg.getChannel() != botConfig.discussionChannel) {
                    embed.setAsError("Channel Error for This Action");
                    embedBuilder.addField("System Message",
                            "**That was the Wrong Channel to attach an image to this command. Please use this channel.**", true);
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
            catch (NumberFormatException ex) {
                embed.setAsError("Error While Setting Bot Abuse");
                embedBuilder.addField("System Message", ":x: [System] The Discord ID cannot contain any letters or special characters", true);
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            catch (NullPointerException ex) {
                embedBuilder.clearFields();
                embed.setAsWarning("Exception Caught but Successful Bot Abuse");
                embedBuilder.addField("System Message", "Caught a NullPointerException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Bot Abuse for that Discord ID to the Database**", true);
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused " + args[1]);
            }
            catch (IllegalArgumentException ex) {
                embedBuilder.clearFields();
                embed.setAsWarning("Exception Caught but Successful Bot Abuse");
                embedBuilder.addField("System Message", "Caught a IllegalArgumentException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Bot Abuse for that Discord ID to the Database**", true);
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
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
                    msg.getGuild().addRoleToMember(msg.getMentionedMembers().get(0),
                            botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                    if (result.contains("FATAL ERROR")) {
                        embed.setAsStop("FATAL ERROR");
                        embedBuilder.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                                " while I reload and then you can try to run that command again**", true);
                        botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                        failedIntegrityCheck();
                    }
                    else if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess("Successful Bot Abuse");
                        embedBuilder.addField("System Message", result, true);
                        botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                        embedBuilder.clearFields();
                        embedBuilder.addField("System Message", "**:white_check_mark: " +
                                " Successfully Bot Abused " + msg.getMentionedMembers().get(0).getAsMention() + "**", true);
                        botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                        log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setAsError("Whoops... Something went wrong");
                        embedBuilder.addField("System Message", result, true);
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    }
                }
                else if (msg.getAttachments().size() == 1 && msg.getChannel() == botConfig.discussionChannel) {
                    String result = core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                            false, args[2], msg.getAttachments().get(0).getProxyUrl(), msg.getMember().getAsMention());
                    msg.getGuild().addRoleToMember(msg.getMentionedMembers().get(0),
                            botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                    if (result.contains("FATAL ERROR")) {
                        embed.setAsStop("FATAL ERROR");
                        embedBuilder.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                                " while I reload and then you can try to run that command again**", true);
                        botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                        failedIntegrityCheck();
                    }
                    else if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess("Successful Bot Abuse");
                        embedBuilder.addField("System Message", result, true);
                        botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                        embedBuilder.clearFields();
                        embedBuilder.addField("System Message", "**:white_check_mark: " + msg.getAuthor().getAsMention()
                                + " Successfully Bot Abused " + msg.getMentionedMembers().get(0).getAsMention() + "**", true);
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                        log.info(msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setAsError("Whoops... Something went wrong");
                        embedBuilder.addField("System Message", result, true);
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    }
                }
                else if (msg.getAttachments().size() == 1 && msg.getChannel() != botConfig.discussionChannel) {
                    embed.setAsError("Channel Error for This Action");
                    embedBuilder.addField("System Message",
                            "**That was the Wrong Channel to attach an image to this command. Please use this channel.**", true);
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() > 1 ) {
            embed.setAsError("Target ID Error");
            embedBuilder.addField("System Message", ":x: [System] Too many Target IDs", true);
            botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
            botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
        }
    }
    private void permBotAbuse(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");

        // If length is 3, then an image url was provided.
        if (msg.getMentionedMembers().isEmpty() && args.length == 3) {
            try {
                embed.setAsSuccess("Successful Perm Bot Abuse");
                embedBuilder.addField("System Message", core.setBotAbuse(Long.parseLong(args[1]),
                        true, "staff", args[2] , msg.getMember().getAsMention()), true);
                botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                msg.getGuild().addRoleToMember(msg.getGuild().getMemberById(Long.parseLong(args[1])),
                        botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                embedBuilder.clearFields();
                embedBuilder.addField("System Message", msg.getAuthor().getAsMention() + " Permanently Bot Abused " +
                        msg.getGuild().getMemberById(Long.parseLong(args[1])).getAsMention(), true);
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                log.info("[Admin Override] " + msg.getMember().getEffectiveName() +
                        " Successfully Permanently Bot Abused " + msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                embed.setAsError("Error While Setting Perm Bot Abuse");
                embedBuilder.addField("System Message", "[System] Invalid User ID!", true);
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            catch (ArrayIndexOutOfBoundsException ex) {
                embed.setAsError("Error While Setting Perm Bot Abuse");
                embedBuilder.addField("System Message", "[System] Invalid Number of Arguments!", true);
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            catch (NullPointerException ex) {
                embedBuilder.clearFields();
                embed.setAsWarning("Exception Caught but Successful Perm Bot Abuse");
                embedBuilder.addField("System Message", "Caught a NullPointerException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Perm Bot Abuse for that Discord ID to the Database**", true);
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() == 1 && args.length == 3) {
            try {
                embed.setAsSuccess("Successful Perm Bot Abuse");
                embedBuilder.addField("System Message", core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                        true, "staff", args[2], msg.getMember().getAsMention()),true);
                botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                msg.getGuild().addRoleToMember(msg.getMentionedMembers().get(0),
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
                embedBuilder.addField("System Message",
                        core.setBotAbuse(Long.parseLong(args[1]), true, "staff", null, msg.getMember().getAsMention()), true);
                botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                msg.getGuild().addRoleToMember(msg.getGuild().getMemberById(Long.parseLong(args[1])),
                        botConfig.botAbuseRole).completeAfter(5, TimeUnit.MILLISECONDS);
                log.info("[Admin Override] " + msg.getMember().getEffectiveName()
                        + " Successfully Permanently Bot Abused " + msg.getGuild().getMemberById(args[1]).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                embed.setAsError("Error in Setting Perm Bot Abuse");
                embedBuilder.addField("System Message",  "**:x: [System] Invalid User ID!**", true);
                botConfig.discussionChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            catch (ArrayIndexOutOfBoundsException ex) {
                embed.setAsError("Error in Setting Perm Bot Abuse");
                embedBuilder.addField("System Message", "**:x: [System] Invalid Number of Arguements!**", true);
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            catch (NullPointerException ex) {
                embedBuilder.clearFields();
                embed.setAsWarning("Exception Caught but Successful Perm Bot Abuse");
                embedBuilder.addField("System Message", ":white_check_mark: " + msg.getMember().getEffectiveName()
                        + " Permanently Bot Abused " + args[1] + " who does not exist on the Discord Server", true);
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() == 1 && args.length == 2) {
            try {
                embed.setAsSuccess("Successful Perm Bot Abuse");
                embedBuilder.addField("System Message", core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                        true, "staff", null, msg.getMember().getAsMention()), true);
                botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                msg.getGuild().addRoleToMember(msg.getMentionedMembers().get(0),
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
            embedBuilder.addField("System Message", "**:x: [System] Too Many Mentioned Players!**", true);
            botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
            botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
        }
        embedBuilder.clearFields();
    }
    private void undoCommand(Message msg) {

        String[] args = msg.getContentRaw().substring(1).split(" ");
        embed.setAsSuccess("Successful Undo");
        try {
            if (args.length == 1) {
                msg.getGuild().removeRoleFromMember(msg.getGuild().getMemberById(core.discordID.get(core.issuingTeamMember.lastIndexOf(msg.getMember().getAsMention()))),
                        botConfig.botAbuseRole).complete();
                String result = core.undoBotAbuse(msg.getMember().getAsMention(), true, 0);
                if (result.contains("FATAL ERROR")) {
                    embed.setAsStop("FATAL ERROR");
                    embedBuilder.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                            " while I reload and then you can try to run that command again**", true);
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    failedIntegrityCheck();
                }
                else if (result.contains(":x:")) {
                    embed.setAsError("Error While Undoing");
                    embedBuilder.addField("System Message", result, true);
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                }
                else {
                    embedBuilder.addField("System Message", result, true);
                    log.info("[System] " + msg.getMember().getEffectiveName() + " just undid their last Bot Abuse");
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
            else if (args.length == 2 && msg.getMentionedMembers().isEmpty()) {
                msg.getGuild().removeRoleFromMember(Long.parseLong(args[1]),
                        botConfig.botAbuseRole).complete();
                String result = core.undoBotAbuse(msg.getMember().getAsMention(), false, Long.parseLong(args[1]));
                if (result.contains("FATAL ERROR")) {
                    embed.setAsStop("FATAL ERROR");
                    embedBuilder.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                            " while I reload and then you can try to run that command again**", true);
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    failedIntegrityCheck();
                }
                else if (result.contains(":x:")) {
                    embed.setAsError("Error While Undoing");
                    embedBuilder.addField("System Message", result, true);
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                }
                else {
                    embedBuilder.addField("System Message", result, true);
                    log.info("[System] " + msg.getMember().getEffectiveName() + " just undid the Bot Abuse for "
                    + msg.getGuild().getMemberById(args[1]).getEffectiveName());
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
            else if (args.length == 2 && msg.getMentionedMembers().size() == 1) {
                msg.getGuild().removeRoleFromMember(msg.getMentionedMembers().get(0),
                        botConfig.botAbuseRole).complete();
                String result = core.undoBotAbuse(msg.getMember().getAsMention(), false, msg.getMentionedMembers().get(0).getIdLong());
                if (result.contains("FATAL ERROR")) {
                    embed.setAsStop("FATAL ERROR");
                    embedBuilder.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                            " while I reload and then you can try to run that command again**", true);
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    failedIntegrityCheck();
                }
                if (result.contains(":x:")) {
                    embed.setAsError("Error While Undoing");
                    embedBuilder.addField("System Message", result, true);
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                }
                else {
                    embedBuilder.addField("System Message", result, true);
                    log.info("[System] " + msg.getMember().getEffectiveName() + " just undid the Bot Abuse for "
                            + msg.getMentionedMembers().get(0).getEffectiveName());
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
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
            String result = core.getInfo(msg.getAuthor().getIdLong(), 100, false);
            if (result.contains(":white_check_mark:")) {
                embed.setAsSuccess("You Are Not Bot Abused");
            }
            embedBuilder.addField("System Message", result , true);
            botConfig.helpChannel.sendMessage("Hey " + msg.getAuthor().getAsMention() + ",").queue();
            botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
            log.info(msg.getMember().getEffectiveName() + " just checked their own Bot Abuse status");
        }
        // This handles if the player opts for a Direct Message instead "/check dm"
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2 && args[1].equalsIgnoreCase("dm")) {
            String result = core.getInfo(msg.getAuthor().getIdLong(), 100, false);
            if (result.contains(":white_check_mark:")) {
                embed.setAsSuccess("You Are Not Bot Abused");
            }
            embedBuilder.addField("System Message", result, true);
            sendDM(msg.getAuthor(), embedBuilder.build());
            log.info(msg.getMember().getEffectiveName() + " just checked their own Bot Abuse status and opted for a DM");
        }
        // /check <Discord ID>
        else if (isTeamMember && args.length == 2 && msg.getMentionedMembers().isEmpty()) {
            try {
                String result = core.getInfo(Long.parseLong(args[1]), 100 ,true);
                if (result.contains(":white_check_mark:")) {
                    embed.setAsError("Player Not Bot Abused");
                    embedBuilder.addField("System Message", ":x: **This Player is Not Bot Abused**", true);
                }
                else {
                    embedBuilder.addField("System Message", result, true);
                }
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                log.info(msg.getMember().getEffectiveName() + " just checked on "+
                        msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName() + "'s Bot Abuse Status");
            }
            catch (NumberFormatException ex) {
                embed.setAsError("Check Info Error");
                embedBuilder.addField("System Message",":x: **Invalid Discord ID**", true);
                botConfig.discussionChannel.sendMessage(msg.getAuthor().getAsMention());
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                log.info("Team Member " + msg.getMember().getEffectiveName() + " just entered an invalid Discord ID");
            }
        }
        // /check <Mention>
        else if ((msg.getMentionedMembers().size() == 1 && isTeamMember) && args.length == 2) {
            String result = core.getInfo(msg.getMentionedMembers().get(0).getIdLong(), 100 ,true);
            if (result.contains(":white_check_mark:")) {
                embed.setAsError("Player Not Bot Abused");
                embedBuilder.addField("System Message", ":x: **This Player is Not Bot Abused**", true);
                log.error(msg.getMember().getEffectiveName() + " just checked on " +
                        msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status but they were not Bot Abused");
            }
            else {
                embedBuilder.addField("System Message", result, true);
                log.info(msg.getMember().getEffectiveName() + " just checked on "+
                        msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status");
            }
            botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            log.info(msg.getMember().getEffectiveName() + " just checked on "+
                    msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status");

        }
        // /check <Timezone Offset>
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2) {
            if (core.checkOffset(args[1])) {
                String result = core.getInfo(msg.getAuthor().getIdLong(), Float.parseFloat(args[1]), false);
                if (result.contains(":white_check_mark:")) {
                    embed.setAsError("You Are Not Bot Abused");
                }
                embedBuilder.addField("System Message", result, true);
                botConfig.helpChannel.sendMessage("Hey " + msg.getAuthor().getAsMention() + ",").queue();
                botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
                log.info(msg.getMember().getEffectiveName() +
                        " just checked on their own Bot Abuse status using TimeZone offset " + args[1]);
            }
            else {
                embed.setAsError("Check Info Error");
                embedBuilder.addField("System Message",":x: **Invalid Timezone Offset**", true);
                botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
                log.error(msg.getMember().getEffectiveName() + " just entered an invalid TimeZone offset into /check");
            }
        }
        // /check [dm] <Timezone Offset>
        else if (msg.getMentionedMembers().isEmpty() && args.length == 3 && args[1].equalsIgnoreCase("dm")) {
            if (core.checkOffset(args[2])) {
                String result = core.getInfo(msg.getAuthor().getIdLong(), Float.parseFloat(args[2]), false);
                if (result.contains(":white_check_mark:")) {
                    embed.setAsSuccess("You Are Not Bot Abused");
                }
                embedBuilder.addField("System Message", result, true);
                sendDM(msg.getAuthor(), embedBuilder.build());
                log.info(msg.getMember().getEffectiveName() +
                        " just checked on their own Bot Abuse status while opting for a DM");
            }
            else {
                embed.setAsError("TimeZone Offset Error");
                embedBuilder.addField("System Message", ":x: " + msg.getAuthor().getAsMention() + " **Invalid Timezone Offset**", true);
                botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
                log.error(msg.getMember().getEffectiveName() +
                        " just entered an invalid Discord ID while opting for a DM");
            }
        }
        // /check <Timezone Offset> <Mention or Discord ID>
        else if ((isTeamMember) && args.length == 3) {
            if (core.checkOffset(args[1])) {
                if (msg.getMentionedMembers().isEmpty()) {
                    String result =  core.getInfo(Long.parseLong(args[2]), Float.parseFloat(args[1]), true);
                    if (result.contains(":white_check_mark:")) {
                        embed.setAsError("Player Not Bot Abused");
                        embedBuilder.addField("System Message", ":x: **This Player is Not Bot Abused**", true);
                        log.error(msg.getMember().getEffectiveName() + " just checked on " +
                                msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status but they were not Bot Abused");
                    }
                    else {
                        embedBuilder.addField("System Message", result, true);
                    }
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    log.info(msg.getMember().getEffectiveName() +
                            " just checked on " + msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName()
                            + "'s Bot Abuse status using TimeZone offset " + args[1]);
                }
                else if (msg.getMentionedMembers().size() == 1) {
                    String result = core.getInfo(msg.getMentionedMembers().get(0).getIdLong(), Float.parseFloat(args[1]), true);
                    if (result.contains(":white_check_mark:")) {
                        embed.setAsError("Player Not Bot Abused");
                        embedBuilder.addField("System Message", ":x: **This Player is Not Bot Abused**", true);
                    }
                    else {
                        embedBuilder.addField("System Message", result, true);
                    }
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    log.info(msg.getMember().getEffectiveName() +
                            " just checked on " + msg.getMentionedMembers().get(0).getEffectiveName()
                            + "'s Bot Abuse status using TimeZone offset " + args[1]);
                }
            }
            else {
                embed.setAsError("Check Info Error");
                embedBuilder.addField("System Message", ":x: **[System] Invalid Timezone Offset**", true);
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                log.error("Team Member " + msg.getMember().getEffectiveName() + " just entered an invalid TimeZone offset.");
            }

        }
        else {
            embed.setAsError("Permission Error");
            embedBuilder.addField("System Message","You Don't have Permission to check on someone else's Bot Abuse status", true);
            botConfig.helpChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
            botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
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
                msg.getGuild().removeRoleFromMember(msg.getMentionedMembers().get(index).getIdLong(),
                        botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                embed.setAsInfo("Bot Abuse Role Removed");
                embedBuilder.addField("System Message", "**Successfully Removed Bot Abuse Role from "
                        + msg.getMentionedMembers().get(index).getAsMention() + " as their Records just got Cleared**", true);
                botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                embedBuilder.clearFields();
                log.info("Successfully Removed Bot Abuse Role from " +
                         msg.getMentionedMembers().get(index).getEffectiveName() + " as their Records just got Cleared");
            }
            embed.setAsSuccess("Successfully Cleared Records");
            try {
                int clearedRecords = core.clearRecords(msg.getMentionedMembers().get(index).getIdLong());
                if (clearedRecords == -1) {
                    embed.setAsStop("FATAL ERROR");
                    embedBuilder.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                            " while I reload and then you can try to run that command again**", true);
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    failedIntegrityCheck();
                    break;
                }
                else if (clearedRecords == 0) {
                    embed.setAsError("No Records Cleared");
                    embedBuilder.addField("System Message","** [System] No Records Found for " + msg.getMentionedMembers().get(0).getEffectiveName() + "**", true);
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    log.error("No Records Cleared for " + msg.getMentionedMembers().get(index).getEffectiveName());
                }
                else {
                    embedBuilder.addField("System Message", ":white_check_mark: **[System] Successfully Cleared " +
                            clearedRecords + " Records from " + msg.getMentionedMembers().get(index).getAsMention() + "**", true);
                    botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                    log.info("Successfully Cleared " + clearedRecords + " Records from " +
                            msg.getMentionedMembers().get(index).getEffectiveName());
                }
                index++;
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
            embedBuilder.clearFields();
        }
        index = 0;
        // We check for any plain discord IDs with this, we don't take any action on a NumberFormatException as that would indicate
        // a mention in that argument, which was already handled, so they're ignored
        while (index < args.length) {
            embed.setAsSuccess("Successfully Cleared Records");
            try {
                if (msg.getGuild().getMemberById(Long.parseLong(args[index])).getRoles().contains(botConfig.botAbuseRole)) {
                    msg.getGuild().removeRoleFromMember(Long.parseLong(args[index]),
                            botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                    embed.setAsInfo("Bot Abuse Role Removed");
                    embedBuilder.addField("System Message", "**Successfully Removed Bot Abuse Role from "
                            + msg.getGuild().getMemberById(Long.parseLong(args[index])).getEffectiveName() +
                            " as their Records just got Cleared**", true);
                    log.info("Successfully Removed Bot Abuse Role from " +
                            msg.getGuild().getMemberById(Long.parseLong(args[index])).getEffectiveName() +
                            " as their Records just got Cleared");
                }
                int clearedRecords = core.clearRecords(Long.parseLong(args[index]));
                if (clearedRecords == -1) {
                    embed.setAsStop("FATAL ERROR");
                    embedBuilder.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                            " while I reload and then you can try to run that command again**", true);
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    failedIntegrityCheck();
                    break;
                }
                if (clearedRecords == 0) {
                    embed.setAsError("No Records Cleared");
                    embedBuilder.addField("System Message", "**[System] No Records Found for " + args[index] + "**", true);
                    botConfig.discussionChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    log.error("No Records Cleared for " + args[index]);
                }
                else {
                    embed.setAsSuccess("Successfully Cleared Records");
                    botConfig.logChannel.sendMessage(":white_check_mark: **[System] Successfully Cleared " +
                            clearedRecords + " Records from " +
                            msg.getGuild().getMemberById(Long.parseLong(args[index])).getAsMention()).queue();
                    log.info("Successfully Cleared " + clearedRecords + " Records from " +
                            msg.getGuild().getMemberById(Long.parseLong(args[index])).getEffectiveName());
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
                        embed.setAsStop("FATAL ERROR");
                        embedBuilder.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                                " while I reload and then you can try to run that command again**", true);
                        botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                        failedIntegrityCheck();
                        break;
                    }
                    if (clearedRecords == 0) {
                        embed.setAsError("Error in Clearing Records");
                        botConfig.discussionChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                        log.error("No Records Cleared for " + args[index]);
                    }
                    else {
                        embedBuilder.addField("System Message", ":white_check_mark: **[System] Successfully Cleared " +
                                clearedRecords + " Records from " + args[index] + "**", true);
                        botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                        log.info("Successfully Cleared " + clearedRecords + " Records from " + args[index]);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
            embedBuilder.clearFields();
            index++;
        }
    }
    private void transferRecords(Message msg) throws IOException {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        embed.setAsSuccess("Successful Transfer of Records");

        if (args.length == 3) {
            if (msg.getMentionedMembers().size() == 2) {
                if (core.botAbuseIsCurrent(msg.getMentionedMembers().get(0).getIdLong())) {
                    msg.getGuild().addRoleToMember(msg.getMentionedMembers().get(1).getIdLong(),
                            botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                    msg.getGuild().removeRoleFromMember(msg.getMentionedMembers().get(0).getIdLong(),
                            botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                }
                log.info(msg.getMember().getEffectiveName() + " Successfully Transferred the Records of "
                        + msg.getMentionedMembers().get(0).getEffectiveName() + " to " + msg.getMentionedMembers().get(1).getEffectiveName());
                embedBuilder.addField("System Message",
                        core.transferRecords(msg.getMentionedMembers().get(0).getIdLong(), msg.getMentionedMembers().get(1).getIdLong()), true);
                botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
            }
            else if (msg.getMentionedMembers().size() == 1) {
                try {
                    // If they provide a Discord ID First and a Mention Last
                    if (core.botAbuseIsCurrent(Long.parseLong(args[1]))) {
                        msg.getGuild().addRoleToMember(msg.getMentionedMembers().get(0).getIdLong(),
                                botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                        try {
                            msg.getGuild().removeRoleFromMember(Long.parseLong(args[1]),
                                    botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                        }
                        catch (ErrorResponseException ex) {
                            embed.setAsWarning("Exception Caught - Player Does Not Exist");
                            embedBuilder.addField("System Message", "**[System] Could Not Remove the Bot Abuse Role from "
                                    + args[1] + " because they do not exist in the Discord Server**", true );
                            botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                            embedBuilder.clearFields();
                        }
                    }
                    embed.setAsSuccess("Successful Transfer of Records");
                    embedBuilder.addField("System Message",
                            core.transferRecords(Long.parseLong(args[1]), msg.getMentionedMembers().get(0).getIdLong()), true);
                    botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                    try {
                        log.info(msg.getMember().getEffectiveName() + " Successfully Transferred the Records of "
                                + msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName()
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
                            msg.getGuild().addRoleToMember(Long.parseLong(args[2]),
                                    botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                        }
                        catch (ErrorResponseException e) {
                            embed.setAsWarning("Exception Caught - Player Does Not Exist");
                            embedBuilder.addField("System Message", "**[System] Could Not Add the Bot Abuse Role to "
                                    + args[2] + " because they do not exist in the Discord Server**", true);
                            botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                            embedBuilder.clearFields();
                        }
                        msg.getGuild().removeRoleFromMember(msg.getMentionedMembers().get(0).getIdLong(),
                                botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                    }
                    embed.setAsSuccess("Successful Transfer of Records");
                    embedBuilder.addField("System Message",
                            core.transferRecords(msg.getMentionedMembers().get(0).getIdLong(), Long.parseLong(args[2])), true);
                    botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                    try {
                        log.info(msg.getMember().getEffectiveName() + " Successfully Transferred the Records of "
                                + msg.getMentionedMembers().get(0).getEffectiveName() + " to " +
                                msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName());
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
                        msg.getGuild().addRoleToMember(Long.parseLong(args[2]),
                                botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                    }
                    catch (ErrorResponseException ex) {
                        embed.setAsWarning("Exception Caught - Player Does Not Exist");
                        embedBuilder.addField("System Message", "**[System] Could Not Add the Bot Abuse Role to "
                                + args[2] + " because they do not exist in the Discord Server**", true);
                        botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                        log.warn("Could Not Add the Bot Abuse Role to " +
                                args[2] + " because they do not exist in the Discord Server");
                    }
                    try {
                        msg.getGuild().removeRoleFromMember(Long.parseLong(args[1]),
                                botConfig.botAbuseRole).completeAfter(50, TimeUnit.MILLISECONDS);
                    }
                    catch (ErrorResponseException ex) {
                        embed.setAsWarning("Exception Caught - Player Does Not Exist");
                        embedBuilder.addField("System Message", "**[System] Could Not Remove the Bot Abuse Role from "
                                + args[1] + " because they do not exist in the Discord Server**", true);
                        botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                        log.warn("Could Not Remove the Bot Abuse Role from " + args[1] +
                                " because they do not exist in the Discord Server");
                    }
                }
                embed.setAsSuccess("Successful Transfer of Records");
                botConfig.logChannel.sendMessage(core.transferRecords(Long.parseLong(args[1]), Long.parseLong(args[2]))).queue();
                try {
                    log.info(msg.getMember().getEffectiveName() + " Successfully Transferred the Records of "
                            + msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName() + " to " +
                            msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName());
                }
                catch (NullPointerException ex) {
                    log.info(msg.getMember().getEffectiveName() + " Successfully Transferred the Records of " +
                            args[1] + " to " + args[2]);
                }

            }
            else {
                embed.setAsError("Error while Parsing Transfer Command");
                embedBuilder.addField("System Message",
                        "[System] Invalid Number of Mentions!" +
                                "\nUsage: /transfer <Old Mention or Discord ID> <New Mention or Discord ID>", true);
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            embedBuilder.clearFields();
        }
        else {
            embed.setAsError("Error while Parsing Transfer Command");
            embedBuilder.addField("System Message",
                    "[System] Invalid Number of Arguments!" +
                            "\nUsage: /transfer <Old Mention or Discord ID> <New Mention or Discord ID>", true);
            msg.getChannel().sendMessage(embedBuilder.build()).queue();
        }
    }
    private void checkHistory(Message msg, boolean isTeamMember) throws IllegalStateException {
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
                embedBuilder.addField("System Message", result, true);
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                embedBuilder.clearFields();
                embedBuilder.addField("System Message", ":information_source: **[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                        msg.getGuild().getMemberById(Long.parseLong(args[1])).getAsMention() + "**", true);
                botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                log.info(author.getEffectiveName() + " just checked the history of " +
                        msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                try {
                    // The code above would throw a NumberFormatException if it's a mention
                    String result = core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), 100, true);
                    if (result.contains(":x:")) {
                        embed.setAsError("Bot Abuse History Information");
                    }
                    embedBuilder.addField("System Message", result, true);
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                    embedBuilder.clearFields();
                    embedBuilder.addField("System Message", ":information_source: **[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                            msg.getMentionedMembers().get(0).getAsMention() + "**", true);
                    botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                    log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                            msg.getMentionedMembers().get(0).getEffectiveName());
                }
                // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
                catch (IllegalArgumentException e) {
                    this.lengthyHistory(
                            core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), 100, true), msg.getAuthor(), isTeamMember);
                }
            }
            // The Try code would throw a NullPointerException if the Discord ID Provided does not exist on the server.
            catch (NullPointerException f) {
                embed.setAsWarning("Bot Abuse History - Player Does Not Exist in the Server");
                embedBuilder.addField("System Message","**[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                        args[1] + " who currently does not exist within the Discord Server**", true);
                botConfig.logChannel.sendMessage(embedBuilder.build()).queue();
                log.info(msg.getMember().getEffectiveName() + " just checked the history of "  +
                        args[1] + " who currently does not exist within the Discord Server");
            }
            // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
            catch (IllegalArgumentException h) {
                this.lengthyHistory(core.seeHistory(Long.parseLong(args[1]), 100,true), msg.getAuthor(), isTeamMember);
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
                String result = core.seeHistory(msg.getAuthor().getIdLong(), 100,false);
                if (result.contains(":white_check_mark:")) {
                    embed.setAsSuccess("Your Bot Abuse History");
                }
                embedBuilder.addField("System Message", result, true);
                sendDM(msg.getAuthor(), embedBuilder.build());
                log.info(msg.getMember().getEffectiveName() + " just checked their own Bot Abuse History");
            }
            // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
            catch (IllegalArgumentException ex) {
                this.lengthyHistory(core.seeHistory(msg.getAuthor().getIdLong(), 100,false), msg.getAuthor(), isTeamMember);
            }
        }
        // /checkhistory <timeOffset>
        else if (args.length == 2) {
            try {
                if (core.checkOffset(args[1])) {
                    String result = core.seeHistory(msg.getAuthor().getIdLong(), Float.parseFloat(args[1]), false);
                    embed.setAsInfo("Your Bot Abuse History");
                    if (result.contains(":white_check_mark:")) {
                        embed.setAsSuccess("Your Bot Abuse History");
                    }
                    embedBuilder.addField("System Message", result, true);
                    sendDM(msg.getAuthor(), embedBuilder.build());
                    log.info(msg.getMember().getEffectiveName() + " just checked their own Bot Abuse History" +
                            " using TimeZone offset " + args[1]);
                }
                else {
                    embed.setAsError("Error while Parsing Command");
                    embedBuilder.addField("System Message", ":x: **Invalid Timezone Offset**", true);
                    botConfig.helpChannel.sendMessage(msg.getMember().getAsMention());
                    botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
            catch (IllegalArgumentException ex) {
                this.lengthyHistory(core.seeHistory(msg.getAuthor().getIdLong(), Float.parseFloat(args[1]), false), msg.getAuthor(), isTeamMember);
            }
        }

        // /checkhistory <timeOffset> <Mention or Discord ID>
        else if (args.length == 3 && isTeamMember) {
            embed.setAsInfo("Bot Abuse History");
            if (core.checkOffset(args[1])) {
                try {
                    if (msg.getMentionedMembers().size() == 1) {
                        String result = core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), Float.parseFloat(args[1]), true);
                        if (result.contains(":x:")) {
                            embed.setAsError("Bot Abuse History");
                        }
                        embedBuilder.addField("System Message", result, true);
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                        log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getMentionedMembers().get(0).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                    else if (msg.getMentionedMembers().isEmpty()) {
                        String result = core.seeHistory(Long.parseLong(args[2]), Float.parseFloat(args[1]), true);
                        if (result.contains(":x:")) {
                            embed.setAsError("Bot Abuse History");
                        }
                        embedBuilder.addField("System Message", result, true);
                        botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                        log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                }
                catch (IllegalArgumentException ex) {
                    try {
                        this.lengthyHistory(core.seeHistory(Long.parseLong(args[2]), Float.parseFloat(args[1]), true), msg.getAuthor(), isTeamMember);
                        log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                    catch (NumberFormatException e) {
                        this.lengthyHistory(core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), Float.parseFloat(args[1]), true), msg.getAuthor(), isTeamMember);
                        log.info(msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getMentionedMembers().get(0).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                }
            }
        }
        // No Permissions to check on someone elses Bot Abuse history
        else if (args.length > 1 && !isTeamMember) {
            embed.setAsError("Error - No Permissions");
            embedBuilder.addField("System Message", ":x: " + msg.getAuthor().getAsMention() +
                    " **[System] You Don't Have Permission to check on someone elses Bot Abuse History**", true);
            botConfig.helpChannel.sendMessage(msg.getMember().getAsMention()).queue();
            botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
            log.warn(msg.getMember().getEffectiveName() +
                    " just tried to check someone elses Bot Abuse History but they did not have permission to");
        }
        else {
            embed.setAsStop("FATAL ERROR");
            embedBuilder.addField("System Message", ":x: **[System] Something went Seriously wrong when that happened**", true);
            msg.getChannel().sendMessage(embedBuilder.build()).queue();
        }
    }
    private void helpCommand(Message msg, boolean isTeamMember) {

        String[] args = msg.getContentRaw().substring(1).split(" ");
        if (args.length == 1) {
            embed.setAsInfo("About /help");
            embedBuilder.addField("System Message", "Syntax: `/help <Command Name>`", true);
            if (isTeamMember) {
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            else {
                botConfig.helpChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
            }
        }
        else if (args.length == 2) {
            if (args[1].equalsIgnoreCase("botAbuse") && isTeamMember) {
                help.botAbuseCommand();
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            else if (args[1].equalsIgnoreCase("permBotAbuse") && isTeamMember) {
                help.permBotAbuseCommand();
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            else if (args[1].equalsIgnoreCase("undo") && isTeamMember) {
                help.undoCommand();
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            else if (args[1].equalsIgnoreCase("transfer") && isTeamMember) {
                help.transferCommand();
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            else if (args[1].equalsIgnoreCase("clear") && isTeamMember) {
                help.clearCommand();
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            else if (args[1].equalsIgnoreCase("check")) {
                help.checkCommand(isTeamMember, msg.getGuild(), botConfig.helpChannel);
                if (!isTeamMember) {
                    botConfig.helpChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
                }
                else {
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
            else if (args[1].equalsIgnoreCase("checkHistory")) {
                help.checkHistoryCommand();
                if (!isTeamMember) {
                    botConfig.helpChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
                }
                else {
                    botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
                }
            }
            else if (isCommand(args[1]) && !isTeamMember) {
                embed.setAsError("No Permissions to Get This Help");
                embedBuilder.addField("System Message", ":x: **[System] You Do Not Have Permissions to See This Help**", true);
                botConfig.helpChannel.sendMessage(msg.getMember().getAsMention());
                botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
            }
            else {
                embed.setAsError("Misspelled Command");
                embedBuilder.addField("System Message", ":x: **[System] You Likely Misspelled the Name of the " +
                        "Command You Are Wanting to Lookup**", true);
                msg.getChannel().sendMessage(msg.getMember().getAsMention());
                msg.getChannel().sendMessage(embedBuilder.build()).queue();
            }
        }
        else if (args.length > 2) {
            embed.setAsError("Error While Fetching Help");
            embedBuilder.addField("System Message", ":x: **[System] Too Many Arguements**", true);
            if (!isTeamMember) {
                botConfig.helpChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.helpChannel.sendMessage(embedBuilder.build()).queue();
            }
            else {
                botConfig.discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
        }
    }
    ///////////////////////////////////////////////////////////
    // Miscellaneous Methods
    ///////////////////////////////////////////////////////////
    private void lengthyHistory(String stringToSplit, User user, boolean isTeamMember) {
        String[] splitString = stringToSplit.split("\n\n");
        int index = 0;
        while (index < splitString.length) {
            embedBuilder.addField("System Message", splitString[index], true);
            if (!isTeamMember) {
                sendDM(user, embedBuilder.build());
            }
            else {
                botConfig.discussionChannel.sendMessage(embedBuilder.build()).queue();
            }
            embedBuilder.clearFields();
            index++;
        }
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
    private void failedIntegrityCheck() throws IOException, TimeoutException {
        log.fatal("Integiry Check on ArrayList Objects Failed - Attempting Restart to Prevent Damage");
        core.startup(true);
    }
    private void sendDM(User user, MessageEmbed msg) {
        user.openPrivateChannel().flatMap(channel -> channel.sendMessage(msg)).queue();
    }
}
abstract class BotConfiguration {
    JsonObject configObj;
    Guild guild;
    Member owner;
    String ownerDiscordID;
    String token;
    String adminRoleID;
    String staffRoleID;
    String teamRoleID;
    String botAbuseRoleID;
    String teamChannelID;
    String helpChannelID;
    String logChannelID;
    MessageChannel discussionChannel;
    MessageChannel helpChannel;
    MessageChannel logChannel;
    Role adminRole;
    Role staffRole;
    Role teamRole;
    Role botAbuseRole;
    long roleScannerInterval;

    BotConfiguration(JsonObject importConfigObj) {
        configObj = importConfigObj;
        token = configObj.get("token").getAsString();
    }
    // Initial setup contains all of the configuration fields that need to be read.
    // Token is one of them except it cannot be among the configurations to be reloaded, which is why the token is in
    // the constructor
    void initialSetup() {
        ownerDiscordID = configObj.get("ownerDiscordID").getAsString();
        adminRoleID = configObj.get("adminRoleID").getAsString();
        staffRoleID = configObj.get("staffRoleID").getAsString();
        teamRoleID = configObj.get("teamRoleID").getAsString();
        botAbuseRoleID = configObj.get("botAbuseRoleID").getAsString();
        teamChannelID = configObj.get("teamDiscussionChannel").getAsString();
        helpChannelID = configObj.get("helpChannel").getAsString();
        logChannelID = configObj.get("logChannel").getAsString();
        roleScannerInterval = configObj.get("roleScannerInterval").getAsLong();
    }
    void finishSetup() {
        owner = guild.getMemberById(ownerDiscordID);
        discussionChannel = guild.getTextChannelById(teamChannelID);
        helpChannel = guild.getTextChannelById(helpChannelID);
        logChannel = guild.getTextChannelById(logChannelID);

        adminRole = guild.getRoleById(adminRoleID);
        staffRole = guild.getRoleById(staffRoleID);
        teamRole = guild.getRoleById(teamRoleID);
        botAbuseRole = guild.getRoleById(botAbuseRoleID);
    }
    void reload() {
        initialSetup();
        finishSetup();
    }
}