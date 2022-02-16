package Angel.CheckIn;

import Angel.CheckIn.AFKChecks.AFKCheckManagement;
import Angel.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
    // Messages to Purge when check-in is confirmed
    private List<Message> toPurge = new ArrayList<>();
    // Messages to tell EmbedEngine to purge when the check-in is confirmed
    private List<Message> toSearchPurge = new ArrayList<>();
    // Containers for the check-in startup messages, not the running.
    private List<Message> checkInStartupMessages = new ArrayList<>();
    private List<MessageEntry> checkInStartupEntryObjects = new ArrayList<>();
    // Session Channel in Question
    private TextChannel sessionChannel;
    private ZonedDateTime checkInStartDate;
    private boolean commandsSuspended = false;
    private boolean isConnected = false;
    private boolean checkInRunning = false;
    private boolean checkInConfirmed = false;
    private boolean isBusy = false;
    public final List<String> commands = Arrays.asList("checkin", "ci", "reprint");
    private List<String> emojiList = new ArrayList<>();
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
                "\u0039" + makeKeyCap, "\u0039" + makeKeyCap, "\uD83D\uDD1F");
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        try {
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
                case 0: embed.setAsSuccess("Check-In Roles Setup Successfully","Roles that can be checked-in were successfully setup");
                        log.info("Roles Configured Successfully");
                        break;
                case 1: embed.setAsWarning("Check-In Roles Setup Warning",
                        ":warning: One or More of the role IDs that can be checked-in in the config file were not found");
                        log.warn("One or More of the role IDs that can be checked-in in the config file were not found");
                        break;
                case 2: embed.setAsStop("Check-In Roles Setup Fatal",
                        "The configured roles that can be checked-in are not usable\n\n**Commands Suspended**");
                        commandsSuspended = true;
                        log.fatal("The configured roles that can be checked-in are not usable");
                        break;
            }
            embed.sendToLogChannel();
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

        if (event.getMessage().getChannel() == ciConfig.getCheckInChannel() && checkInConfirmed) {
            checkInPlayer(msg);
        }

        if (msg.getContentRaw().charAt(0) == mainConfig.commandPrefix && !commandsSuspended) {
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
                        afkCheck.startNewCheckIn(msg, 15, 5);
                    }
                    break;
                case "reprint":
                    if (checkInConfirmed && msg.getChannel() == sessionChannel) {
                        checkInSessionChannelEmbed.delete().queue();
                        sessionChannel.sendMessage(checkInSessionChannelEntry.getEmbed()).queue();
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
                case "pardon":
                    pardonCheckIn(msg);
                    break;
                case "resultlist":
                case "rlist":
                case "playerlist":
                case "plist":
                    listCheckIns(msg);
                    break;
            }
        }
    }
    private void checkInPlayer(Message msg) {
        AtomicReference<Member> member = new AtomicReference<>();
        guild.retrieveMember(msg.getAuthor()).queue(m -> member.set(m));

        if (discord.isTeamMember(msg.getAuthor().getIdLong())) {
            embed.setAsError("Exempt From Check-In", ":x: **You Are Exempt From Needing to Check-In**");
            embed.sendDM(msg, msg.getAuthor());
            // Reserved For Team Member Needing Help with Check-In
        }
        else if (ciCore.checkInMember(msg.getAuthor(), ciTimer.getRemainingTime())) {
            msg.delete().queue();
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
                        msg.delete().queue();
                        break;
                    // /checkin start <Session Channel Mention Inserted>
                    case 3:
                        sessionName = msg.getMentionedChannels().get(0).getName().split("_")[0];
                        break;
                }
                if (msg.getMentionedChannels().size() >= 2) {
                    embed.setAsError("Too Many Mentioned Channels",
                            ":x: **You gave me too many channel mentions, I was only expecting 1**");
                }
                else if (ciCore.isValidSessionName(sessionName)) {
                    checkInRunning = true;
                    sessionChannel = msg.getTextChannel();
                    ciCore.setupCheckIn(sessionName);

                    if (ciCore.getCheckInList().isEmpty()) {
                        embed.setAsError("Session Empty",
                                ":x: The session you have selected is currently empty... there's nobody to check-in");
                    }
                    else {
                        embed.setAsSuccess("Successful Check-In Start",
                                "**Successfully started a check-in for " + sessionChannel.getAsMention() + "**");
                        sendCheckInStartupPrompts(msg, false);
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

    private void confirmCheckIn(Message msg) {
        if (discord.isTeamMember(msg.getAuthor().getIdLong())) {
            if (!checkInRunning) {
                embed.setAsError("Check-In Not Started",
                        ":x: **You Cannot Do That... you have to start the check-in first**");
                embed.sendToTeamOutput(msg, msg.getAuthor());
            }
            else {
                checkInConfirmed = true;
                purgeAllCommands();
                checkInStartDate = ZonedDateTime.now(ZoneId.of("UTC"));
                embed.setAsWarning("Check-In Starting", "**Check-In Confirmed! " +
                        "Please wait while all the players to be checked-in are assigned the "
                        + ciConfig.getCheckInRole().getAsMention() + " role...**" +
                        "\n\n*This may take up to a minute to complete... Please be Patient...*");
                embed.sendToTeamOutput(msg, msg.getAuthor());

                ciCore.getCheckInList().forEach(m -> {
                    if (!mainConfig.testModeEnabled) {
                        guild.addRoleToMember(m.getPlayer(), ciConfig.getCheckInRole()).queue();
                        log.info("Successfully Added Role " + ciConfig.getCheckInRole().getName() + " to " +
                                m.getPlayer().getEffectiveName() + " (Discord ID: " + m.getPlayer().getIdLong() + ")");
                    }
                    else {
                        log.warn("Would Have Successfully Added Role " + ciConfig.getCheckInRole().getName() + " to " +
                        m.getPlayer().getEffectiveName() + " (Discord ID: " + m.getPlayer().getIdLong() + ") but could not because I'm in test mode...");
                    }
                });
                sendSessionChannelMessage(false);

                ciTimer.startTimer();
                embed.editEmbed(msg, "Check-In Started", "**Check-In has Successfully Started for " +
                        sessionChannel.getAsMention() + "**", EmbedDesign.SUCCESS);
                sendCheckInProgressEmbed(msg, false);
            }
        }
    }
    // ci pardon <Player Mention or Discord ID> [Result ID]
    // [Result ID] can be empty if you want to pardon the last result.
    // Otherwise if this argument is included then we want to pardon that specific result
    private void pardonCheckIn(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");

        String result = "";
        CheckInResult ciResult = null;
        int checkInIDForPardon = 0;
        AtomicReference<Member> staffMember = new AtomicReference<>();
        AtomicReference<Member> pardonedMember = new AtomicReference();
        boolean successfulPardon = false;

        if (discord.isTeamMember(msg.getAuthor().getIdLong())) {
            if (args.length <= 2 || args.length >= 5) {
                embed.setAsError("Syntax Error", ":x: **Invalid Syntax there..." +
                        "\nThe syntax for the pardon command is `" + mainConfig.commandPrefix +
                        "checkin pardon <Player Mention or Discord ID> [Result ID]`");
                embed.sendToTeamOutput(msg, msg.getAuthor());
                return;
            }
            else {
                guild.retrieveMemberById(msg.getAuthor().getIdLong()).queue(m -> staffMember.set(m));
                try {
                    guild.retrieveMemberById(Long.parseLong(args[2])).queue(m -> pardonedMember.set(m));
                }
                catch (NumberFormatException ex) {
                    pardonedMember.set(msg.getMentionedMembers().get(0));
                }

                if (args.length == 3) {
                    successfulPardon = ciCore.pardonMemberOnLatestResult(pardonedMember.get());
                    checkInIDForPardon = ciCore.getLatestResult().getId();
                }
                else {
                    try {
                        successfulPardon = ciCore.pardonMemberByResultID(pardonedMember.get(), Integer.parseInt(args[3]));
                        checkInIDForPardon = Integer.parseInt(args[3]);
                    }
                    catch (NumberFormatException ex) {}
                }
            }

            if (successfulPardon) {
                embed.setAsSuccess("Successful Pardon", "**" + msg.getAuthor().getAsMention() + " successfully pardoned result ID " +
                        checkInIDForPardon + " from " + pardonedMember.get().getAsMention() + "'s Check-In Record**");
                embed.sendToTeamOutput(msg, msg.getAuthor());

                log.info("Staff Member " + staffMember.get().getEffectiveName() + " successfully pardoned result ID "
                        + checkInIDForPardon + " from "  + pardonedMember.get().getEffectiveName() + "'s Check-In Record");
            }
            else {

            }
        }
        else {
            embed.setAsError("No Permissions", ":x: **You don't have permissions to do that!**");
            embed.sendToMemberOutput(msg, msg.getAuthor());
            return;
        }
    }
    private void listCheckIns(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        String result = "";
        int index = 0;

        String prefix = "";
        String suffix = "";

        List<String> pages = new ArrayList<>();

        switch (args[1].toLowerCase()) {
            case "playerlist":
            case "plist":
                // ci playerlist <Player Mention or Discord ID>
                // This is for showing all of the check-ins the player got and which have been pardoned or not.
                CheckInRecord record;
                CheckInPardonList pardonList;
                prefix = "**Check-Ins For #\n**" +
                        ":warning: *Indicates The Result Is Not Pardoned* \n" +
                        ":white_check_mark: *Indicates The Result Is Pardoned*\n\n";
                AtomicReference<Member> targetMember = new AtomicReference<>();
                try {

                    guild.retrieveMemberById(Long.parseLong(args[2])).queue(m -> targetMember.set(m));
                    result = result.replace("#", targetMember.get().getAsMention());
                    record = ciCore.getRecordByDiscordID(Long.parseLong(args[2]));
                }
                catch (NumberFormatException ex) {
                    record = ciCore.getRecordByMember(msg.getMentionedMembers().get(0));
                    targetMember.set(msg.getMentionedMembers().get(0));
                    result = result.replace("#", msg.getMentionedMembers().get(0).getAsMention());
                }

                if (record == null) {
                    embed.setAsError("No Records Found", "**" + targetMember.get().getAsMention() + " has never been included in any check-ins**");
                }

                pardonList = record.getPardonedList();

                do {
                    String emoteIndicator = "";
                    if (pardonList.getIsPardoned().get(index)) {
                        emoteIndicator = ":white_check_mark:";
                    }
                    else {
                        emoteIndicator = ":warning:";
                    }

                    result = result.concat("\n" + emoteIndicator + " ID: **" + pardonList.getResultsList().get(index).getId() +
                            "** Date: **" + discord.getDefaultSDF().format(pardonList.getResultsList().get(index).getStartDate()) + "**");

                    if ((index + 1) % 10 == 0) {
                        pages.add(result);
                        result = "";
                    }
                } while (++index < pardonList.getResultsList().size());

                if (!result.equals("")) pages.add(result);

                suffix = "Total Number of Missed Check-Ins: **" + record.getMissedCheckIns() + "**" +
                        "\n\n*If you want to see the full list of players involved in one of the check-ins listed*" +
                        "\n**Use: `" + mainConfig.commandPrefix + "checkin resultlist <id>`**";

                discord.addAsReactionListEmbed(new ListEmbed(new MessageEntry("Check-In Result", EmbedDesign.INFO, mainConfig, msg, TargetChannelSet.TEAM),
                        prefix, pages, null).invertButtonLabels(true));
                break;
            case "resultlist":
            case "rlist":
            case "list":
                // This is for listing all the players involved in a particular check-in
                // ci resultlist [ID]
                try {
                    CheckInResult resultList = null;
                    if (args.length == 2) {
                        resultList = ciCore.getLatestResult();
                    }
                    else if (args.length == 3) {
                        resultList = ciCore.getResultByID(Integer.parseInt(args[2]));
                    }

                    prefix = "**Check-In Results:**\n" +
                            "Date Initiated: **" + discord.getDefaultSDF().format(resultList.getStartDate()) + "**\n\n" +
                            ":white_check_mark: *Indicates The Player Did Check-In along with how much time was remaining when they did.*\n" +
                            "*:warning: Indicates The Player Failed to Check-In\n" +
                            "*:x: Indicates The Player Was Removed From the Check-In Queue* \n\n";

                    List<CheckInPlayer> players = resultList.getPlayers();
                    do {
                        CheckInPlayer p = players.get(index);
                        if (p.successfullyCheckedIn()) {
                            result = result.concat(":white_check_mark: ID: **" + p.getId() + " " + p.getPlayer().getEffectiveName()  +
                                    "** \nTime Remaining: **" + p.getCheckInRemainingTime() + "**");
                        }
                        else {
                            if (!p.isQueuedToCheckIn()) {
                                result = result.concat(":x: ID: ~~" + p.getId() + " " + p.getPlayer().getEffectiveName() + "~~");
                            }
                            else {
                                result = result.concat(":warning: ID: **" + p.getId() + " " + p.getPlayer().getEffectiveName() + "**");
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
                            prefix, pages, null).invertButtonLabels(true));
                }
                catch (NumberFormatException ex) {
                    embed.setAsError("Invalid Result ID", ":x: **Invalid Result ID** \n\nYou can find the result IDs with ");
                    embed.sendToTeamOutput(msg, msg.getAuthor());
                }
                break;
        }

    }

    void sendSessionChannelMessage(boolean update) {
        String checkInWarningString = ":warning: **A Check-In Has Started for this session!**" +
                "\n\n*Please reply in ? with `" + mainConfig.commandPrefix + "checkin` or with a message to confirm you are paying attention to discord.*" +
                "\nYou'll receive confirmation you have checked-in via direct message" +
                "\n\nTime Remaining: **" + ciTimer.getRemainingTime() + "**" +
                "\n\n:warning: ***Do Not Bump This Message** except with kickvotes or other emergencies*";

        if (!ciConfig.checkInChannelID.equalsIgnoreCase("None") || ciConfig.getCheckInChannel() == null) {
            checkInWarningString = checkInWarningString.replace("?", "this channel");
        }
        else {
            checkInWarningString = checkInWarningString.replace("?", ciConfig.getCheckInChannel().getAsMention());
        }

        if (update) {
            checkInSessionChannelEmbed.editMessage(checkInSessionChannelEntry.setMessage(checkInWarningString)
                    .getEmbed(false)).queue(m -> {
                        checkInSessionChannelEmbed = m;
            });
        }
        else {
            checkInSessionChannelEntry = new MessageEntry(
                    "Check-In In Progress", checkInWarningString, EmbedDesign.WARNING, mainConfig);
            sessionChannel.sendMessage(checkInSessionChannelEntry.getEmbed(false)).queue(m -> {
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
            else if (!msg.getMentionedMembers().isEmpty()) {
                ciCore.addMemberToCheckIn(msg.getMentionedMembers());
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
            else if (msg.getMentionedMembers().size() == 1) {
                if (ciCore.removeMemberFromCheckIn(msg.getMentionedMembers().get(0))) {
                    addCheckMarkReactionToMessage(msg);
                }
                else {
                    addXReactionToMessage(msg);
                }
            }
            else if (msg.getMentionedMembers().size() >= 2) {
                // No Longer Possible
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
        CheckInResult ciResult = ciCore.endCheckIn(checkInStartDate);

        ciResult.getPlayers().forEach(p -> {
            if (p.isQueuedToCheckIn()) {
                if (mainConfig.testModeEnabled) {
                    log.warn("Would Have Removed the Role " + ciConfig.getCheckInRole().getName() + " from " + p.getPlayer().getEffectiveName() +
                            " (Discord ID: " + p.getPlayer().getIdLong() + ") because the check-in ended, but couldn't because I'm in test mode...");
                }
                else {
                    guild.removeRoleFromMember(p.getPlayer(), ciConfig.getCheckInRole()).queue();
                    log.info("Successfully Removed the Role " + ciConfig.getCheckInRole().getName() + " from " + p.getPlayer().getEffectiveName() +
                            " (Discord ID: " + p.getPlayer().getIdLong() + ") because the check-in ended.");
                }
            }
        });

        checkInProgressionEntry.setTitle("Check-In Ended").setDesign(EmbedDesign.SUCCESS).setMessage("**The Check-In Has Ended**" +
                "\n\nTo See the results for this check in you can use `" + mainConfig.commandPrefix + "ci resultlist`" +
                "\nIf another check-in is run after this one the command is then `" + mainConfig.commandPrefix + "ci resultlist " + ciResult.getId() + "`");
        checkInProgressionEmbed.editMessage(checkInProgressionEntry.getEmbed(false)).queue();

        checkInSessionChannelEntry.setTitle("Check-In Ended").setDesign(EmbedDesign.SUCCESS).setMessage("**The Check-In Has Ended for This Session***");
        checkInSessionChannelEmbed.editMessage(checkInSessionChannelEntry.getEmbed(false)).queue();
        try {
            fileHandler.saveDatabase();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void sendCheckInStartupPrompts(Message originalCmd, boolean update) {
        String memberListPrefix = "These members were successfully queried from the discord server:";
        String duplicateMatches = "These are the queries I performed on this server, and when I tried to search this player it returned" +
                " multiple accounts with any of these roles:\n# \n\n";
        EmbedDesign duplicateMatchesStatus = EmbedDesign.ERROR;
        String unrecogizedPlayers = "These players I did not find a player a discord account for." +
                "\nThis could be due to a slight misread, or it could be due to a rando in the session. " +
                "\n*Please review this list to see if there's any members I might've missed*\n\n";
        String controlHelp = "Use `" + mainConfig.commandPrefix + "checkin add <Player Mention(s)>` " +
                "to add additional players that should be checked in. " +
                "You'll want to add the players that you believe their names were misread and got sent to *Members Not Found*. " +
                "You may add multiple players here." +
                "\n" +
                "\nUse `" + mainConfig.commandPrefix + "checkin remove/del <id>` " +
                "to remove players that were accidently added to *Members To Be Checked-In* due to a zoobot misread. " +
                "You may remove multiple players here." +
                "\n" +
                "\nTo get player mentions for adding to the checkin queue you can use `" + mainConfig.commandPrefix +
                "search <query>` (`" + mainConfig.commandPrefix + "s <query>` for short) whereas `<query>` " +
                "you'd place part of or all of a player's name. " +
                "The bot will then search every member of this server, in their nickname if they have one and their usernames." +
                "\n" +
                "\nAs you use commands I'll be reacting to them with :white_check_mark: or :x: " +
                "to indicate if it was successful or a failure. The messages above will also edit themselves to reflect the change." +
                "\n" +
                "\nWhen you're finished, use `" + mainConfig.commandPrefix + "checkin confirm` " +
                "when you believe everyone that's in the session is underneath the *Members To Be Checked-In* section.";


        int index = 0;
        int emojiIndex = 1;
        List<String> memberList = new ArrayList<>();
        // Setup Check-In List

        String tempString = "";
        do {
            if (ciCore.getCheckInList().get(index).isQueuedToCheckIn()) {
                tempString = tempString.concat(emojiList.get(emojiIndex++) + " :white_check_mark: **" + ciCore.getCheckInList().get(index).getPlayer().getEffectiveName() + "**\n");
            }
            else {
                tempString = tempString.concat(emojiList.get(emojiIndex++) + " :x: ~~" + ciCore.getCheckInList().get(index).getPlayer().getEffectiveName() + "~~\n");
            }

            if (index % 10 == 0) {
                memberList.add(tempString);
                tempString = "";
                emojiIndex = 1;
            }
        } while (++index < ciCore.getCheckInList().size());
        // This ensures the remainder of the tempString is into the memberList
        memberList.add(tempString);

        if (!update) {
            TargetChannelSet queueEmbedChannelSet = TargetChannelSet.TEAM;

            if (originalCmd.getChannel() == ciConfig.getCheckInChannel()) {
                queueEmbedChannelSet = TargetChannelSet.SAME;
            }

            checkInQueueEmbed = new CheckInQueueEmbed(new MessageEntry("Members To Be Checked-In",
                    EmbedDesign.SUCCESS, mainConfig, originalCmd, queueEmbedChannelSet), memberListPrefix, memberList,
                    null, emojiList, this);
            discord.addAsReactionListEmbed(checkInQueueEmbed);
        }
        else {
            discord.updateReactionListEmbed(checkInQueueEmbed, memberList);
        }

        // Construct Duplicate Matches Error Message if there are any
        Enumeration<String> keys = ciCore.getDuplicateMatchHashTable().keys();
        Enumeration<List<Member>> members = ciCore.getDuplicateMatchHashTable().elements();

        if (!members.hasMoreElements()) {
            duplicateMatches = "**No Duplicate Members Found**";
            duplicateMatchesStatus = EmbedDesign.SUCCESS;
        }
        else {
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
            do {
                unrecogizedPlayers = unrecogizedPlayers.concat("**- " + ciCore.getUnrecognizedPlayerList().get(index++) + "**\n");
            }
            while (index < ciCore.getUnrecognizedPlayerList().size());
        }
        else {
            unrecognizedPlayerStatus = EmbedDesign.SUCCESS;
            unrecogizedPlayers = ":white_check_mark: **No Unrecognized Players Found.** " +
                    "However, *that does not mean there is not errors in the players found*, " +
                    "it is advisible to check zoobot's screenshot and compare that to the member list.";
        }
        if (update) checkInStartupEntryObjects.clear();
        checkInStartupEntryObjects.add(checkInQueueEmbed.getMessageEntry());
        checkInStartupEntryObjects.add(new MessageEntry("Duplicate Accounts", duplicateMatches, duplicateMatchesStatus, mainConfig));
        checkInStartupEntryObjects.add(new MessageEntry("Members Not Found", unrecogizedPlayers, unrecognizedPlayerStatus, mainConfig));
        checkInStartupEntryObjects.add(new MessageEntry("Check-In Pre-Start Controls", controlHelp, EmbedDesign.HELP, mainConfig));

        printStartupEmbeds(update);
    }
    void sendCheckInProgressEmbed(@Nullable Message msg,  boolean update) {
        String prefix = "**A Check-In has started for " + sessionChannel.getAsMention() + "**" +
                "\nTime Remaining: **" + ciTimer.getRemainingTime() + "**" +
                "\n\n*Players List*:" +
                "\n:white_check_mark: **Indicates the player has checked in**" +
                "\n:warning: **Indicates the player has not check-in yet...**" +
                "\n:x: **Indicates the player was removed from the check-in queue prior to confirmation**";

        String progressString = "";
        List<String> pages = new ArrayList<>();

        int index = 0;
        List<CheckInPlayer> checkInList = ciCore.getCheckInList();
        do {
            if (checkInList.get(index).successfullyCheckedIn() && checkInList.get(index).isQueuedToCheckIn()) {
                progressString = progressString.concat(":white_check_mark: **" + checkInList.get(index).getPlayer().getEffectiveName() +
                        "** @ " + checkInList.get(index).getCheckInRemainingTime() + "\n");
            }
            else if (checkInList.get(index).isQueuedToCheckIn()) {
                progressString = progressString.concat(":warning: **" + checkInList.get(index).getPlayer().getEffectiveName() + "**\n");
            }
            else {
                progressString = progressString.concat(":x: ~~" + checkInList.get(index).getPlayer().getEffectiveName() + "~~\n");
            }
        } while (++index < checkInList.size());

        if (update) {
            checkInProgressionEmbed.editMessage(checkInProgressionEntry.setMessage(progressString).getEmbed()).queue(m -> {
                checkInProgressionEmbed = m;
            });
        }
        else {
            checkInProgressionEntry = new MessageEntry("Check-In In Progress", EmbedDesign.WARNING,
                    mainConfig, msg, TargetChannelSet.TEAM);
            getCheckInProgressionEmbedChannel().sendMessage(checkInProgressionEntry.getEmbed()).queue(m -> {
                checkInProgressionEmbed = m;
            });
        }
    }
    public void addSearchCommands(Message msg) {
        toSearchPurge.add(msg);
    }
    private void purgeAllCommands() {
        getCheckInManagementEmbedChannel().purgeMessages(toPurge);
        toPurge.clear();
        toSearchPurge.forEach(msg -> {
            embed.deleteResultsByCommand(msg);
        });
        toSearchPurge.clear();
    }
    private void printStartupEmbeds(boolean update) {
        int index = 0;
        do {
            if (update) {
                checkInStartupMessages.get(index).editMessage(checkInStartupEntryObjects.get(index).getEmbed(false)).queue();
            }
            else {
                getCheckInManagementEmbedChannel().sendMessage(checkInStartupEntryObjects.get(index).getEmbed(false))
                        .queue(msg -> checkInStartupMessages.add(msg));
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

    public String getStatusString() {
        String defaultOutput = "*__Check-In Feature__*";
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
                defaultOutput.replace("!", "Idle");
            }
        }
        else {
            defaultOutput = defaultOutput.replace("?", "Disabled");
        }


        return defaultOutput;
    }
}