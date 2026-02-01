package Angel;

import Angel.BotAbuse.BotAbuseInit;
import Angel.BotAbuse.BotAbuseMain;
import Angel.CheckIn.CheckInInit;
import Angel.CheckIn.CheckInMain;
import Angel.CustomEmbeds.CustomEmbedInit;
import Angel.CustomEmbeds.CustomEmbedMain;
import Angel.Nicknames.NicknameInit;
import Angel.Nicknames.NicknameMain;
import Angel.PlayerList.PlayerListInit;
import Angel.PlayerList.PlayerListMain;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveAllEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class DiscordBotMain extends ListenerAdapter implements CommonLogic {
    private final Logger log = LogManager.getLogger(DiscordBotMain.class);
    private FileHandler fileHandler;
    private Guild guild;
    private CustomEmbedInit customEmbedInit;
    private CustomEmbedMain customEmbedFeature = null;
    private NicknameMain nickFeature = null;
    private NicknameInit nickInit;
    private BotAbuseMain baFeature = null;
    private BotAbuseInit baInit;
    private CheckInInit ciInit;
    private CheckInMain ciFeature = null;
    private PlayerListInit playerListInit;
    private PlayerListMain playerListMain = null;
    private int restartValue;
    private boolean commandsSuspended = false;
    public boolean isStarting = true;
    private ArrayList<Date> pingCooldownOverTimes = new ArrayList<>();
    private ArrayList<Long> pingCooldownDiscordIDs = new ArrayList<>();
    public final List<String> mainCommands = new ArrayList<>(Arrays.asList("search", "s", "reload", "restart", "ping", "status", "help", "set", "e", "embed"));

    private List<ListEmbed> listEmbeds = new ArrayList<>();
    private Dictionary<Message, ScheduledFuture<?>> reactionClearTimers = new Hashtable<>();

    // File Garbage Truck Class for the Log Files
    private FileGarbageTruck logFileGarbageTruck;

    DiscordBotMain() {
        this.fileHandler = new FileHandler();
    }

    void setRestartValue(int value) {
        restartValue = value;
    }

    public int getRestartValue() {
        return restartValue;
    }
    @Override
    public void onReady(ReadyEvent event) {
        Thread.currentThread().setUncaughtExceptionHandler(aue);
        JDA jda = event.getJDA();
        guild = jda.getGuilds().get(0);
        mainConfig.guild = this.guild;
        if (!mainConfig.discordGuildConfigurationsExist()) {
            log.fatal("One or More of the Discord Configurations Don't Exist - Commands have been suspended in all features");
            commandsSuspended = true;
        }
        else {
            log.info("Setting Up Main Config's Discord Settings");
            mainConfig.discordSetup();
        }

        logFileGarbageTruck = new FileGarbageTruck("Log", "logs/Previous", 11)
                .setFileNamingPattern("MM-dd-yy-HH-mm-ss-1").setDaysToStoreFilesBeforeDeletion(180).filesDoNotIncludeTimeZones();

        nickInit = new NicknameInit(commandsSuspended);
        baInit = new BotAbuseInit();
        ciInit = new CheckInInit();
        customEmbedInit = new CustomEmbedInit();
        playerListInit = new PlayerListInit();

        Thread tNickFeature = new Thread(nickInit);
        Thread tBotAbuseFeature = new Thread(baInit);
        Thread tCustomEmbedFeature = new Thread(customEmbedInit);
        Thread tCheckInFeature = new Thread(ciInit);
        Thread tPlayerListFeature = new Thread(playerListInit);

        tNickFeature.setUncaughtExceptionHandler(aue);
        tNickFeature.setName("Nickname Thread");
        tNickFeature.start();

        tBotAbuseFeature.setUncaughtExceptionHandler(aue);
        tBotAbuseFeature.setName("Bot Abuse Thread");
        tBotAbuseFeature.start();

        tCheckInFeature.setUncaughtExceptionHandler(aue);
        tCheckInFeature.setName("Check-In Thread");
        tCheckInFeature.start();

        tCustomEmbedFeature.setUncaughtExceptionHandler(aue);
        tCustomEmbedFeature.setName("Custom Embed Thread");
        tCustomEmbedFeature.start();

        tPlayerListFeature.setUncaughtExceptionHandler(aue);
        tPlayerListFeature.setName("Player List Thread");
        tPlayerListFeature.start();

        while (baFeature == null || nickFeature == null || customEmbedFeature == null || ciFeature == null || playerListMain == null) {
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            try {
                baFeature = baInit.getBaFeature();
                nickFeature = nickInit.getNickFeature();
                ciFeature = ciInit.getThis();
                customEmbedFeature = customEmbedInit.getFeature();
                playerListMain = playerListInit.getPlayerListFeature();
            }
            catch (NullPointerException ex) {}
        }
        log.info("All Features Successfully Initalized");
        isStarting = false;
        if (restartValue != 2 && !commandsSuspended) {
            String defaultTitle = "Startup Complete";

            if (restartValue == 1) defaultTitle = defaultTitle.replace("Startup", "Restart");

            embed.setAsSuccess(defaultTitle,
                    "**All Systems Are Go! Remember I'm divided into 3 sections...**" +
                            "\n**You can check on their status with `" + mainConfig.commandPrefix + "status`**");
            embed.sendToChannel(null, mainConfig.discussionChannel);
        }
    }

    @Override
    public void onMessageReactionRemoveAll(@NotNull MessageReactionRemoveAllEvent event) {
        Thread.currentThread().setUncaughtExceptionHandler(aue);
        int index = 0;
        do {
            if (listEmbeds.get(index).getMessageEntry().getResultEmbed().getIdLong() == event.getMessageIdLong()
                    && listEmbeds.get(index).getTotalPages() == 0) {
                reactionClearTimers.remove(listEmbeds.remove(index).getMessageEntry().getResultEmbed()).cancel(true);
                break;
            }
        } while (++index < listEmbeds.size());
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        Thread.currentThread().setUncaughtExceptionHandler(aue);
        event.retrieveMessage().queue(msg -> {
            if (msg.getAuthor() == msg.getJDA().getSelfUser() && event.getUser() != msg.getJDA().getSelfUser()) {
                MessageEntry entry = null;
                ListEmbed listEmbed = null;

                listEmbed = getListEmbedFromMsg(msg);
                if (listEmbed != null) {
                    entry = listEmbed.getMessageEntry();
                }

                if (entry == null || !entry.isListEmbed()) return;

                if (event.getUser().getIdLong() != entry.getOriginalCmd().getAuthor().getIdLong() &&
                        !isTeamMember(event.getUser().getIdLong())) return;

                // Action to Remove the User's reaction add before any processing
                // this has to happen in any case so this is among the first things that happen
                event.getReaction().removeReaction(event.getUser()).queue();

                int previousIndex = listEmbed.getCurrentPageIndex();

                switch (event.getReaction().getEmoji().getAsReactionCode()) {
                        // First Page
                    case "\u23EE\uFE0F":
                        entry.setMessage(listEmbed.getFirstPage()); break;
                        // Previous Page
                    case "\u2B05\uFE0F":
                        entry.setMessage(listEmbed.getPreviousPage()); break;
                        // Stop Button
                    case "\u23F9\uFE0F":
                        msg.clearReactions().queue();
                        return;
                        // Next Page
                    case "\u27A1\uFE0F":
                        entry.setMessage(listEmbed.getNextPage()); break;
                        // Last Page
                    case "\u23ED\uFE0F":
                        entry.setMessage(listEmbed.getLastPage()); break;
                    default:
                        if (listEmbed.isCustomEmbed()) {
                            listEmbed.getCustomListEmbed().takeAction(entry.getOriginalCmd(), event);
                            entry.setMessage(listEmbed.getCurrentPage());

                            if (listEmbed.getTotalPages() == 0) {
                                entry.getResultEmbed().editMessageEmbeds(listEmbed.getMessageEntry().getEmbed()).queue();
                                entry.getResultEmbed().clearReactions().queue();
                            }
                            else if (listEmbed.getTotalPages() == 1) {
                                msg.clearReactions().queue();
                                addCustomEmotes(msg);
                                entry.getResultEmbed().editMessageEmbeds(entry.setTitle(entry.getTitle().split(" - Page")[0]).getEmbed()).queue();
                            }
                        }
                }
                if (listEmbed.getTotalPages() >= 2) {
                    entry.getResultEmbed().editMessageEmbeds(entry.setTitle(entry.getTitle().replace(
                            previousIndex + "/" + listEmbed.getTotalPages(),
                            listEmbed.getCurrentPageIndex() + "/" + listEmbed.getTotalPages()))
                            .getEmbed()).queue();
                    reactionClearTimers.remove(msg).cancel(true);
                    reactionClearTimers.put(msg, msg.clearReactions().queueAfter(30, TimeUnit.MINUTES, success -> {
                        log.info("Reaction Clear Queue: All Reactions Cleared Successfully");
                    }, error -> {
                        log.warn("Reaction Clear Queue: Message to Clear Reactions From Not Found - " + error.getMessage());
                        reactionClearTimers.remove(msg);
                    }));
                }
            }
        });
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        Thread.currentThread().setUncaughtExceptionHandler(aue);
        Enumeration<Message> messages = reactionClearTimers.keys();

        do {
            try {
                Message messageInQuestion = messages.nextElement();
                if (messageInQuestion.getIdLong() == event.getMessageIdLong()) {
                    reactionClearTimers.remove(messageInQuestion).cancel(true);
                }
            }
            catch (NoSuchElementException ex) {
                break;
            }
        } while (messages.hasMoreElements());
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Thread.currentThread().setUncaughtExceptionHandler(aue);
        if (event.getAuthor().isBot()) return;
        if (event.getChannelType().equals(ChannelType.GUILD_PRIVATE_THREAD) || event.getChannelType().equals(ChannelType.GUILD_PUBLIC_THREAD)) return;
        Message msg = event.getMessage();

        if (isTeamMember(msg.getAuthor().getIdLong())) {
            logFileGarbageTruck.dumpFiles();
        }

        String[] args = null;
        try {
            args = msg.getContentRaw().substring(1).split(" ");
        }
        catch (StringIndexOutOfBoundsException ex) {
            log.warn("A message that was just got sent in #" + msg.getChannel().getName() +
                    " could not be processed as a sentence.");
            return;
        }
        if (event.getMessage().getContentRaw().charAt(0) == mainConfig.commandPrefix && isValidCommand(msg)) {
            guild.retrieveMemberById(event.getAuthor().getIdLong()).useCache(false).submit().whenComplete(new BiConsumer<Member, Throwable>() {
                @Override
                public void accept(Member member, Throwable throwable) {
                    if (throwable == null) {
                        if (event.getMessage().getChannelType().equals(ChannelType.PRIVATE)) {
                            log.info(member.getEffectiveName() + "@DM: " + event.getMessage().getContentRaw());
                        }
                        else {
                            log.info(member.getEffectiveName() + "@" + event.getMessage().getChannel().getName() + ": " +
                                    event.getMessage().getContentRaw());
                        }
                    }
                    else {
                        log.info(throwable.getMessage());
                        if (throwable.getMessage().contains("UNKNOWN_MEMBER")) {
                            MessageEntry entry = new MessageEntry("No Permissions to Use Me!", "You Do Not Have Permissions to use me at all because you're not a member of the SAFE Crew discord server!",
                                    EmbedDesign.STOP);

                            event.getMessage().replyEmbeds(entry.getEmbed()).queue();
                            return;
                        }
                        else {
                            log.warn("Unable to Retrieve Member Data for Command: " + event.getMessage().getContentRaw() + " - " + throwable.getMessage());
                        }
                    }
                }
            });
        }

        if ((msg.getChannel().getType() != ChannelType.PRIVATE && (msg.getChannel().asTextChannel().equals(mainConfig.managementChannel) || msg.getChannel().asTextChannel().equals(mainConfig.dedicatedOutputChannel)))
                && !isValidCommand(msg)) {
            MessageEntry entry = new MessageEntry();

            if (msg.getContentRaw().charAt(0) != mainConfig.commandPrefix && isTeamMember(msg.getAuthor().getIdLong())) {
                entry = entry.setDesign(EmbedDesign.WARNING).setTitle("No Messages Here").dontUseFieldHeader()
                        .setMessage("You Cannot send messages in this channel, *hence the name*... \n**You can only send commands!**" +
                        "\n\nTake discussion to " +
                        mainConfig.discussionChannel.getAsMention());
            }
            else if (msg.getContentRaw().charAt(0) != mainConfig.commandPrefix && !isTeamMember(msg.getAuthor().getIdLong())) {
                entry = entry = entry.setDesign(EmbedDesign.WARNING).setTitle("No Messages Here").dontUseFieldHeader()
                        .setMessage("You Cannot send messages in this channel, *hence the name*... \n**You can only send commands!**" +
                        "\n\nIf you need help, take that to " +
                        mainConfig.helpChannel.getAsMention());
            }
            else {
                entry = entry.setDesign(EmbedDesign.STOP).setTitle("No Messages Here").dontUseFieldHeader()
                        .setMessage("*I know what you're trying to do*... what did you think I only base this filter on the first character?");
            }
            msg.getChannel().asTextChannel().sendMessageEmbeds(entry.getEmbed()).submit().whenComplete(new BiConsumer<Message, Throwable>() {
                @Override
                public void accept(Message message, Throwable throwable) {
                    if (throwable == null) {
                        msg.delete().queueAfter(30, TimeUnit.SECONDS);
                    }
                    else {
                        aue.logCaughtException(Thread.currentThread(), throwable);
                    }
                }
            });
            log.warn("Message Deleted - Management or Dedicated Output Channel - Not Valid Command");
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
                    embed.setAsWarning("Nickname Feature Busy", "**The Nickname Side is Currently Busy**" +
                            "\n\nYour command was received and will be processed when it's available");
                    warningNeeded = true;
                }
                else if (!nickFeature.isConnected) {
                    embed.setAsWarning("Nickname Feature Disconnected",
                            "**The Nickname Side is Not Connected to Discord's Gateway**" +
                                    "\n\nPlease Try Again Later...");
                    warningNeeded = true;
                }
            }
            if (warningNeeded && !isTeamMember(msg.getAuthor().getIdLong())) {
                embed.sendToMemberOutput(msg, msg.getAuthor());
            }
            else if (warningNeeded && isTeamMember(msg.getAuthor().getIdLong())) {
                embed.sendToTeamOutput(msg, msg.getAuthor());
            }
        }
        if (!msg.getChannelType().equals(ChannelType.PRIVATE) && msg.getMentions().getMembers().contains(guild.getSelfMember())) {
            msg.getChannel().sendMessage("<:blobnomping:" + mainConfig.blobNomPingID + ">").queue();
            log.info("Nommed the Ping from " + msg.getAuthor().getAsTag());
        }
        else if (msg.getContentRaw().charAt(0) == mainConfig.commandPrefix) {
            switch (args[0].toLowerCase()) {
                case "search":
                case "s":
                    searchCommand(msg);
                    break;
                case "restart":
                    if (isStaffMember(event.getAuthor().getIdLong())) {
                        try {
                            msg.delete().queue();
                            log.warn("Message Deleted - restart command");
                            if (args.length == 1) {
                                embed.setAsWarning("Restart Initiated", "**Restart Initiated by " + msg.getAuthor().getAsMention()
                                        + "\nPlease Allow up to 10 seconds for this to complete**");
                                log.warn(msg.getAuthor().getAsTag() + " Invoked a Restart");
                                embed.sendToTeamOutput(msg, null);
                                Thread.sleep(5000);
                                restartBot(false);
                            }
                            else if (args.length == 2) {
                                if (args[1].equalsIgnoreCase("-s") || args[1].equalsIgnoreCase("silent")) {
                                    log.warn(msg.getAuthor().getAsTag() + " Invoked a Silent Restart");
                                    Thread.sleep(5000);
                                    restartBot(true);
                                }
                                else {
                                    embed.setAsError("Invalid Argument",
                                            ":x: **That argument you gave with this command... I did not recognize it**");
                                    embed.sendToTeamOutput(msg, msg.getAuthor());
                                }
                            }
                            else {
                                embed.setAsError("Too Many Arguments!", ":x: **You just gave me too many arguments...**");
                                embed.sendToTeamOutput(msg, msg.getAuthor());
                            }

                        }
                        catch (NoClassDefFoundError ex) {
                            // Take No Action - This is an indicator that the jar file for the program was overwritten while the bot is running
                        }
                        catch (IllegalStateException ex) {
                            // Take No Action - This is an indicator that the command was used via DM
                        }
                        catch (IOException e) {
                            log.error("Restart Command", e);
                        }
                        catch (InterruptedException e) {
                            // Take No Action
                        }
                    }
                    else {
                        embed.setAsError("No Permissions", ":x: **You Need to be full staff member to restart me... " +
                                "you're not quite there... yet...**");
                        embed.sendToTeamOutput(msg, msg.getAuthor());
                    }
                    break;
                case "reload":
                    if (isStaffMember(event.getAuthor().getIdLong())) {
                        embed.setAsWarning("Reloading Configuration", "**Reloading Configuration... Please Wait a Few Seconds...**");
                        embed.sendToTeamOutput(msg, null);
                        try { Thread.sleep(5000); } catch (InterruptedException e) {}
                        if (mainConfig.reload(fileHandler.getMainConfig())) {
                            baFeature.reload(msg);
                            nickFeature.reload(msg);
                            ciFeature.reload();
                            log.info("Successfully Reloaded All Configurations");
                            embed.editEmbed(msg, "Configuration Reloaded",
                                    "**All Configurations Successfully Reloaded from config files**",
                                    EmbedDesign.SUCCESS);
                        }
                        else {
                            baFeature.commandsSuspended = true;
                            nickFeature.commandsSuspended = true;
                            log.fatal("Discord Configurations Not Found - All Commands Suspended");
                        }
                    }
                    else {
                        embed.setAsError("No Permissions", ":x: **You Need to be full staff member to restart me... " +
                                "you're not quite there... yet...**");
                        embed.sendToTeamOutput(msg, msg.getAuthor());
                    }
                    break;
                case "ping":
                    log.info(msg.getAuthor().getAsTag() + " requested my pings");
                    String originalOutput = ":ping_pong: **Pong!**" +
                            "\n**Pinging Discord's Gateway... Please Wait...**";
                    embed.setAsInfo("My Ping Info", originalOutput);
                    if (isTeamMember(event.getAuthor().getIdLong()) && !msg.getChannelType().equals(ChannelType.PRIVATE)) {
                        embed.sendToTeamOutput(msg,null);
                    }
                    else {
                        try {
                            if (msg.getChannelType().equals(ChannelType.PRIVATE)) {
                                embed.sendDM(msg, msg.getAuthor());
                            }
                            // If they use /ping before their cooldown time is over then we send them the ping information in a DM
                            else if (Calendar.getInstance().getTime().before
                                    (pingCooldownOverTimes.get(pingCooldownDiscordIDs.lastIndexOf(msg.getMember().getIdLong())))
                                    && !msg.getChannel().asTextChannel().equals(mainConfig.botSpamChannel)
                                    && !msg.getChannel().asTextChannel().equals(mainConfig.dedicatedOutputChannel)) {
                                embed.sendDM(msg, msg.getAuthor());
                            }
                            // Otherwise we can send them this in the help channel.
                            else {
                                pingHandler(msg.getMember().getIdLong());
                                embed.sendToMemberOutput(msg, msg.getAuthor());
                            }
                        }
                        // This would run if their discord ID wasn't found in pingCooldownDiscordIDs,
                        // a -1 would throw this exception
                        catch (IndexOutOfBoundsException ex) {
                            pingHandler(msg.getMember().getIdLong());
                            embed.sendToMemberOutput(msg, msg.getAuthor());
                        }
                    }

                    String editedPing = "My Ping to Discord's Gateway: **" + getGatewayNetPing() + "ms**" +
                            "\n\n*__Request to Ack Pings__*" +
                            "\nMain Thread: **" + msg.getJDA().getGatewayPing() + "ms**" +
                            "\nBot Abuse Thread: **#**" +
                            "\nNickname Thread: **$**" +
                            "\nCheck-In Thread: **!**" +
                            "\nPlayer List Thread: **%**";

                    if (baFeature.getConfig().isEnabled()) {
                        editedPing = editedPing.replace("#", String.valueOf(baInit.getPing()) + "ms");
                    }
                    else {
                        editedPing = editedPing.replace("#", "Disabled");
                    }

                    if (nickFeature.getConfig().isEnabled()) {
                        editedPing = editedPing.replace("$", String.valueOf(nickInit.getPing()) + "ms");
                    }
                    else {
                        editedPing = editedPing.replace("$", "Disabled");
                    }
                    if (ciFeature.getConfig().isEnabled()) {
                        editedPing = editedPing.replace("!", String.valueOf(ciInit.getPing()) + "ms");
                    }
                    else {
                        editedPing = editedPing.replace("!", "Disabled");
                    }

                    editedPing = editedPing.replace("%", String.valueOf(playerListInit.getPing()) + "ms");

                    embed.editEmbed(msg, null, originalOutput.replace(
                            "**Pinging Discord's Gateway... Please Wait...**", editedPing), null);
                    break;
                case "status":
                    if (isTeamMember(msg.getAuthor().getIdLong())) {
                        try {
                            log.info(guild.getMember(msg.getAuthor()).getEffectiveName() + " just requested my status");
                        }
                        catch (NullPointerException ex) {
                            log.info(msg.getAuthor().getAsTag() + " just requested my status");
                        }
                        String defaultTitle = "My Status";
                        String thisOriginalOutput = "**Retrieving Status... Please Wait...**";
                        embed.setAsInfo(defaultTitle, thisOriginalOutput);
                        if (!msg.getChannelType().equals(ChannelType.PRIVATE)) embed.sendToTeamOutput(msg, msg.getAuthor());
                        else embed.sendDM(msg, msg.getAuthor());
                        try { Thread.sleep(2000); } catch (InterruptedException e) {}

                        String defaultOutput = baFeature.getStatusString().concat(nickFeature.getStatusString())
                                .concat(ciFeature.getStatusString());
                        EmbedDesign requestedType;

                        if (!defaultOutput.contains("true")) {
                            defaultOutput = defaultOutput.replaceAll("false", "Offline");
                            requestedType = EmbedDesign.ERROR;
                        }
                        else if (defaultOutput.contains("false") || defaultOutput.contains(":warning:") ||
                                defaultOutput.contains(":x:") || defaultOutput.contains("Unknown")) {
                            defaultOutput = defaultOutput.replaceAll("false", ":warning: Suspended :warning:");
                            defaultOutput = defaultOutput.replaceAll("true", "Operational");
                            requestedType = EmbedDesign.WARNING;
                        }
                        else {
                            defaultOutput = defaultOutput.replaceAll("true", "Operational");
                            requestedType = EmbedDesign.SUCCESS;
                        }
                        embed.editEmbed(msg, null, defaultOutput, requestedType);
                    }
                    else {
                        log.error(msg.getAuthor().getAsTag() + " just requested my status but did not have permission to");
                        embed.setAsError("No Permissions", ":x: **You Do Not Have Permissions to View My Status**");
                        embed.sendToMemberOutput(msg, msg.getAuthor());
                    }
                    break;
                case "set":
                    if (isStaffMember(msg.getAuthor().getIdLong())) {
                        if (msg.getChannel().asTextChannel().equals(mainConfig.discussionChannel)
                                || msg.getChannel().asTextChannel().equals(mainConfig.managementChannel)) {
                            configCommand(msg);
                        }
                        else {
                            msg.delete().queue();
                            log.warn("Message Deleted - set command");
                            embed.setAsError("Not Usable in This Channel",
                                    "**:x: This Command Is Not Usable in <#" + msg.getChannel().getIdLong() + ">!**" +
                                            "\n\nPlease use `" + mainConfig.commandPrefix + "set` in this channel or in " + mainConfig.managementChannel.getAsMention());
                            embed.sendToTeamOutput(msg, msg.getAuthor());
                        }
                    }
                    else {
                        embed.setAsError("No Permissions", ":x: **You Do Not Have Permissions To Do That!**");
                        embed.sendToTeamOutput(msg, msg.getAuthor());
                    }
                    break;
                case "help":
                    if (args.length == 2 && baFeature.isCommand(args[1])) {
                        baFeature.helpCommand(msg, isTeamMember(msg.getAuthor().getIdLong()));
                    }
                    else if (args.length == 3 && nickFeature.isCommand(args[1], args[2])) {
                        nickFeature.helpCommand(msg, isTeamMember(msg.getAuthor().getIdLong()));
                    }
                    else if (isValidCommand(msg)) helpCommand(msg);
                    else {
                        embed.setAsError("Invalid Command", ":x: **The Command you asked for help for does not exist anywhere within me...**");
                        embed.sendToChannel(msg, msg.getChannel().asTextChannel());
                    }
                    break;
            }
        }
        if (!msg.getChannelType().equals(ChannelType.PRIVATE) && !msg.getMentions().getMembers().contains(guild.getSelfMember())
                && !msg.getChannel().asTextChannel().equals(mainConfig.botSpamChannel) && !msg.getChannel().asTextChannel().equals(mainConfig.managementChannel)
                && msg.getContentRaw().charAt(0) == mainConfig.commandPrefix && msg.getAttachments().isEmpty()
                && mainConfig.deleteOriginalNonStaffCommands && !playerListMain.isValidCommand(args[0])) {
            msg.delete().queue();
            log.warn("Message Deleted - Channel Type Not Private, Does Not Mention Me, Not Bot Spam Channel, " +
                    "Not Management Channel, Prefix Found, Attachments Empty, Delete Original Staff Commands True");
        }
        else if (!msg.getChannelType().equals(ChannelType.PRIVATE) && !msg.getMentions().getMembers().contains(guild.getSelfMember()) &&
                isTeamMember(msg.getAuthor().getIdLong()) && isValidCommand(msg) &&
                (!msg.getChannel().asTextChannel().equals(mainConfig.discussionChannel) || mainConfig.deleteOriginalStaffCommands) &&
                !msg.getChannel().asTextChannel().equals(mainConfig.managementChannel) && !msg.getChannel().asTextChannel().equals(ciFeature.getConfig().getCheckInChannel()) && msg.getAttachments().isEmpty()
        && !playerListMain.isValidCommand(args[0])) {
            msg.delete().queue();
            log.warn("Message Deleted - Channel Type Not Private, Does Not Mention Me, Not Bot Spam Channel, Is Team Member, Is Valid Command " +
                    "Discussion Channel or Delete Original Staff Commands True, Not Management Channel, Not Check-In Channel, Attachments Empty");
        }
    }
    private void helpCommand(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        if (args.length == 1) {
            embed.setAsHelp("About " + mainConfig.commandPrefix + "help",
                    "Syntax: `" + mainConfig.commandPrefix + "help <Command Name>`");
        }
        else if (args.length == 2) {
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
                case "s":
                case "search":
                    if (isTeamMember(msg.getAuthor().getIdLong())) {
                        embed.setAsHelp(mainConfig.commandPrefix + "search Command",
                                "*This Allows Us to get mentions more easily without the need to switch channels*" +
                                        "\nThis searches effective names followed by user names. " +
                                        "You may also enter in a full user tag in and I'll get the mention for this player." +
                                        "\n\nSyntax: `" + mainConfig.commandPrefix + "search <Name>` or `/s <Name>`");
                    }
                    else {
                        embed.setAsError("No Permissions", ":x: **You Do Not Have Permissions To See This Information**");
                    }
                    break;
            }
        }
        if (isTeamMember(msg.getAuthor().getIdLong())) embed.sendToTeamOutput(msg, msg.getAuthor());
        else embed.sendToChannel(msg, msg.getChannel().asTextChannel());
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
                "\n**Syntax**: syn";
        if (isStaffMember(msg.getAuthor().getIdLong())) {
            // /set role <Config Key> <Mentioned Role>
            if (args[1].equalsIgnoreCase("role") && args.length == 4) {
                String roleSyntax = "`" + mainConfig.commandPrefix + "set role <\"adminRole\", \"staffRole\", \"teamRole\", or \"botAbuseRole\"> " +
                        "<Role Mention or ID>`";
                try {
                    if (mainConfig.isValidConfig(args[2])) {
                        mainConfig.setRoleConfig(args[2], Long.parseLong(args[3]));
                        embed.setAsSuccess(defaultTitle,
                                defaultOutput.replace("key", args[2])
                                        .replace("value", guild.getRoleById(args[3]).getAsMention()));
                        successfulRun = true;

                    }
                    else if (baFeature.getConfig().isValidConfig(args[2])) {
                        if (baFeature.getConfig().setNewBotAbuseRole(Long.parseLong(args[3]))) {
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
                    if (!msg.getMentions().getRoles().isEmpty()) {
                        if (msg.getMentions().getRoles().size() == 1) {
                            if (mainConfig.isValidConfig(args[2])) {
                                mainConfig.setRoleConfig(args[2], msg.getMentions().getRoles().get(0));
                                embed.setAsSuccess(defaultTitle,
                                        defaultOutput.replace("key", args[2])
                                                .replace("value", msg.getMentions().getRoles().get(0).getAsMention()));
                                successfulRun = true;
                            }
                            else if (baFeature.getConfig().isValidConfig(args[2])) {
                                baFeature.getConfig().setNewBotAbuseRole(msg.getMentions().getRoles().get(0));
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
                String channelSyntax = "`" + mainConfig.commandPrefix + "set channel <\"helpChannel\", \"botSpamChannel\", \"theLightAngelChannel\", " +
                        "\"teamDiscussionChannel\", \"botManagementChannel\", or \"logChannel\" > " +
                        "<Channel Mention or ID>`";
                try {
                    mainConfig.setChannelConfig(args[2], Long.parseLong(args[3]));
                    embed.setAsSuccess(defaultTitle,
                            defaultOutput.replace("key", args[2])
                                    .replace("value", guild.getTextChannelById(args[3]).getAsMention()));
                    successfulRun = true;
                }
                catch (NumberFormatException ex) {
                    if (!msg.getMentions().getChannels(TextChannel.class).isEmpty()) {
                        if (mainConfig.isValidConfig(args[2])) {
                            if (msg.getMentions().getChannels(TextChannel.class).size() == 1) {
                                mainConfig.setChannelConfig(args[2], msg.getMentions().getChannels(TextChannel.class).get(0));
                                embed.setAsSuccess(defaultTitle,
                                        defaultOutput.replace("key", args[2])
                                                .replace("value", msg.getMentions().getChannels(TextChannel.class).get(0).getAsMention()));
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
                    String integerDefaultSyntax = "`" + mainConfig.commandPrefix + "set config` " +
                            "\n**2nd Argument Options:**" +
                            "\n\n*__Main Configuration__*" +
                            "\n`<\"pingCooldown\" (\"pingCD\" for short), or \"highPingTime\" (\"highPing\" for short)>` " +
                            "\n\n*__Bot Abuse Configuration__*" +
                            "\n`<\"roleScannerInterval\" (\"roleScanInt\" for short), \"hotOffenseMonths\" (\"hotMonths\" for short), " +
                            "\"maxDaysAllowedForUndo\" (\"maxDaysUndo\" for short), or \"hotOffenseWarning\" (\"hotWarning\" for short)>`" +
                            "\n\n*__Nickname Configuration__*" +
                            "\n`<\"requestCooldown\" (or \"requestCD\" or \"reqCD\" for short)>`" +
                            "\n\nFollowed by: `<New Integer Value>`";
                    // Handling for maxDaysAllowedForUndo
                    if (args[2].equalsIgnoreCase("maxDaysUndo") ||
                            args[2].equalsIgnoreCase("maxDaysAllowedForUndo")) {
                        try {
                            if (baFeature.setNewMaxDaysAllowedForUndo(Integer.parseInt(args[3]))) {
                                embed.setAsSuccess(defaultTitle,
                                        defaultOutput.replace("key", args[2]).replace("value", args[3]));
                                successfulRun = true;
                            }
                            else {
                                embed.setAsError("Cannot Update " + args[2],
                                        "**There was an error while trying to update " + args[2] + "**" +
                                                "\n\n**This value cannot exceed the time length of a Bot Abuse.** " +
                                                "This is so that when a player has already served their time being Bot Abused, " +
                                                "it is too late to undo it." +
                                                "\n\n*Example: If the first offense is a 3 day Bot Abuse, " +
                                                "you cannot set this value equal to or more than that, the only legal values would be 1 or 2 days.*");
                            }
                        }
                        catch (NumberFormatException ex) {
                            embed.setAsError(defaultErrorTitle,
                                    defaultErrorOutput.replace("key", args[2]).replace("syn", integerDefaultSyntax));
                        }
                    }
                    else if (args[2].equalsIgnoreCase("hotOffenseWarning") || args[2].equalsIgnoreCase("hotWarning")) {
                        try {
                            if (baFeature.setNewHotOffenseWarning(Integer.parseInt(args[3]))) {
                                embed.setAsSuccess(defaultTitle,
                                        defaultOutput.replace("key", args[2]).replace("value", args[3]));
                                successfulRun = true;
                            }
                            else {
                                embed.setAsError("Cannot Update " + args[2],
                                        "**The New " + args[2] + " value cannot exceed the Number of Temporary " +
                                                "Bot Abuses Someone Gets Before the Permanent one.**" +
                                                "\n\n*Example: If there's 4 Temporary Bot Abuses before the Permanent, " +
                                                "this value cannot exceed 4*");
                            }
                        }
                        catch (NumberFormatException ex) {
                            embed.setAsError(defaultErrorTitle,
                                    defaultErrorOutput.replace("key", args[2]).replace("syn", integerDefaultSyntax));
                        }
                    }
                    else if (mainConfig.isValidConfig(args[2])) {
                        mainConfig.setConfig(args[2], Integer.parseInt(args[3]));
                        embed.setAsSuccess(defaultTitle,
                                defaultOutput.replace("key", args[2]).replace("value", args[3]));
                        successfulRun = true;
                    }
                    else if (baFeature.getConfig().isValidConfig(args[2])) {
                        baFeature.getConfig().setConfig(args[2], Integer.parseInt(args[3]));
                        embed.setAsSuccess(defaultTitle, defaultOutput.replace("key", args[2]).replace("value", args[3]));
                        successfulRun = true;
                    }
                    else if (nickFeature.getConfig().isValidConfig(args[2])) {
                        nickFeature.getConfig().setConfig(args[2], Integer.parseInt(args[3]));
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
                        else if (nickFeature.getConfig().isValidConfig(args[2])) {
                            nickFeature.getConfig().setConfig(args[2], Boolean.valueOf(args[3]));
                            embed.setAsSuccess(defaultTitle,
                                    defaultOutput.replace("key", args[2]).replace("value", args[3]));
                            successfulRun = true;
                        }
                        else if (baFeature.getConfig().isValidConfig(args[2])) {
                            baFeature.getConfig().setConfig(args[2], Boolean.valueOf(args[3]));
                            embed.setAsSuccess(defaultTitle,
                                    defaultOutput.replace("key", args[2]).replace("value", args[3]));
                            successfulRun = true;
                        }
                        else {
                            embed.setAsError(defaultErrorTitle, defaultErrorOutput.replace("key", args[2]).replace("syn",
                                    "`" + mainConfig.commandPrefix + "set config` " +
                                            "\n**2nd Argument Options:**" +
                                            "\n\n*__Main Configuration__*" +
                                            "\n`<\"deleteOriginalNonStaffCommands\" (\"delNonStaffCmd\" for short), " +
                                            "\"deleteOriginalStaffCommands\" (\"delStaffCmd\" for short), or " +
                                            "\"forceToLightAngelChannel\" (\"forceToLA\" for short)>`" +
                                            "\n\n*__Bot Abuse Configuration__*" +
                                            "\n`<\"autoPermanent\" (\"autoPerm\" for short)>`" +
                                            "\n\n*__Nickname Configuration__*" +
                                            "\n`<\"pingStaffOnlineOnRequest\" (or \"pingStaffOnline\" for short)>" +
                                            "\n\nFollowed By: <New Boolean Value>`"));
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
                                    "`" + mainConfig.commandPrefix + "set config <\"timeZone\", \"checkIconURL\", \"errorIconURL\", " +
                                            "\"infoIconURL\", \"stopIconURL\", \"helpIconURL\", \"blobNomPingID\", " +
                                            "\"commandPrefix\" (\"cmdPrefix\" for short), \"fieldHeader\", " +
                                            "or \"botabuselength\" (to add or remove times to the bot abuse punishments)>" +
                                            "<New String Value>`"));
                        }
                    }
                }
            }
            // /set config botabuselength <add/del/remove> <# of Days>
            else if (args[1].equalsIgnoreCase("config") && args[2].equalsIgnoreCase("botabuselength")
                    && args.length == 5) {
                String defaultBASuccessTitle = "Time ? Successfully";
                String defaultSyntax = mainConfig.commandPrefix + "set config botabuselength <add/del/remove> <# of Days>";
                String result = "";
                try {
                    if (args[3].equalsIgnoreCase("add")) {
                        result = baFeature.getConfig().addExpiryTime(Integer.parseInt(args[4]));
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
                        result = baFeature.getConfig().removeExpiryTime(Integer.parseInt(args[4]), false);
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
            // /set role nameRestrictedRoles add/del/remove <Role Mention or ID>
            else if (args[1].equalsIgnoreCase("role") &&
                    (args[2].equalsIgnoreCase("nameRestrictedRoles") || args[2].equalsIgnoreCase("nameRoles"))
                    && args.length == 5) {
                String defaultSuccessTitle = "Name Restricted Role ?";
                String defaultSyntax = "`" + mainConfig.commandPrefix + "set config nameRoles/nameRestrictedRoles add/del/remove <Role Mention or ID>`";
                String defaultSuccess = "Successfully ? ! The Roles Not Allowed to Change Names";

                if (args[3].equalsIgnoreCase("add")) {
                    try {
                        nickFeature.getConfig().addNewNameRestrictedRole(Long.parseLong(args[4]));
                        embed.setAsSuccess(defaultSuccessTitle.replace("?", "Added"),
                                defaultSuccess.replace("?", "Added")
                            .replace("!", guild.getRoleById(Long.parseLong(args[4])).getAsMention() + " To"));
                        log.info(msg.getMember().getEffectiveName() + " Successfully Added " + guild.getRoleById(Long.parseLong(args[4])).getName() +
                                " to the name restricted roles list");
                        successfulRun = true;
                        nameRestrictedRoleUpdated = true;

                    }
                    catch (NumberFormatException ex) {
                        try {
                            nickFeature.getConfig().addNewNameRestrictedRole(msg.getMentions().getRoles().get(0));
                            embed.setAsSuccess(defaultSuccessTitle.replace("?", "Added"),
                                    defaultSuccess.replace("?", "Added")
                                    .replace("!", msg.getMentions().getRoles().get(0).getAsMention() + " To"));
                            log.info(msg.getMember().getEffectiveName() + " Successfully Added " + msg.getMentions().getRoles().get(0).getName() +
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
                        if (nickFeature.getConfig().removeNewNameRestrictedRole(Long.parseLong(args[4]))) {
                            embed.setAsSuccess(defaultSuccessTitle.replace("?", "Removed"),
                                    defaultSuccess.replace("?", "Removed")
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
                            if (nickFeature.getConfig().removeNewNameRestrictedRole(msg.getMentions().getRoles().get(0))) {
                                embed.setAsSuccess(defaultSuccessTitle.replace("?", "Removed"),
                                        defaultSuccess.replace("?", "Removed")
                                        .replace("!", msg.getMentions().getRoles().get(0).getAsMention() + " From"));
                                log.info(msg.getMember().getEffectiveName() + " Successfully Added " + msg.getMentions().getRoles().get(0).getName() +
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
            else {
                embed.setAsError("Syntax Error", ":x: **I was expecting 4 or 5 arguments in this command...**");
                embed.sendToChannel(msg, msg.getChannel().asTextChannel());
                return;
            }
            embed.sendToChannel(msg, msg.getChannel().asTextChannel());
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
            if (isTeamMember(msg.getAuthor().getIdLong())) embed.sendToTeamOutput(msg, msg.getAuthor());
            else embed.sendToMemberOutput(msg, msg.getAuthor());
        }
    }
    private void searchCommand(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        String title = "Search Results";
        String results = "";
        EmbedDesign embedType = EmbedDesign.INFO;
        if (args.length >= 2) {
            String query = "";
            if (args.length == 2) {
                query = args[1];
            }
            else {
                int index = 1;
                do {
                    query = query.concat(args[index++]);
                    if (index < args.length) query = query.concat(" ");
                } while (index < args.length);
            }
            List<Member> searchResults = new ArrayList<>();
            List<Member> effectiveNameSearch = guild.getMembersByEffectiveName(query, false);
            List<Member> memberByNameSearch = guild.getMembersByName(query, false);
            Member memberByTagSearch = null;
            try {
                memberByTagSearch = guild.getMemberByTag(query);
            }
            catch (IllegalArgumentException ex) {
                log.warn("Could Not Find \"" + args[1] + "\" by tag.");
            }
            if (effectiveNameSearch.size() > 0) {
                searchResults.addAll(effectiveNameSearch);
            }
            if (memberByNameSearch.size() > 0) {
                memberByNameSearch.forEach(m -> {
                    if (!searchResults.contains(m)) {
                        searchResults.add(m);
                    }
                });
            }
            if (memberByTagSearch != null && !searchResults.contains(memberByTagSearch)) {
                searchResults.add(memberByTagSearch);
            }
            // The Methods Above are reliant on the search being exact
            final String searchQuery = query;
            guild.loadMembers(m -> {
                // We use toLowerCase so that the search results are not case sensitive
                if (m.getEffectiveName().toLowerCase().contains(searchQuery.toLowerCase()) ||
                        m.getUser().getName().toLowerCase().contains(searchQuery.toLowerCase())) {
                    if (!searchResults.contains(m)) searchResults.add(m);
                }
            });

            results = "**Your Search Yielded the Following Results:** \n";
            int index = 0;
            if (searchResults.isEmpty()) {
                title = "No Results Found";
                results = "**:x: No Results found with that search query...**";
                embedType = EmbedDesign.ERROR;
                log.error("No Results returned with the search query \"" + query + "\"");
            }
            else {
                do {
                    results = results.concat("- " + searchResults.get(index++).getAsMention() + "\n");
                } while (index < searchResults.size());
                results = results.concat("\n*You may use these results to get a mention you need in your next command " +
                        "or for any other purpose. Right click on the desired mention and click **Mention***");
                log.info("Search Query \"" + query + "\" returned " + searchResults.size() + " result(s)");
                embedType = EmbedDesign.INFO;
            }
        }
        else {
            title = "Error While Parsing Command";
            results = "**Invalid Number of Arguments! Syntax: `" + mainConfig.commandPrefix + "search <Name>`**";
            embedType = EmbedDesign.ERROR;
        }

        MessageEntry entry = new MessageEntry(title, results, embedType).setOriginalCmd(msg);

        if (ciFeature.isCheckInRunning() && msg.getChannel() == ciFeature.getCheckInManagementEmbedChannel()) {
            entry.setCustomChannels(ciFeature.getCheckInManagementEmbedChannel());
        }
        else {
            entry.setChannels(TargetChannelSet.TEAM);
        }
        embed.sendAsMessageEntryObj(entry);

        while (true) {
            try {
                ciFeature.addSearchCommands(embed.getMessageEntryObj(msg));
                break;
            }
            catch (NullPointerException ex) {}
        }
    }

    // Specifically for Embeds That Edit Themselves Based on Reactions
    public void addAsReactionListEmbed(ListEmbed listEmbed) {
        MessageEntry initialEntry = listEmbed.getMessageEntry();
        if (listEmbed.getTotalPages() > 1) {
            initialEntry.setTitle(listEmbed.getMessageEntry().getTitle().concat(" - Page 1/" + listEmbed.getTotalPages()));
        }
        embed.sendAsMessageEntryObj(initialEntry.setTargetUser(initialEntry.getOriginalCmd().getAuthor()));
        MessageEntry changingEmbed = null;
        while (changingEmbed == null || changingEmbed.getResultEmbed() == null || !changingEmbed.isListEmbed()) {
            try {
                changingEmbed = embed.getMessageEntryObj(listEmbed.getMessageEntry().getOriginalCmd());
            }
            catch (NullPointerException ex) {}
        }

        listEmbeds.add(listEmbed);

        addCustomEmotes(changingEmbed.getResultEmbed());

        processPagesOnListEmbed(changingEmbed, listEmbed.getTotalPages());
    }

    public void addAsReactionListEmbed(CustomListEmbed customListEmbed) {
        addAsReactionListEmbed(customListEmbed.getListEmbed());
    }
    public void updateReactionListEmbed(ListEmbed listEmbed, List<String> newAlternatingStrings) {
        ListEmbed oldListEmbed = listEmbeds.remove(listEmbeds.indexOf(listEmbed));

        ListEmbed newListEmbed = oldListEmbed.setNewPages(newAlternatingStrings);

        listEmbeds.add(newListEmbed);
        newListEmbed.getMessageEntry().setMessage(newListEmbed.getCurrentPage());
        if (newListEmbed.getTotalPages() != oldListEmbed.getTotalPages()) {
            newListEmbed.getMessageEntry().setTitle(newListEmbed.getMessageEntry().getTitle().split(" - Page")[0] +
                    newListEmbed.getCurrentPage() + "/" + newListEmbed.getTotalPages());
        }
        newListEmbed.getMessageEntry().getResultEmbed().editMessageEmbeds(newListEmbed.getMessageEntry().getEmbed()).queue();

        processPagesOnListEmbed(newListEmbed.getMessageEntry(), listEmbed.getTotalPages());
    }
    public void updateReactionListEmbed(CustomListEmbed customListEmbed, List<String> newAlternatingStrings) {
        updateReactionListEmbed(customListEmbed.getListEmbed(), newAlternatingStrings);
    }
    private void processPagesOnListEmbed(MessageEntry changingEmbed, int newPageCount) {
        switch (newPageCount) {
            case 0:
            case 1: break;
            case 2:
                // Previous Page
                changingEmbed.getResultEmbed().addReaction(Emoji.fromUnicode("\u2B05\uFE0F")).queue();
                // Stop Button
                changingEmbed.getResultEmbed().addReaction(Emoji.fromUnicode("\u23F9\uFE0F")).queue();
                // Next Page
                changingEmbed.getResultEmbed().addReaction(Emoji.fromUnicode("\u27A1\uFE0F")).queue();
                break;
            default:
                // First Page
                changingEmbed.getResultEmbed().addReaction(Emoji.fromUnicode("\u23EE\uFE0F")).queue();
                // Previous Page
                changingEmbed.getResultEmbed().addReaction(Emoji.fromUnicode("\u2B05\uFE0F")).queue();
                // Stop Button
                changingEmbed.getResultEmbed().addReaction(Emoji.fromUnicode("\u23F9\uFE0F")).queue();
                // Next Page
                changingEmbed.getResultEmbed().addReaction(Emoji.fromUnicode("\u27A1\uFE0F")).queue();
                // Last Page
                changingEmbed.getResultEmbed().addReaction(Emoji.fromUnicode("\u23ED\uFE0F")).queue();
        }
        reactionClearTimers.put(changingEmbed.getResultEmbed(),
                changingEmbed.getResultEmbed().clearReactions().queueAfter(30, TimeUnit.MINUTES, success -> {}, error -> {
                    log.warn("Unable to Clear Reactions from MessageEntry Object Timer - " + error.getMessage());
                }));
    }
    // This is used when we want to update prefix or suffixes of the list embed objects
    public void replaceListEmbedObjects(ListEmbed oldListEmbed, ListEmbed newListEmbed) {
        listEmbeds.set(listEmbeds.indexOf(oldListEmbed), newListEmbed);

        oldListEmbed.getMessageEntry().getResultEmbed().editMessage(newListEmbed.getCurrentPage()).queue();
    }
    private void addCustomEmotes(Message listEmbedMsg) {
        ListEmbed listEmbed = getListEmbedFromMsg(listEmbedMsg);
        if (listEmbed.isCustomEmbed()) {
            int index = 0;
            while (index < listEmbed.getCustomListEmbed().getEmoteUnicodeToReactOn().size()) {
                listEmbed.getMessageEntry().getResultEmbed().addReaction(
                        Emoji.fromUnicode(listEmbed.getCustomListEmbed().getEmoteUnicodeToReactOn().get(index++))).queue();
            }
        }
    }

    private ListEmbed getListEmbedFromMsg(Message listEmbedMsg) {
        int index = 0;
        while (index < listEmbeds.size()) {
            if (listEmbeds.get(index).getMessageEntry().getResultEmbed().getIdLong() == listEmbedMsg.getIdLong()) {
                return listEmbeds.get(index);
            }
            index++;
        }
        return null;
    }
    ///////////////
    public void restartBot(boolean restartSilently) throws IOException {
        log.warn("Program Restarting...");
        String suffix = "true";
        if (restartSilently) {
            suffix = "-s";
        }
        new ProcessBuilder("cmd", "/c", "start", "/MIN", "java", "-jar", "-Dlog4j.configurationFile=./log4j2.properties", "TheLightAngel.jar", suffix).start();
        System.exit(1);
    }

    // Is the command array provided a valid command anywhere in the program?
    private boolean isValidCommand(Message msg) {
        if (msg.getContentRaw().charAt(0) != mainConfig.commandPrefix) return false;
        String[] cmd = msg.getContentRaw().substring(1).split(" ");
        if (baFeature.isCommand(cmd[0])) return true;
        if (playerListMain.isValidCommand(cmd[0])) return true;
        if (cmd.length > 1 ) {
            if (nickFeature.isCommand(cmd[0], cmd[1])) return true;
            if (ciFeature.isCommand(cmd[0], cmd[1])) return true;
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

    private long getGatewayNetPing() {
        String reader = "";
        long returnValue = 0;
        try {
            Process p = Runtime.getRuntime().exec("ping gateway.discord.gg -n 10");
            BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));

            int index = 0;
            while ((reader = inputStream.readLine()) != null) {
                if (reader.contains("Average =")) {
                    returnValue = Long.parseLong(reader.substring(reader.lastIndexOf("=") + 2).split("m")[0]);
                    log.info("Network Ping Time to Discord's Gateway: " + returnValue + "ms");
                }
                else {
                    try {
                        log.info("Packet " + ++index + ": " + reader.split("time=")[1].substring(0, 4));
                    }
                    catch (ArrayIndexOutOfBoundsException ex) {
                        index--;
                    }
                }
            }
            inputStream.close();
            return returnValue;
        }
        catch (IOException ex) {
            log.error("Caught IOException - Returning 0ms", ex);
            return 0;
        }
    }

    // This method handles anytime an integrity check fails within one of the features
    public void failedIntegrityCheck(String obj, Message msg, String cause) {
        embed.setAsStop("FATAL ERROR",
                "**Ouch! That Really Didn't Go Well!**" +
                        "\n**You may use *" + mainConfig.commandPrefix + "restart* to try to restart me. " +
                        "If you don't feel comfortable doing that... " + mainConfig.owner.getAsMention()
                        + " has been notified.**" +
                        "\n\n**Cause: " + cause + "**" +
                        "\n\n** " + obj + " Commands have Been Suspended**");
        embed.sendToTeamOutput(msg, msg.getAuthor());
        log.fatal("Integrity Check on ArrayList Objects Failed - Cause: " + cause);
        if (obj.contains("BotAbuseMain")) {
            baFeature.commandsSuspended = true;
            baFeature.stopTimers();
        }
        else if (obj.contains("NicknameMain")) {
            nickFeature.commandsSuspended = true;
        }
        else commandsSuspended = true;
        return;
    }
}