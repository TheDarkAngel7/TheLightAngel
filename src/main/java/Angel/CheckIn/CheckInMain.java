package Angel.CheckIn;

import Angel.CheckIn.AFKTool.AFKCheckManagement;
import Angel.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.DisconnectEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ReconnectedEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public class CheckInMain extends ListenerAdapter {
    private final Logger log = LogManager.getLogger(CheckInMain.class);
    private final DiscordBotMain discord;
    private FileHandler fileHandler;
    private final Guild guild;
    private final MainConfiguration mainConfig;
    private final EmbedEngine embed;
    private CheckInCore ciCore;
    private CheckInConfiguration ciConfig;
    private CheckInTimer ciTimer;
    // Embed in the session channel while the check-in is in progress
    private MessageEntry checkInSessionChannelEntry = null;
    private Message checkInSessionChannelEmbed = null;
    // Embed in one of our channels while the check-in is in progress
    private Message checkInProgressionEmbed;
    private MessageEntry checkInProgressionEntry;
    // Embed that confirms the !ci start command was used
    private Message checkinStartConfirmationEmbed;
    private MessageEntry checkinStartConfirmationEntry;
    // Messages to Purge when check-in is confirmed
    private List<Message> toPurge = new ArrayList<>();
    // Messages to tell EmbedEngine to purge when the check-in is confirmed
    private List<Message> toSearchPurge = new ArrayList<>();
    // Containers for the check-in startup messages, not the running.
    private List<Message> checkInStartupMessages = new ArrayList<>();
    private List<MessageEntry> checkInStartupEntryObjects = new ArrayList<>();
    // Session Channel in Question
    private TextChannel sessionChannel;
    private boolean commandsSuspended = false;
    private boolean isConnected = false;
    private boolean checkInRunning = false;
    private boolean checkInConfirmed = false;
    private boolean isBusy = false;
    private final List<String> commands = Arrays.asList("checkin", "ci", "reprint", "afk", "afkcheck");
    private final List<String> firstArg = Arrays.asList("add", "start", "confirm", "cancel", "refresh", "remove", "delete", "del", "resultlist", "rlist", "list", "help");
    private final List<String> emojiList;
    private CheckInQueueEmbed checkInQueueEmbed;
    private AFKCheckManagement afkCheck;

    CheckInMain(DiscordBotMain discord, Guild guild, MainConfiguration mainConfig, EmbedEngine embed) {
        this.discord = discord;
        this.guild = guild;
        this.mainConfig = mainConfig;
        this.embed = embed;
        String makeKeyCap = "\uFE0F\u20E3";
        emojiList = Arrays.asList("\u0031" + makeKeyCap, "\u0032" + makeKeyCap, "\u0033" + makeKeyCap,
                "\u0034" + makeKeyCap, "\u0035" + makeKeyCap, "\u0036" + makeKeyCap, "\u0037" + makeKeyCap, "\u0038" + makeKeyCap,
                "\u0039" + makeKeyCap, "\uD83D\uDD1F");
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        try {
            isConnected = true;
            fileHandler = new FileHandler();
            ciConfig = new ModifyCheckInConfiguration(guild, fileHandler.getConfig());
            ciCore = new CheckInCore(ciConfig, guild);
            ciTimer = new CheckInTimer(this, ciConfig);
            afkCheck = new AFKCheckManagement(guild, event.getJDA(), discord, embed, mainConfig);
            afkCheck.startTimer();
            fileHandler.setCiCore(ciCore);
            try {
                fileHandler.getDatabase();
            }
            catch (IllegalStateException ex) {
                log.warn("No Data Existed in the Check-In Database - No Data File");
            }
            ciConfig.setup();

            switch (ciConfig.setupRoles()) {
                case 0: log.info("Check-In Roles Configured Successfully");
                        break;
                case 1: log.warn("One or More of the role IDs that can be checked-in in the config file were not found");
                        break;
                case 2: commandsSuspended = true;
                        log.fatal("The configured roles that can be checked-in are not usable");
                        break;
            }
        }
        catch (IOException ex) {
            log.error("IOException Caught while initalizing Check-In Config and Core: \n" + ex.getMessage());
        }
    }

    @Override
    public void onReconnected(@NotNull ReconnectedEvent event) {
        isConnected = true;
    }

    @Override
    public void onDisconnect(@NotNull DisconnectEvent event) {
        isConnected = false;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        isBusy = true;
        Message msg = event.getMessage();
        String[] args;
        try {
            args = msg.getContentRaw().substring(1).split(" ");
        }
        catch (StringIndexOutOfBoundsException ex) {
            // Take No Action - This exception is already handed by DiscordBotMain
            return;
        }

        if (!ciConfig.isEnabled() && isCommand(args[0], args[1])) {
            embed.setAsError("Check-In Feature Disabled", ":x: **You used a command for a section of the bot that is currently disabled***");
            embed.sendToChannel(msg, msg.getChannel());
        }

        else if (msg.getContentRaw().charAt(0) == mainConfig.commandPrefix && !commandsSuspended) {
            switch (args[0].toLowerCase()) {
                case "ci":
                case "checkin": checkInCommand(msg); break;
                case "afk":
                case "afkcheck":
                    if (args.length == 1 || (args.length == 2 && args[1].equalsIgnoreCase("list"))) {
                        afkCheck.displayAFKCheckList(msg);
                    }
                    else if (args.length == 2 && args[1].equalsIgnoreCase("status")) {
                        afkCheck.displayStatus(msg);
                    }
                    else {
                        afkCheck.startNewAfkCheck(msg, 15, 5);
                    }
                    break;
                case "reprint":
                    if (checkInConfirmed && msg.getTextChannel() == sessionChannel) {
                        checkInSessionChannelEmbed.delete().queue();
                        sessionChannel.sendMessageEmbeds(checkInSessionChannelEntry.getEmbed()).queue();
                    }
                    break;
            }
        }
        isBusy = false;
    }

    //////////////////////////////////////////////////
    // /checkin start [TextChannel Mention or Session Name]
    // /checkin start (In the Session Channel)
    //////////////////////////////////////////////////
    private void checkInCommand(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");

        if (args.length == 1) {
            checkInPlayer(msg);
        }
        else if (args.length >= 2) {
            switch (args[1].toLowerCase()) {
                case "start":
                    startCheckInCmd(msg);
                    break;
                case "add":
                    addMemberToCheckIn(msg);
                    break;
                case "remove":
                case "delete":
                case "del":
                    removeMemberFromCheckIn(msg);
                    break;
                case "confirm":
                    confirmCheckIn(msg);
                    break;
                case "cancel":
                    cancelCheckIn(msg);
                    break;
                case "resultlist":
                case "rlist":
                case "list":
                    listCheckIns(msg);
                    break;
                case "refresh":
                    refreshCheckIn(msg);
                    break;
                case "help":
                    helpCommand(msg);
                    break;
            }
        }
    }
    private void helpCommand(Message msg) {
        List<String> controlHelps = Arrays.asList(
                "Use `" + mainConfig.commandPrefix + "checkin start [Session Channel Mention]` to initiate the session reads and " +
                        "the initialization entire process. The [Session Channel Mention] argument is not required when the command " +
                        "is used in a session channel.",
                "Use `" + mainConfig.commandPrefix + "checkin confirm` " +
                        "to confirm the check-in as configured",
                "Use `" + mainConfig.commandPrefix + "checkin add <Player Mention(s)>` " +
                "to add additional players that should be checked in. " +
                "You'll want to add the players that you believe their names were misread and got sent to *Members Not Found*. " +
                "You may add multiple players here.",
                "Use `" + mainConfig.commandPrefix + "checkin remove/del <id>` " +
                        "to remove players that were accidently added to *Members To Be Checked-In* due to a zoobot misread. " +
                        "You may remove multiple players here.",
                "To get player mentions for adding to the checkin queue you can use `" + mainConfig.commandPrefix +
                        "search <query>` (`" + mainConfig.commandPrefix + "s <query>` for short) whereas `<query>` " +
                        "you'd place part of or all of a player's name. " +
                        "The bot will then search every member of this server, in their nickname if they have one and their usernames."
                );
        discord.addAsReactionListEmbed(new ListEmbed(new MessageEntry("Check-In Pre-Start Controls", EmbedDesign.HELP, mainConfig, msg, TargetChannelSet.TEAM),
                "Here is one of the controls. Remember that `" + mainConfig.commandPrefix + "ci` is a alias to `"
                        + mainConfig.commandPrefix + "checkin`.",
                controlHelps, "As you use commands I'll be reacting to them with :white_check_mark: or :x: " +
                "to indicate if it was successful or a failure. The messages above will also edit themselves to reflect the change." +
                "\n\nWhen you're finished, use `"+ mainConfig.commandPrefix + "checkin confirm` \n" +
                "when you believe everyone that's in the session is underneath the *Members To Be Checked-In* section."));

    }
    private void checkInPlayer(Message msg) {
        AtomicReference<Member> member = new AtomicReference<>();
        guild.retrieveMember(msg.getAuthor()).queue(member::set);

        if (discord.isTeamMember(msg.getAuthor().getIdLong())) {
            embed.setAsError("Exempt From Check-In", ":x: **You Are Exempt From Needing to Check-In**");
            // Reserved For Team Member Needing Help with Check-In
        }
        else if (ciCore.checkInMember(msg.getAuthor(), ciTimer.getRemainingTime())) {
            embed.setAsSuccess("Successfully Checked-In", "You successfully checked in with " +
                    ciTimer.getRemainingTime() + " remaining on the clock." +
                    "\nCheck-In ID: " + ciCore.getCurrentCheckInID() +
                    "\n\n*Please keep this message handy in case you are accidently mistaken to not have checked-in.*");
            if (mainConfig.testModeEnabled) {
                log.warn("Would Have Removed the Role " + ciConfig.getCheckInRole().getName() + " from " + member.get().getEffectiveName() +
                        " (Discord ID: " + member.get().getIdLong() + ") because they have checked-in successfully, but couldn't because I'm in test mode...");
            }
            else {
                guild.removeRoleFromMember(member.get(), ciConfig.getCheckInRole()).queue();
                log.info("Successfully Removed the Role " + ciConfig.getCheckInRole().getName() + " from " + member.get().getEffectiveName() +
                        " (Discord ID: " + member.get().getIdLong() + ") because they have checked-in successfully.");
            }
        }
        else {
            embed.setAsError("No Check-In Needed",
                    "**There's either no check-in active or you're not being asked to check-in at this time**");
        }
        embed.sendDM(msg, msg.getAuthor());

        if (ciCore.everyoneIsCheckedIn()) {
            log.info("Everyone has successfully checked-in! Ending...");
            endCheckIn();
        }
    }
    private void startCheckInCmd(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        String sessionName = "";
        if (discord.isTeamMember(msg.getAuthor().getIdLong())) {
            // /checkin start [sessionChannel]
            if (checkInRunning) {
                embed.setAsError("Check-In Already Running",
                        "**:x: You Cannot Start Another Check-In While One is Already Running!**");
            }

            else if (args.length == 2 || args.length == 3) {

                switch (args.length) {
                    // Using "/checkin start" in a session channel
                    case 2:
                        sessionName = msg.getChannel().getName().split("_")[0];
                        sessionChannel = msg.getTextChannel();
                        break;
                    // /checkin start <Session Channel Mention Inserted>
                    case 3:
                        sessionName = msg.getMentions().getChannels(TextChannel.class).get(0).getName().split("_")[0];
                        sessionChannel = msg.getMentions().getChannels(TextChannel.class).get(0);
                        break;
                }
                if (msg.getMentions().getChannels(TextChannel.class).size() >= 2) {
                    embed.setAsError("Too Many Mentioned Channels",
                            ":x: **You gave me too many channel mentions, I was only expecting 1**");
                }
                else if (ciCore.isValidSessionName(sessionName)) {
                    checkInRunning = true;
                    ciCore.loadSessionLists(sessionName, false);

                    if (ciCore.getCheckInList().isEmpty()) {
                        embed.setAsError("Session Empty",
                                ":x: The session you have selected is currently empty... there's nobody to check-in");
                    }
                    else {
                        checkinStartConfirmationEntry = new MessageEntry("Successful Check-In Startup",
                                "**Successfully started a check-in for " + sessionChannel.getAsMention() + "!**" +
                                        "\n\n**Please Wait While I go fetch the names from zoobot and match those names up to discord accounts...**",
                                EmbedDesign.WARNING, mainConfig);
                        getCheckInProgressionEmbedChannel().sendMessageEmbeds(checkinStartConfirmationEntry.getEmbed(false)).queue(m -> {
                            checkinStartConfirmationEmbed = m;
                        });
                        sendCheckInStartupPrompts(msg, false);
                        return;
                    }
                }
                else {
                    embed.setAsError("Not a Valid Session Channel",
                            ":x: **The channel you used this command in is not valid session channel**");
                }
            }
            else {
                embed.setAsError("Too Many Arguments", ":x: **You gave me too many arguments**");
            }

            embed.sendToTeamOutput(msg, msg.getAuthor());

        }
        else {
            embed.setAsError("No Permissions",
                    ":x: **You Do Not Have Permissions to Initiate a Check-In**");
            embed.sendToMemberOutput(msg, msg.getAuthor());
        }
    }
    private void cancelCheckIn(Message msg) {
        if (discord.isTeamMember(msg.getAuthor().getIdLong())) {
            ciCore.endCheckIn(true);
            if (checkInRunning) {
                if (checkInConfirmed) {
                    ciTimer.stopTimer();
                    checkInRunning = false;
                    checkInConfirmed = false;
                    embed.setAsSuccess("Cancelled Check-In", "The Check-In that was running was successfully " +
                            "cancelled." +
                            "\nRemaining Time: **" + ciTimer.getRemainingTime() + "**");
                    log.info("The running Check-In was successfully cancelled with " + ciTimer.getRemainingTime() + " left on the clock");

                    checkInSessionChannelEmbed.editMessageEmbeds(
                            checkInSessionChannelEntry.setMessage("**The Check-In that was running for this session has been cancelled**" +
                                            "\n\nYou may resume normal chatter and bot commands.\n")
                                    .setDesign(EmbedDesign.SUCCESS).getEmbed(false)).queue();
                    checkInProgressionEmbed.editMessageEmbeds(
                            checkInProgressionEntry.setTitle("Check-In Successfully Cancellled").setMessage(":white_check_mark: **The Check-In was successfully cancelled**").getEmbed()).queue();
                }
                else {
                    purgeAllCommands();
                    checkInRunning = false;
                    checkInStartupMessages.clear();
                    checkInStartupEntryObjects.clear();
                    embed.setAsSuccess("Cancelled Check-In", "The Check-In Feature was successfully taken out " +
                            "of initialization and all pre-start data dumped.");
                    log.info("The initialized check-in was successfully cancelled");
                }
            }
            else {
                embed.setAsError("Unable to Cancel Check-In", ":x: **No Check-In Running, so there is nothing to cancel**");
                log.error("Unable to cancel check in as there was not one running");
            }
            embed.sendToTeamOutput(msg, msg.getAuthor());
        }
    }

    private void confirmCheckIn(Message msg) {
        if (discord.isTeamMember(msg.getAuthor().getIdLong())) {
            if (!checkInRunning) {
                embed.setAsError("Check-In Not Started",
                        ":x: **You Cannot Do That... you have to start the check-in first**");
                embed.sendToTeamOutput(msg, msg.getAuthor());
            }
            else {
                toPurge.add(msg);
                checkInConfirmed = true;
                purgeAllCommands();
                embed.setAsWarning("Check-In Starting", "**Check-In Confirmed! " +
                        "Please wait while all the players to be checked-in are assigned the "
                        + ciConfig.getCheckInRole().getAsMention() + " role...**" +
                        "\n\n*This may take up to a minute to complete... Please be Patient...*");
                embed.sendToTeamOutput(msg, msg.getAuthor());

                checkInStartupEntryObjects.clear();
                checkInStartupMessages.clear();

                ciCore.getCheckInList().forEach(m -> {
                    AtomicReference<Member> member = new AtomicReference<>();
                    guild.retrieveMemberById(m.getPlayerDiscordId()).queue(member::set);
                    if (!mainConfig.testModeEnabled && m.isQueuedToCheckIn()) {

                        guild.addRoleToMember(User.fromId(m.getPlayerDiscordId()), ciConfig.getCheckInRole()).submit().whenComplete(new BiConsumer<Void, Throwable>() {
                            @Override
                            public void accept(Void unused, Throwable throwable) {
                                if (throwable != null) {
                                    log.error("Could Not Complete Check-In Role Addition For " + member.get().getEffectiveName() +
                                            " (Discord ID: " + m.getPlayerDiscordId() + ")");
                                    ciCore.removeMemberFromCheckIn(member.get());
                                    embed.setAsError("Could Not Add Check-In Role", ":x: Unable to add check-in role for "
                                            + member.get().getAsMention() + ", so I automatically excused them from the check-in");
                                    embed.sendToLogChannel();
                                }
                                else {
                                    log.info("Successfully Added Role " + ciConfig.getCheckInRole().getName() + " to " +
                                            member.get().getEffectiveName() + " (Discord ID: " + m.getPlayerDiscordId() + ")");
                                }
                            }
                        });
                    }
                    else if (mainConfig.testModeEnabled && m.isQueuedToCheckIn()) {
                        log.warn("Would Have Successfully Added Role " + ciConfig.getCheckInRole().getName() + " to " +
                                member.get().getEffectiveName() + " (Discord ID: " + m.getPlayerDiscordId() + ") but could not because I'm in test mode...");
                    }
                });
                sendSessionChannelMessage(false);

                ciTimer.startTimer();
                embed.editEmbed(msg, "Check-In Running", "**Check-In has Successfully Started for " +
                        sessionChannel.getAsMention() + "**" +
                        "\n\n**The "+ ciConfig.getCheckInRole().getAsMention() +
                        " role may need a little additional time to get applied**", EmbedDesign.SUCCESS);
                sendCheckInProgressEmbed(msg, false);
            }
        }
    }

    private void refreshCheckIn(Message msg) {
        if (discord.isTeamMember(msg.getAuthor().getIdLong())) {
            if (checkInRunning && !checkInConfirmed) {
                purgeAllCommands();
                checkInStartupMessages.clear();
                checkInStartupEntryObjects.clear();
                ciCore.loadSessionLists(null, true);
                sendCheckInStartupPrompts(msg, false);
            }
            else {
                embed.setAsError("Unable To Refresh", "Uhhh... I cannot process that command because I need to " +
                        "be put into the first stages of setting up a check-in... use `"  + mainConfig.commandPrefix + "checkin start` " +
                        "in a session channel or you can use `" + mainConfig.commandPrefix + "checkin help` for more help on check-in commands");
                embed.sendToTeamOutput(msg, msg.getAuthor());
            }
        }
    }

    private void listCheckIns(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        String result = "";
        int index = 0;

        String prefix = "";
        String suffix = null;

        List<String> pages = new ArrayList<>();

        // This is for listing all the players involved in a particular check-in
        // ci list [ID]
        try {
            CheckInResult resultList = null;
            if (args.length == 2) {
                resultList = ciCore.getLatestResult();
            }
            else if (args.length == 3) {
                resultList = ciCore.getResultByID(Integer.parseInt(args[2]));
            }

            prefix = "Check-In ID: **" + resultList.getId() + "**\n" +
                    "Date Ended: **" + discord.getDiscordFormat(resultList.getEndDate()) + "**\n\n" +
                    ":white_check_mark: *Indicates The Player Did Check-In along with how much time was remaining when they did.*\n" +
                    ":warning: *Indicates The Player Failed to Check-In*\n" +
                    ":x: *Indicates The Player Was Removed From the Check-In Queue* \n";

            List<CheckInPlayer> players = resultList.getPlayers();
            AtomicReference<Member> member = new AtomicReference<>();

            if (resultList.isCancelled()) suffix = "\n\n :x: **This Check-In was Cancelled before it was completed!** :x:";

            do {
                CheckInPlayer p = players.get(index);
                guild.retrieveMemberById(p.getPlayerDiscordId()).queue(member::set);
                if (p.successfullyCheckedIn()) {
                    result = result.concat(":white_check_mark: **" + member.get().getEffectiveName()  +
                            " @ " + p.getCheckInRemainingTime() + "**\n");
                }
                else {
                    if (!p.isQueuedToCheckIn()) {
                        result = result.concat(":x: ~~" + member.get().getEffectiveName() + "~~\n");
                    }
                    else {
                        result = result.concat(":warning: **" + member.get().getEffectiveName() + "**\n");
                    }
                }
                // Going Twice on the Enter Key at the end of each record, except the last record
                if (index > players.size() - 1) result = result.concat("\n\n");

                // Placing the result string into the page and then clearing the result string
                if ((index + 1) % 10 == 0) {
                    pages.add(result);
                    result = "";
                }
            } while (++index < players.size());

            if (!result.equals("")) pages.add(result);
            discord.addAsReactionListEmbed(new ListEmbed(new MessageEntry("Check-In Result", EmbedDesign.INFO, mainConfig, msg, TargetChannelSet.TEAM),
                    prefix, pages, suffix).invertButtonLabels(true));
        }
        catch (NumberFormatException ex) {
            embed.setAsError("Invalid Result ID", ":x: **Invalid Result ID** \n\nYou can find the result IDs with ");
            embed.sendToTeamOutput(msg, msg.getAuthor());
        }
    }

    void sendSessionChannelMessage(boolean update) {
        String checkInWarningString = ":warning: **A Check-In has started for this session!**" +
                "\n\n*Please reply in ? with `" + mainConfig.commandPrefix + "checkin` to confirm you are paying attention to discord.*" +
                "\nYou'll receive confirmation you have checked-in via direct message" +
                "\n\nTime Remaining: **" + ciTimer.getRemainingTime() + "**" +
                "\n\n:warning: ***Do Not Bump This Message** except with kickvotes or other emergencies*";

        if (ciConfig.checkInChannelID.equalsIgnoreCase("None") || ciConfig.getCheckInChannel() == null) {
            checkInWarningString = checkInWarningString.replace("?", "this channel");
        }
        else {
            checkInWarningString = checkInWarningString.replace("?", ciConfig.getCheckInChannel().getAsMention());
        }

        if (update) {
            checkInSessionChannelEmbed.editMessageEmbeds(checkInSessionChannelEntry.setMessage(checkInWarningString)
                    .getEmbed(false)).queue();
        }
        else {
            checkInSessionChannelEntry = new MessageEntry(
                    "Check-In In Progress", checkInWarningString, EmbedDesign.WARNING, mainConfig);
            sessionChannel.sendMessageEmbeds(checkInSessionChannelEntry.getEmbed(false)).queue(m -> {
                checkInSessionChannelEmbed = m;
            });
        }
    }

    // /checkin add <Mention(s)>
    private void addMemberToCheckIn(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        if (discord.isTeamMember(msg.getAuthor().getIdLong())) {
            if (!checkInRunning) {
                embed.setAsError("Check-In Not Started",
                        ":x: **You Cannot Do That... you have to start the check-in first**");
                embed.sendToTeamOutput(msg, msg.getAuthor());
            }
            // /checkin add <Member Mention(s)>
            else if (!msg.getMentions().getMembers().isEmpty()) {
                ciCore.addMemberToCheckIn(msg.getMentions().getMembers());
                addCheckMarkReactionToMessage(msg);
            }
            else {
                addXReactionToMessage(msg);
            }
            sendCheckInStartupPrompts(msg, true);
        }
        else {
            embed.setAsError("No Permissions",
                    ":x: **You Do Not Have Permissions to Add Members to a Check-In**");
            embed.sendToMemberOutput(msg, msg.getAuthor());
        }
    }
    private void removeMemberFromCheckIn(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        if (discord.isTeamMember(msg.getAuthor().getIdLong())) {
            if (!checkInRunning) {
                embed.setAsError("Check-In Not Started",
                        ":x: **You Cannot Do That... you have to start the check-in first**");
                embed.sendToTeamOutput(msg, msg.getAuthor());
            }
            else if (msg.getMentions().getMembers().size() == 1) {
                if (ciCore.removeMemberFromCheckIn(msg.getMentions().getMembers().get(0))) {
                    addCheckMarkReactionToMessage(msg);
                }
                else {
                    addXReactionToMessage(msg);
                }
            }
            else if (msg.getMentions().getMembers().size() >= 2) {
                if (ciCore.removeMemberFromCheckIn(msg.getMentions().getMembers())) {
                    addCheckMarkReactionToMessage(msg);
                }
                else {
                    addXReactionToMessage(msg);
                }
            }
            else {
                try {
                    if (ciCore.removeMemberFromCheckIn(Integer.parseInt(args[2]))) {
                        addCheckMarkReactionToMessage(msg);
                    }
                    else {
                        addXReactionToMessage(msg);
                    }
                }
                catch (NumberFormatException ex) {
                    addXReactionToMessage(msg);
                }
            }
        }
        else {
            embed.setAsError("No Permissions",
                    ":x: **You Do Not Have Permissions to Remove Members from a Check-In**");
            embed.sendToMemberOutput(msg, msg.getAuthor());
        }
    }
    void fromReactionEmoji(Message msg, int numSelected) {
        ciCore.toggleInQueueFromReaction(numSelected);
        sendCheckInStartupPrompts(msg, true);
    }
    void endCheckIn() {
        checkInRunning = false;
        checkInConfirmed = false;
        CheckInResult ciResult = ciCore.endCheckIn(false);
        AtomicReference<Member> member = new AtomicReference<>();

        ciResult.getPlayers().forEach(p -> {
            guild.retrieveMemberById(p.getPlayerDiscordId()).queue(member::set);
            if (p.isQueuedToCheckIn() && !p.successfullyCheckedIn()) {
                if (mainConfig.testModeEnabled) {
                    log.warn("Would Have Removed the Role " + ciConfig.getCheckInRole().getName() + " from " + member.get().getEffectiveName() +
                            " (Discord ID: " + p.getPlayerDiscordId() + ") because the check-in ended, but couldn't because I'm in test mode...");
                }
                else {
                    guild.removeRoleFromMember(member.get(), ciConfig.getCheckInRole()).submit().whenComplete(new BiConsumer<Void, Throwable>() {
                        @Override
                        public void accept(Void unused, Throwable throwable) {
                            if (throwable != null) {
                                log.info("Successfully Removed the Role " + ciConfig.getCheckInRole().getName() + " from " + member.get().getEffectiveName() +
                                        " (Discord ID: " + p.getPlayerDiscordId() + ") because the check-in ended.");
                            }
                            else {
                                log.warn("Could Not Remove the Role " + ciConfig.getCheckInRole().getName() + " from " + member.get().getEffectiveName() +
                                        " (Discord ID: " + p.getPlayerDiscordId() + ")");
                            }
                        }
                    });
                }
            }
        });

        checkInProgressionEntry.setTitle("Check-In Ended").setDesign(EmbedDesign.SUCCESS).setMessage("**The Check-In Has Ended**" +
                "\n\nTo See the results for this check in you can use `" + mainConfig.commandPrefix + "ci list`" +
                "\nIf another check-in is run after this one the command is then `" + mainConfig.commandPrefix + "ci list " + ciResult.getId() + "`");
        checkInProgressionEmbed.editMessageEmbeds(checkInProgressionEntry.getEmbed(false)).queue();

        checkInSessionChannelEntry.setTitle("Check-In Ended").setDesign(EmbedDesign.SUCCESS).setMessage("**The Check-In Has Ended for This Session**\n\nYou may resume normal chatter and bot commands.");
        checkInSessionChannelEmbed.editMessageEmbeds(checkInSessionChannelEntry.getEmbed(false)).queue();
        try {
            fileHandler.saveDatabase();
        }
        catch (IOException e) {
            e.printStackTrace();
        }


        int checkedIn = 0;
        int failedCheckIn = 0;
        int removedFromCheckIn = 0;
        int index = 0;
        List<CheckInPlayer> players = ciResult.getPlayers();

        while (index < players.size()) {
            CheckInPlayer p = players.get(index++);

            if (p.isQueuedToCheckIn() && p.successfullyCheckedIn()) {
                checkedIn++;
            }
            else if (p.isQueuedToCheckIn() && !p.successfullyCheckedIn()) {
                failedCheckIn++;
            }
            else {
                removedFromCheckIn++;
            }
        }


        String result = "This Check-In is now completed from the session channel\n" +
                sessionChannel.getAsMention() +
                "\n\n:white_check_mark: Successfully Checked-In: **" + checkedIn + "**" +
                "\n:warning: Failed To Check-In: **" + failedCheckIn + "**" +
                "\n:x: Removed From Check-In Queue: **" + removedFromCheckIn + "**" +
                "\n\n**To See these results in more detail in the future, you may run `" +
                mainConfig.commandPrefix + "checkin list " + ciResult.getId() + "`**";

        embed.setAsInfo("Check-In " + ciResult.getId() + " Ended", result);
        embed.sendToLogChannel();
    }
    private void sendCheckInStartupPrompts(Message originalCmd, boolean update) {
        String memberListPrefix = "These members were successfully queried from the discord server:" +
                "\n:white_check_mark: = **Queued to be Checked-In**" +
                "\n:x: = **Not Queued to be Checked-In**";
        String duplicateMatches = "These are the queries I performed on this server, and when I tried to search this player it returned" +
                " multiple accounts with any of these roles:\n# \n\n";
        EmbedDesign duplicateMatchesStatus = EmbedDesign.ERROR;
        String unrecogizedPlayers = "These players I did not find a player a discord account for." +
                "\nThis could be due to a slight misread, or it could be due to a rando in the session. " +
                "\n*Please review this list to see if there's any members I might've missed*\n\n";

        int index = 0;
        int emojiIndex = 0;
        List<String> memberList = new ArrayList<>();
        // Setup Check-In List
        List<CheckInPlayer> playerList = ciCore.getCheckInList();
        AtomicReference<Member> member = new AtomicReference<>();
        String tempString = "";
        do {
            guild.retrieveMemberById(playerList.get(index).getPlayerDiscordId()).queue(member::set);
            if (playerList.get(index).isQueuedToCheckIn()) {
                tempString = tempString.concat(emojiList.get(emojiIndex++) + " :white_check_mark: **" + member.get().getEffectiveName() + "**\n");
            }
            else {
                tempString = tempString.concat(emojiList.get(emojiIndex++) + " :x: ~~" + member.get().getEffectiveName() + "~~\n");
            }

            if (++index % 10 == 0) {
                memberList.add(tempString);
                tempString = "";
                emojiIndex = 0;
            }
        } while (index < playerList.size());
        // This ensures the remainder of the tempString is into the memberList
        if (tempString != "") memberList.add(tempString);

        if (!update) {
            checkInQueueEmbed = new CheckInQueueEmbed(new MessageEntry("Members To Be Checked-In",
                    EmbedDesign.SUCCESS, mainConfig, originalCmd, ciConfig.getCheckInChannel()).setAsIsListEmbed(),
                    memberListPrefix, memberList, null, emojiList, this);

            discord.addAsReactionListEmbed(checkInQueueEmbed);
            toPurge.add(embed.getMessageEntryObj(originalCmd).getResultEmbed());

            checkinStartConfirmationEmbed.editMessageEmbeds(checkinStartConfirmationEntry.setDesign(EmbedDesign.SUCCESS).setMessage(
                    "**Successfully started a check-in for " + sessionChannel.getAsMention() + "!**").getEmbed(false)).queue();
        }
        else {
            discord.updateReactionListEmbed(checkInQueueEmbed, memberList);
        }

        // Construct Duplicate Matches Error Message if there are any
        Enumeration<String> keys = ciCore.getDuplicateMatchHashTable().keys();
        Enumeration<List<Member>> members = ciCore.getDuplicateMatchHashTable().elements();
        boolean thereIsDuplicates = false;
        if (!members.hasMoreElements()) {
            duplicateMatches = "**No Duplicate Members Found**";
            duplicateMatchesStatus = EmbedDesign.SUCCESS;

        }
        else {
            thereIsDuplicates = true;
            index = 0;
            String roleList = "";
            while (index < ciConfig.getRolesThatCanBeCheckedIn().size()) {
                roleList = roleList.concat(ciConfig.getRolesThatCanBeCheckedIn().get(index++).getAsMention() + ", ");
            }
            duplicateMatches = duplicateMatches.replace("#", roleList);

            while (members.hasMoreElements()) {
                index = 0;
                duplicateMatches.concat(
                        "Query: **\"" + keys.nextElement() + "\"**" +
                                "\nResults:");
                List<Member> mList = members.nextElement();
                while (index < mList.size()) {
                    Member m = mList.get(index++);
                    duplicateMatches.concat("\n**-" + m.getAsMention() + "**");
                }
                if (members.hasMoreElements()) duplicateMatches.concat("\n");
            }
        }
        EmbedDesign unrecognizedPlayerStatus = EmbedDesign.WARNING;
        // Construct Strings that were not recognized as a player - any if there are any
        if (!ciCore.getUnrecognizedPlayerList().isEmpty()) {
            index = 0;
            while (index < ciCore.getUnrecognizedPlayerList().size()) {
                unrecogizedPlayers = unrecogizedPlayers.concat("**- " + ciCore.getUnrecognizedPlayerList().get(index++) + "**\n");
            }
        }
        else {
            unrecognizedPlayerStatus = EmbedDesign.SUCCESS;
            unrecogizedPlayers = ":white_check_mark: **No Unrecognized Players Found.** " +
                    "However, *that does not mean there is not errors in the players found*, " +
                    "it is advisible to check zoobot's screenshot and compare that to the member list.";
        }
        if (update) checkInStartupEntryObjects.clear();
        if (thereIsDuplicates) checkInStartupEntryObjects.add(new MessageEntry("Duplicate Accounts", duplicateMatches, duplicateMatchesStatus, mainConfig));
        checkInStartupEntryObjects.add(new MessageEntry("Members Not Found", unrecogizedPlayers, unrecognizedPlayerStatus, mainConfig));

        printStartupEmbeds(update);
    }
    void sendCheckInProgressEmbed(@Nullable Message msg,  boolean update) {
        List<CheckInPlayer> players = ciCore.getCheckInList();
        int index = 0;
        int count = 0;
        do {
            if (!players.get(index).successfullyCheckedIn() && players.get(index).isQueuedToCheckIn()) {
                count++;
            }
        } while (++index < players.size());

        String prefix = "**A Check-In has started for " + sessionChannel.getAsMention() + "**" +
                "\nTime Remaining: **" + ciTimer.getRemainingTime() + "**" +
                "\nPlayers Remaining: **" + count + "/" + players.size() + "**" +
                "\n\n*Players List*:" +
                "\n:white_check_mark: **Indicates the player has checked in**" +
                "\n:warning: **Indicates the player has not check-in yet...**" +
                "\n:x: **Indicates the player was removed from the check-in queue prior to confirmation**" +
                "\n\n";

        String progressString = "";
        index = 0;
        List<CheckInPlayer> checkInList = ciCore.getCheckInList();
        AtomicReference<Member> member = new AtomicReference<>();
        do {
            guild.retrieveMemberById(checkInList.get(index).getPlayerDiscordId()).queue(member::set);
            if (checkInList.get(index).successfullyCheckedIn() && checkInList.get(index).isQueuedToCheckIn()) {
                progressString = progressString.concat(":white_check_mark: ~~" + member.get().getEffectiveName() +
                        "~~ @ **" + checkInList.get(index).getCheckInRemainingTime() + "**\n");
            }
            else if (checkInList.get(index).isQueuedToCheckIn()) {
                progressString = progressString.concat(":warning: **" + member.get().getEffectiveName() + "**\n");
            }
            else {
                progressString = progressString.concat(":x: ~~" + member.get().getEffectiveName() + "~~\n");
            }
        } while (++index < checkInList.size());

        if (update) {
            checkInProgressionEmbed.editMessageEmbeds(checkInProgressionEntry.setMessage(prefix + progressString).getEmbed()).queue();
        }
        else {
            checkInProgressionEntry = new MessageEntry("Check-In In Progress", prefix + progressString,  EmbedDesign.WARNING,
                    mainConfig, msg, TargetChannelSet.TEAM);
            getCheckInProgressionEmbedChannel().sendMessageEmbeds(checkInProgressionEntry.getEmbed()).queue(m -> {
                checkInProgressionEmbed = m;
            });
        }
    }
    public void addSearchCommands(MessageEntry entry) {
        toSearchPurge.add(entry.getResultEmbed());
        toSearchPurge.add(entry.getOriginalCmd());
    }
    private void purgeAllCommands() {
        toPurge.forEach(m -> {
            m.delete().queue(success -> {}, error -> {
                log.warn("Normal Purge for the Check-In Channel threw an error: " + error.getMessage());
            });
        });
        toPurge.clear();
        toSearchPurge.forEach(msg -> {
            msg.delete().queue(success -> {}, error -> {
                log.warn("Search Purge for the Check-In Channel threw an error: " + error.getMessage());
            });
        });
        toSearchPurge.clear();
    }
    private void printStartupEmbeds(boolean update) {
        int index = 0;
        do {
            if (update) {
                checkInStartupMessages.get(index).editMessageEmbeds(checkInStartupEntryObjects.get(index).getEmbed(false)).queue();
            }
            else {
                getCheckInManagementEmbedChannel().sendMessageEmbeds(checkInStartupEntryObjects.get(index).getEmbed(false))
                        .queue(msg -> {
                            checkInStartupMessages.add(msg);
                            toPurge.add(msg);
                        });
            }
        } while (++index < checkInStartupEntryObjects.size());
    }
    public TextChannel getCheckInManagementEmbedChannel() {
        if (!ciConfig.checkInChannelID.equalsIgnoreCase("None")) {
            return ciConfig.getCheckInChannel();
        }
        else {
            if (mainConfig.managementChannel != null) return mainConfig.managementChannel;
            else return mainConfig.discussionChannel;
        }
    }
    private TextChannel getCheckInProgressionEmbedChannel() {
        if (mainConfig.managementChannel != null) return mainConfig.managementChannel;
        else return mainConfig.discussionChannel;
    }
    private void addCheckMarkReactionToMessage(Message msg) {
        msg.addReaction("\u2705").queue();
        toPurge.add(msg);
    }
    private void addXReactionToMessage(Message msg) {
        msg.addReaction("\u274C").queue();
        toPurge.add(msg);
    }

    public boolean isCheckInRunning() {
        return checkInRunning;
    }

    public boolean isCommand(String arg0, String arg1) {
        if (commands.contains(arg0.toLowerCase()) && firstArg.contains(arg1.toLowerCase())) return true;
        else if (commands.contains(arg0) && (arg0.toLowerCase().equalsIgnoreCase("afk")
                || arg0.toLowerCase().equalsIgnoreCase("afkcheck"))) {
            return true;
        }
        else return false;
    }

    public String getStatusString() {
        String defaultOutput = "\n\n*__Check-In Feature__*";
        defaultOutput = defaultOutput.concat("\nStatus: **?**");

        if (ciConfig.isEnabled()) {
            switch (guild.getJDA().getStatus()) {
                case AWAITING_LOGIN_CONFIRMATION:
                case ATTEMPTING_TO_RECONNECT:
                case LOGGING_IN:
                case WAITING_TO_RECONNECT:
                case CONNECTING_TO_WEBSOCKET:
                case IDENTIFYING_SESSION:
                    defaultOutput = defaultOutput.replace("?", ":warning: Connecting");
                    break;
                case INITIALIZED:
                case INITIALIZING:
                case LOADING_SUBSYSTEMS:
                    defaultOutput = defaultOutput.replace("?", ":warning: Starting");
                    break;
                case DISCONNECTED:
                case FAILED_TO_LOGIN:
                    defaultOutput = defaultOutput.replace("?", ":warning: Disconnected");
                    break;
                case RECONNECT_QUEUED:
                    defaultOutput = defaultOutput.replace("?", ":warning: Connection Queued");
                    break;
                case CONNECTED:
                    if (commandsSuspended && !isBusy && isConnected) defaultOutput =
                            defaultOutput.replace("?", "Limited");
                    else if (guild.getJDA().getGatewayPing() >= mainConfig.highPingTime && isConnected)
                        defaultOutput = defaultOutput.replace("?", ":warning: High Ping");
                    else if (isConnected && !isBusy && !commandsSuspended)
                        defaultOutput = defaultOutput.replace("?", "Waiting for Command...");
                    else if (isBusy) defaultOutput = defaultOutput.replace("?", ":warning: Busy");
                    else defaultOutput = defaultOutput.replace("?", ":warning: Connected - Not Ready");
                    break;
                default:
                    defaultOutput = defaultOutput.replace("?", "Unknown");
            }

            defaultOutput = defaultOutput.concat("\nCheck-In Status: **!**");

            if (checkInRunning && checkInConfirmed) {
                int remainingPlayers = 0;
                int index = 0;

                List<CheckInPlayer> playerList = ciCore.getCheckInList();

                do {
                    if (!playerList.get(index++).successfullyCheckedIn()) {
                        remainingPlayers++;
                    }
                } while (index < playerList.size());

                defaultOutput = defaultOutput.replace("!", "Running")
                        .concat("\nRemaining Players: **" + remainingPlayers)
                        .concat("**\nTime Remaining: **" + ciTimer.getRemainingTime() + "**");
            }
            else if (checkInRunning && !checkInConfirmed) {
                defaultOutput = defaultOutput.replace("!", "Queued To Start");
            }
            else {
                defaultOutput = defaultOutput.replace("!", "Idle");
            }
        }
        else {
            defaultOutput = defaultOutput.replace("?", "Disabled");
        }


        return defaultOutput;
    }

    public CheckInConfiguration getConfig() {
        return ciConfig;
    }
}