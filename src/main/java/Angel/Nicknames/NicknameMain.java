package Angel.Nicknames;

import Angel.*;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.TargetType;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateGlobalNameEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public class NicknameMain extends ListenerAdapter implements NickConfig {
    private final Logger log = LogManager.getLogger(NicknameMain.class);
    private Guild guild;
    private DiscordBotMain discord;
    private EmbedEngine embed;
    private NicknameCore nickCore;
    private Help help;
    private ArrayList<Long> tempDiscordID = new ArrayList<>();
    private ArrayList<String> tempOldNick = new ArrayList<>();
    private ArrayList<String> tempNewNick = new ArrayList<>();
    public final List<String> commands = new ArrayList<>(Arrays.asList("nickname", "nn"));
    private final List<String> nickArgs = new ArrayList<>(
            Arrays.asList("request", "req", "withdraw", "wd", "accept", "acc", "a", "deny", "d",
            "history", "h", "forcechange", "fch", "clearhistory", "clh", "list"));
    public boolean commandsSuspended = false;
    public boolean isConnected = false;
    public boolean isBusy = false;
    public User commandUser;
    private List<Long> requestCooldownDiscordIDs = new ArrayList<>();
    private List<ZonedDateTime> requestCooldownDates = new ArrayList<>();
    private ZonedDateTime c;
    private Timer timer = new Timer();

    NicknameMain(boolean getCommandsSuspended, EmbedEngine importEmbed, DiscordBotMain importDiscordBot) {
        this.embed = importEmbed;
        this.guild = getGuild();
        this.discord = importDiscordBot;
        commandsSuspended = getCommandsSuspended;
        try {
            this.nickCore = new NicknameCore();
            help = new Help(embed, this);
            if (nickConfig.isEnabled()) {
                nickConfig.setup();
                nickCore.startup();
            }
            else {
                log.warn("Nickname side of the bot is Disabled");
            }
        }
        catch (FileNotFoundException e) {
            log.error("Nickname Constructor", e);
        }
        catch (IOException e) {
            log.error("Nickname Constructor", e);
        }
        if (!commandsSuspended && nickConfig.isEnabled()) {
            setupRestrictedRoles(false);
            init();
        }
    }
    @Override
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        isConnected = false;
    }

    @Override
    public void onSessionResume(SessionResumeEvent event) {
        isConnected = true;
    }

    @Override
    public void onReady(ReadyEvent event) {
        isConnected = true;
    }
    @Override
    public void onUserUpdateName(UserUpdateNameEvent event) {
        Thread.currentThread().setUncaughtExceptionHandler(aue);
        if (!nickConfig.isEnabled()) return;
        isBusy = true;

        guild.retrieveMemberById(event.getUser().getIdLong()).submit().whenComplete(new BiConsumer<Member, Throwable>() {
            @Override
            public void accept(Member m, Throwable throwable) {
                if (throwable == null) {
                    // The Player Has No Nickname and No Global Name and changes username and is not allowed to change effective name
                    if (m.getNickname() == null && event.getUser().getGlobalName() == null && inNickRestrictedRole(m)) {
                        m.modifyNickname(event.getOldName()).reason(
                                "Automatically Added because this player was previously using their discord username as their social club name").queue();
                        embed.setAsInfo("Automatic Nickname Addition",
                                "**Your name on the SAFE Crew discord server was automatically set back to your old discord username**" +
                                        " \n\nThis is due to the fact that you're in a role that prohibits name changes, your name in the SAFE Crew discord server" +
                                        " must match your social club profile name. Don't worry or panic, " +
                                        "this was just a message to say that a nickname was applied in our server so that your " +
                                        "displayed name continues to match your social club name. No action is required." +
                                        "\n\n**If you believe this is in error, you may reply with `" + mainConfig.commandPrefix + "nickname request reset` " +
                                        "(or `" + mainConfig.commandPrefix + "nn req reset` for short) to request the nickname that was just applied to be removed**");
                        embed.sendDM(null, event.getUser());
                        String logMessage = event.getUser().getAsMention() + " was previously using their discord username " +
                                "as their social club name, they just changed it. They are in a role that prevents effective name changes so a nickname of **" + event.getOldName() + "** was set on them.";
                        log.info(logMessage.replace(event.getUser().getAsMention(), event.getUser().getName() + " (ID:" + event.getUser().getIdLong() + ")")
                                .replace("**" + event.getOldName() + "**", event.getOldName()));
                        embed.setAsInfo("Automatic Nickname Applied",
                                logMessage.replace("null", event.getUser().getName() + " (Their Discord Username)"));
                        embed.sendToLogChannel();
                    }
                    // The Player Has No Nickname and No Global Name and changes to username and is not allowed to change effective name
                    else if (m.getNickname() == null && !inNickRestrictedRole(m)) {

                        String defaultMessage = event.getUser().getName() + " updated their discord username from "
                                + event.getOldName() + " to " + event.getNewName() + " and they had no nickname to prevent the effective name change";
                        String defaultDiscordMessage = event.getUser().getAsMention() + " updated their discord username from **"
                                + event.getOldName() + "** to **" + event.getNewName() + "**.";

                        if (event.getUser().getGlobalName() == null) {
                            defaultDiscordMessage = defaultDiscordMessage.concat("\n\nThis got logged by me as *they had no nickname, so their effective name changed as a result of this action*");
                            addNameHistory(event.getUser().getIdLong(), event.getOldName(), null);
                        }
                        else {
                            defaultDiscordMessage = defaultDiscordMessage.concat("\n\nTheir effective name *did not change because they do have a global display name of **"
                                    + event.getUser().getGlobalName() + "***");
                        }

                        log.info(defaultMessage);
                        embed.setAsInfo("Discord Username Changed",
                                defaultDiscordMessage.replace("null",
                                        event.getUser().getName() + " (Their Discord Username)"));
                        embed.sendToLogChannel();
                    }
                }
                else {
                    // Take No Action - This Exception indicates either this user is not a member of the guild or does not exist.
                    log.warn("Whoops... looks like this discord user is not in the guild or the user does not exist: " + throwable.getMessage());
                    aue.logCaughtException(Thread.currentThread(), throwable);
                }
            }
        });
        isBusy = false;
    }

    @Override
    public void onUserUpdateGlobalName(UserUpdateGlobalNameEvent event) {
        Thread.currentThread().setUncaughtExceptionHandler(aue);
        if (!nickConfig.isEnabled()) return;
        isBusy = true;

        guild.retrieveMemberById(event.getUser().getIdLong()).useCache(false).submit().whenComplete(new BiConsumer<Member, Throwable>() {
            @Override
            public void accept(Member member, Throwable throwable) {
                MessageEntry entry;
                if (throwable == null) {
                    // The player did not previously have a global name and now does.
                    if (event.getOldGlobalName() == null && event.getNewGlobalName() != null && member.getNickname() == null && inNickRestrictedRole(member)) {
                        member.modifyNickname(event.getUser().getName())
                                .reason("Automatically Added because this player was previously using their discord username as their social club name").queue();
                        entry = new MessageEntry("Automatic Nickname Addition",
                                "**Your name on the SAFE Crew discord server was automatically set back to your old discord username**" +
                                        " \n\nThis is due to the fact that you're in a role that prohibits name changes, your name in the SAFE Crew discord server" +
                                        " must match your social club profile name. Don't worry or panic, " +
                                        "this was just a message to say that a nickname was applied in our server so that your " +
                                        "displayed name continues to match your social club name. No action is required." +
                                        "\n\n**If you believe this is in error, you may reply with `" + mainConfig.commandPrefix + "nickname request reset` " +
                                        "(or `" + mainConfig.commandPrefix + "nn req reset` for short) to request the nickname that was just applied to be removed**",
                                EmbedDesign.INFO).setTargetUser(event.getUser()).setChannels(TargetChannelSet.DM);
                        embed.sendAsMessageEntryObj(entry);

                        String logMessage = event.getUser().getAsMention() + " was previously using their discord username " +
                                "as their social club name, they just changed it. They are in a role that prevents effective name changes so a nickname of **" + event.getUser().getName() + "** was set on them.";
                        log.info(logMessage.replace(event.getUser().getAsMention(), event.getUser().getName() + " (ID:" + event.getUser().getIdLong() + ")")
                                .replace("**" + event.getUser().getName() + "**", event.getUser().getName()));
                        embed.setAsInfo("Automatic Nickname Applied",
                                logMessage.replace("null", event.getUser().getName() + " (Their Discord Username)"));
                        embed.sendToLogChannel();
                    }
                    // The player does have a global nickname and is using it as their social club name
                    else if (event.getOldGlobalName() != null && member.getNickname() == null && inNickRestrictedRole(member)) {
                        member.modifyNickname(event.getOldGlobalName())
                                .reason("Automatically Added because this player was previously using their global display name as thier social club name").queue();
                        entry = new MessageEntry("Automatic Nickname Addition",
                                "**Your name on the SAFE Crew discord server was automatically rolled back to your old global name**" +
                                        "\n\nThis is due to the fact that you're in a role that prohibits name changes, your name in the SAFE Crew discord server " +
                                        "must match your social club profile name. Don't worry or panic, " +
                                        "this was just a message to say that a nickname was applied in our server so that your " +
                                        "displayed name continues to match your social club name. No action is requred." +
                                        "\n\n**If you believe this is in error, you may reply with `" + mainConfig.commandPrefix + "nickname request reset` " +
                                        "(or `" + mainConfig.commandPrefix + "nn req reset` for short) to request the nickname that was just applied to be removed**",
                                EmbedDesign.INFO).setTargetUser(event.getUser()).setChannels(TargetChannelSet.DM);
                        embed.sendAsMessageEntryObj(entry);

                        String logMessage = event.getUser().getAsMention() + " was previously using their global display name " +
                                "as their social club name, they just changed it. They are in a role that prevents effective name changes so a nickname of **" + event.getOldGlobalName() + "** was set on them.";
                        log.info(logMessage.replace(event.getUser().getAsMention(), event.getUser().getName() + " (ID:" + event.getUser().getIdLong() + ")")
                                .replace("**" + event.getOldGlobalName() + "**", event.getOldGlobalName()));
                        embed.setAsInfo("Automatic Nickname Applied",
                                logMessage.replace("null", event.getUser().getName() + " (Their Discord Username)"));
                        embed.sendToLogChannel();
                    }
                    else if (member.getNickname() == null && !inNickRestrictedRole(member)) {
                        String oldName = "";
                        String newName = "";
                        if (event.getOldGlobalName() == null) {
                            oldName = event.getUser().getName();

                            if (event.getOldGlobalName() == null && event.getUser().getName().equalsIgnoreCase(event.getNewGlobalName())) {
                                oldName = "null";
                            }
                            else {
                                addNameHistory(event.getUser().getIdLong(), oldName, null);
                            }
                        }
                        else {
                            oldName = event.getOldGlobalName();
                            addNameHistory(event.getUser().getIdLong(), oldName, null);
                        }



                        if (event.getNewGlobalName() == null) {
                            newName = event.getUser().getName();
                        }
                        else {
                            newName = event.getNewGlobalName();
                        }


                        String defaultMessage = event.getUser().getAsMention() + " has updated their global display name from **" +
                                oldName + "** to **" + newName + "** and they had no nickname to prevent the effective name change";

                        entry = new MessageEntry("Global Display Name Changed", defaultMessage, EmbedDesign.INFO).setChannels(TargetChannelSet.LOG);
                        embed.sendAsMessageEntryObj(entry);
                        log.info(defaultMessage.replace(event.getUser().getAsMention(),
                                event.getUser().getName() + " (ID: " + event.getUser().getIdLong() + ")")
                                .replace("**" + oldName + "**", oldName).replace("**" + newName + "**", newName));
                    }
                }
                else {
                    log.warn("Whoops... something went wrong in the UserUpdateGlobalNameEvent handler: " + throwable.getMessage());
                    aue.logCaughtException(Thread.currentThread(), throwable);
                }
            }
        });
    }

    @Override
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
        if (!nickConfig.isEnabled()) return;

        guild.retrieveAuditLogs().submit().whenComplete(new BiConsumer<List<AuditLogEntry>, Throwable>() {
            @Override
            public void accept(List<AuditLogEntry> l, Throwable throwable) {
                Thread.currentThread().setUncaughtExceptionHandler(aue);
                if (throwable != null) {
                    log.error("Exception Thrown by retrieving logs on Nickname Update: " + throwable.getMessage());
                    return;
                }
                isBusy = true;
                AuditLogEntry entry = null;
                int index = 0;
                do {
                    if (l.get(index).getTargetType() == TargetType.MEMBER) {
                        if (l.get(index).getTargetIdLong() == event.getUser().getIdLong()) {
                            entry = l.get(index);
                            break;
                        }
                    }

                    if (entry == null && index == l.size()) {
                        index = -1;
                    }
                } while (++index < l.size());

                if (entry.getUser().isBot() || index == l.size()) return;
                else if (isTeamMember(entry.getUser().getIdLong())) {
                    String newNickname = event.getNewNickname();
                    AtomicReference<Member> member = new AtomicReference<>();
                    AtomicReference<Member> memberResponsible = new AtomicReference<>();
                    guild.retrieveMember(entry.getUser()).queue(m -> memberResponsible.set(m));
                    guild.retrieveMember(event.getUser()).queue(m -> member.set(m));
                    if (newNickname == null) {
                        if (member.get().getUser().getGlobalName() == null) {
                            newNickname = member.get().getEffectiveName() + " (Their Discord Username)";
                        }
                        else {
                            newNickname = member.get().getUser().getGlobalName() + " (Their Global Name)";
                        }
                    }
                    addNameHistory(event.getUser().getIdLong(), event.getOldNickname(), null);
                    embed.setAsInfo("Staff Updated Nickname", "**" + entry.getUser().getAsMention() + " successfully changed a nickname via the discord GUI:**" +
                            "\nMember: " + event.getUser().getAsMention() +
                            "\nOld Nickname: **" + event.getOldNickname() +
                            "**\nNew Nickname: **" + newNickname + "**");
                    embed.sendToLogChannel();
                    log.info(memberResponsible.get().getEffectiveName() + " changed " + member.get().getEffectiveName() +
                            "'s nickname from " + event.getOldNickname() + " to " + event.getNewNickname());
                }

                else if (inNickRestrictedRole(event.getUser().getIdLong())) {
                    if (tempDiscordID.contains(event.getUser().getIdLong()) || nickCore.discordID.contains(event.getUser().getIdLong())) {
                        if (tempDiscordID.contains(event.getUser().getIdLong())) {
                            embed.setAsWarning("Instructions Warning",
                                    ":warning: **Please Re-Read the Instructions I just gave you above this message**");
                            embed.sendDM(null, event.getUser());
                            event.getMember().modifyNickname(event.getOldNickname())
                                    .reason(event.getMember().getEffectiveName() + " has tried to change their nickname on their own already...").queue();

                        }
                        else if (nickCore.discordID.contains(event.getUser().getIdLong())) {
                            embed.setAsError("Pending Request Info",
                                    "You already have a pending nickname request");
                            embed.sendDM(null, event.getUser());
                            event.getMember().modifyNickname(event.getOldNickname())
                                    .reason("Already has pending nickname request and again tried to change it on their own...").queue();
                        }
                        else return;
                    }
                    else {
                        tempDiscordID.add(event.getUser().getIdLong());
                        String results = "**You're in a role that prohibits modifying your name**";

                        if (event.getOldNickname() != null && event.getNewNickname() != null) {
                            results = results.concat(
                                    "\n\nOld Nickname: **" + event.getOldNickname() + "**\nNew Nickname: **" + event.getNewNickname() + "**"
                            );
                        }
                        else if (event.getOldNickname() == null && event.getNewNickname() != null) {
                            results = results.concat(
                                    "\n\nOld Nickname: **No Nickname Found" + "**\nNew Nickname: **" + event.getNewNickname() + "**"
                            );
                        }
                        else if (event.getOldNickname() != null && event.getNewNickname() == null) {
                            results = results.concat(
                                    "\n\nOld Nickname: **" + event.getOldNickname() +
                                            "**\nNew Nickname: ** " + event.getUser().getName() + "** (Reset to Discord Username)"
                            );
                        }
                        tempOldNick.add(event.getOldNickname());
                        tempNewNick.add(event.getNewNickname());

                        results = results.concat("\n\nIf you wish to place a request to change your nickname to what is displayed here: " +
                                "please respond with" +
                                " `" + mainConfig.commandPrefix + "nickname request` (or `"
                                + mainConfig.commandPrefix + "nn req` for short) and this will be submitted to the SAFE Team");

                        embed.setAsError("You Cannot Modify Your Nickname", results);
                        embed.sendDM(null, event.getUser());
                        event.getMember().modifyNickname(event.getOldNickname()).reason("Automatically Reverted Nickname as this player is in a role that prevents nickname changes").queue();
                        String logMessage = event.getMember().getAsMention() + " was not allowed to change their nickname to **"
                                + event.getNewNickname() + "** as they are in a role that prohibits their effective name " +
                                "from changing. So it got reverted back to **" + event.getOldNickname() + "**";
                        embed.setAsSuccess("Nickname Change Prevented",
                                logMessage.replace("null", event.getUser().getName() + " (Their Discord Username)"));
                        embed.sendToLogChannel();
                    }
                }
                else if (!isTeamMember(entry.getUser().getIdLong()) && !inNickRestrictedRole(event.getUser().getIdLong())) {
                    String defaultMessage = event.getUser().getName() + " updated their nickname from "
                            + event.getOldNickname() + " to " + event.getNewNickname()
                            + " as they did not have a role that prohibits it";
                    String defaultDiscordMessage = event.getUser().getAsMention() + " updated their nickname from **"
                            + event.getOldNickname() + "** to **" + event.getNewNickname() + "**. " +
                            "\n*They were able to perform this action because they did not have a role that prohibits it*";
                    embed.setAsInfo("Discord Nickname Updated", defaultDiscordMessage
                            .replace("null", event.getUser().getName() + " (Their Discord Username)"));
                    embed.sendToLogChannel();
                    addNameHistory(event.getUser().getIdLong(), event.getOldNickname(), null);
                    log.info(defaultMessage);
                }
                isBusy = false;
            }
        });
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        Thread.currentThread().setUncaughtExceptionHandler(aue);
        if (!nickConfig.isEnabled()) return;
        isBusy = true;
        try {
            String result = nickCore.withdrawRequest(event.getUser().getIdLong(), true, false);
            if (result != null) {
                embed.setAsWarning("Automatic Nickname Request Withdraw", result);
                embed.sendToLogChannel();
            }
        }
        catch (IOException e) {
            log.error("Member Left Event", e);
        }
        // Writing what their name was when they left the server
        if (event.getMember() != null) {
            addNameHistory(event.getUser().getIdLong(),  event.getMember().getEffectiveName(), null);
        }
        else addNameHistory(event.getUser().getIdLong(), event.getUser().getName(), null);
        isBusy = false;
    }

    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
        Thread.currentThread().setUncaughtExceptionHandler(aue);
        if (inNickRestrictedRole(event.getUser().getIdLong()) || !nickConfig.isEnabled()) return;
        else {
            isBusy = true;
            try {
                String defaultOutput = nickCore.withdrawRequest(event.getUser().getIdLong(), false, true);
                if (defaultOutput != null) {
                    defaultOutput = defaultOutput.replace("?", event.getUser().getAsMention());
                    log.info(defaultOutput.replace(event.getUser().getAsMention(), event.getUser().getName()).split("\n\n")[0]);
                    embed.setAsInfo("Automatic Nickname Request Withdraw", defaultOutput);
                    embed.sendToLogChannel();
                }
            }
            catch (IOException ex) {
                log.error("Member Role Removed Event", ex);
            }
            isBusy = false;
        }
    }
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Thread.currentThread().setUncaughtExceptionHandler(aue);
        isConnected = true;
        if (event.getAuthor().isBot()) return;
        Message msg = event.getMessage();
        String[] args = null;
        try {
            args = msg.getContentRaw().substring(1).split(" ");
        }
        catch (StringIndexOutOfBoundsException ex) {
            // Take No Action - This exception is already handed by DiscordBotMain
            return;
        }
        if (args.length < 2 || !isCommand(args[0], args[1])) return;
        isBusy(msg);
        if (msg.getContentRaw().charAt(0) == mainConfig.commandPrefix) {
            if (args[0].equalsIgnoreCase("nickname") || args[0].equalsIgnoreCase("nn") && !commandsSuspended) {
                try {
                    if (nickConfig.isEnabled()) {
                        nicknameCommand(msg);
                    }
                    else {
                        embed.setAsError("Nickname Feature Disabled", ":x: **You used a command for a section of the bot that is currently disabled**");
                        embed.sendToChannel(msg, msg.getChannel().asTextChannel());
                    }
                }
                catch (IOException e) {
                    log.error("Nickname Command", e);
                }
            }
            else if (commandsSuspended) {
                String defaultTitle = "Commands Suspended";
                if (!isTeamMember(event.getAuthor().getIdLong())) {
                    embed.setAsStop(defaultTitle, "**Commands are Temporarily Suspended on the Nickname Feature side...**" +
                            "\n**Sorry for the inconvience...**");
                    embed.sendToMemberOutput(msg, msg.getAuthor());
                }
                else {
                    embed.setAsStop(defaultTitle, "**Commands are Temporarily Suspended on the Nickname Feature side...**" +
                            "\n**Please Post The Action you were trying to do in either a DM with" + mainConfig.owner.getAsMention() + " or in this channel.**");
                    embed.sendToTeamOutput(msg, null);
                }
            }
        }
        isNotBusy();
    }
    private void init() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                c = ZonedDateTime.now(ZoneId.of("UTC"));
            }
        }, 0, 1000);
    }

    private void nicknameCommand(Message msg) throws IOException {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        String DMResponse = "**You've successfully submitted a request to have your nickname changed**";
        String SocialClubInfo = "\n\n**If you haven't changed your name in Social Club already, " +
                "please do so now so that when the staff go to check your profile, your profile name matches your new requested nickname.**" +
                "\n*If you cannot change your social club name due to the cooldown please use " +
                "`" + mainConfig.commandPrefix + "nickname withdraw` (or `" + mainConfig.commandPrefix + "nn wd` for short) now.*";
        long cmdUserID = msg.getAuthor().getIdLong();
        try {
            guild.retrieveMemberById(cmdUserID).queue(member -> {
                try {
                    String result = "";
                    if (args[1].equalsIgnoreCase("request") || args[1].equalsIgnoreCase("req")) {
                        if (isTeamMember(msg.getAuthor().getIdLong())) {
                            embed.setAsError("No Permissions",
                                    ":x: **You should not need to request a new nickname... you can just change it yourself**");
                            embed.sendToTeamOutput(msg, msg.getAuthor());
                            return;
                        }
                        else {
                            if (!inNickRestrictedRole(cmdUserID)) {
                                String reasonString = "Received Nickname Request from a player who is not a role that prevents changes";
                                embed.setAsSuccess("Nickname Updated", "**You successfully updated your nickname to *"
                                        + args[2] + "***");
                                if (args[2].equalsIgnoreCase("reset")) {
                                    member.modifyNickname(null).reason(reasonString).queue();
                                }
                                else member.modifyNickname(args[2]).reason(reasonString).queue();
                                embed.sendToMemberOutput(msg, msg.getAuthor());
                            }
                            else if (nickCore.discordID.contains(cmdUserID)) {
                                try {
                                    if (!nickCore.newNickname.get(nickCore.discordID.indexOf(cmdUserID)).equals(args[2])) {
                                        embed.setAsError("Nickname Request Pending", "**You Already have a pending nickname change request**" +
                                                "\n\nThe New Nickname you have requested is: **" + nickCore.newNickname.get(nickCore.discordID.indexOf(cmdUserID)) +
                                                "**\n\n*If this new nickname is not correct, please use `" + mainConfig.commandPrefix + "nickname withdraw`" +
                                                " (or `" + mainConfig.commandPrefix + "nn wd` for short) to withdraw this pending request, then you can run this command again.*");
                                    }
                                    else {
                                        embed.setAsWarning("Nickname Request Pending", "**You already have a pending nickname change" +
                                                " request for this new name, please be patient while the staff reviews your old nickname and your requested new nickname.**" +
                                                "\n\n**If you haven't changed your name on Social Club please do, otherwise your request will be denied**");
                                    }
                                }
                                catch (NullPointerException ex) {
                                    String newNickname = "";
                                    if (nickCore.newNickname.get(nickCore.discordID.indexOf(cmdUserID)) == null) {
                                        embed.setAsError("Nickname Request Pending", "**You Already have a pending nickname change request**" +
                                                "\n\nThe New Nickname you have requested is: **" + msg.getAuthor().getName() + " (Reset To Your Discord Username)" +
                                                "**\n\n*If this new nickname is not correct, please use `" + mainConfig.commandPrefix + "nickname withdraw`" +
                                                " (or `" + mainConfig.commandPrefix + "nn wd` for short) to withdraw this pending request, then you can run this command again.*");
                                    }
                                    else {
                                        embed.setAsWarning("Nickname Request Pending", "**You already have a pending nickname change" +
                                                " request for this new name, please be patient while the staff reviews your old nickname and your requested new nickname.**" +
                                                "\n\n**If you haven't changed your name on Social Club please do, otherwise your request will be denied**");
                                    }
                                }
                                embed.sendToMemberOutput(msg, msg.getAuthor());
                            }
                            else if (requestIsCoolingDown(cmdUserID)) {
                                embed.setAsError("Request Cooldown",
                                        "**To Prevent The Requesting and Withdrawing from being abused, there's a "
                                                + nickConfig.requestCoolDown + " minute cooldown in between requests**\n\n" +
                                                "*Would you want to be spammed with requests?*");
                                embed.sendDM(msg, member.getUser());
                            }
                            // /nickname request (No Name Afterwards) They were likely sent a DM with a message to use this command due
                            // to them trying to change it themselves while in the restricted role
                            else if (args.length == 2 && msg.getChannelType() == ChannelType.PRIVATE) {
                                startRequestCooldown(cmdUserID);
                                int index = tempDiscordID.indexOf(cmdUserID);
                                if (index != -1 && msg.getChannelType() == ChannelType.PRIVATE) {
                                    result = nickCore.submitRequest(cmdUserID, tempOldNick.get(index), tempNewNick.get(index));
                                    tempDiscordID.remove(index);
                                    tempOldNick.remove(index);
                                    tempNewNick.remove(index);
                                    if (result.contains("FATAL ERROR")) {
                                        discord.failedIntegrityCheck(this.getClass().getName(), msg, "Nickname: Request Submission with No Nickname Provided");
                                    }
                                    else {
                                        embed.setAsSuccess("Nickname Request Submitted", DMResponse.concat(SocialClubInfo));
                                        embed.sendDM(msg, msg.getAuthor());
                                        embed.setAsInfo("Nickname Request Received", result);
                                        List<TargetChannelSet> channels = new ArrayList<>();
                                        channels.add(TargetChannelSet.LOG);
                                        if (nickConfig.useTeamChannel) {
                                            if (nickConfig.pingOnlineStaff) {
                                                mainConfig.discussionChannel.sendMessage("@here Whenever one of you get a chance, please review the following nickname request:").queue();
                                            }
                                            channels.add(TargetChannelSet.TEAM);
                                        }
                                        log.info("New Nickname Request Received from " + msg.getAuthor().getName());
                                        embed.sendToChannels(msg, channels);
                                    }
                                }
                                else {
                                    embed.setAsError("Nickname Request Error",
                                            "**You cannot do that**, the proper usage for this command is:" +
                                                    "\n`" + mainConfig.commandPrefix + "nickname request <newNickname>`");
                                    embed.sendDM(msg, msg.getAuthor());
                                }
                            }
                            else if (args.length == 3) {
                                if (member.getEffectiveName().equals(args[2])) {
                                    embed.setAsError("Nickname Request Error",
                                            "**Your requested nickname already matches your displayed name**");
                                    embed.sendToMemberOutput(msg, msg.getAuthor());
                                    return;
                                }
                                if (args[2].equalsIgnoreCase("reset") || args[2].equals(msg.getAuthor().getName())) {
                                    result = nickCore.submitRequest(cmdUserID, member.getNickname(), null);
                                }
                                else result = nickCore.submitRequest(cmdUserID, member.getNickname(), args[2]);
                                if (result.equals("None")) {
                                    embed.setAsError("Nickname Request Error",
                                            "**No Need to request for a nickname reset because you don't have a nickname**");
                                    embed.sendToMemberOutput(msg, msg.getAuthor());
                                }
                                else if (result.contains("FATAL ERROR")) {
                                    discord.failedIntegrityCheck(this.getClass().getName(), msg, "Nickname: Request Submission with a Nickname Provided");
                                }
                                else {
                                    startRequestCooldown(cmdUserID);
                                    embed.setAsSuccess("Nickname Request Submitted", result.concat(SocialClubInfo));
                                    if (msg.getChannelType() == ChannelType.PRIVATE) embed.sendDM(msg, msg.getAuthor());
                                    else embed.sendToMemberOutput(msg, msg.getAuthor());
                                    embed.setAsInfo("Nickname Request Received", result);
                                    List<TargetChannelSet> channels = new ArrayList<>();
                                    channels.add(TargetChannelSet.LOG);
                                    if (nickConfig.useTeamChannel) {
                                        if (nickConfig.pingOnlineStaff) {
                                            mainConfig.discussionChannel.sendMessage("@here Whenever one of you get a chance, please review the following nickname request:").queue();
                                        }
                                        channels.add(TargetChannelSet.TEAM);
                                    }
                                    log.info("New Nickname Request Received from " + msg.getAuthor().getName());
                                    embed.sendToChannels(msg, channels);
                                }
                            }
                        }
                    }
                    else if (args[1].equalsIgnoreCase("withdraw") || args[1].equalsIgnoreCase("wd")) {
                        if (args.length == 2 && inNickRestrictedRole(cmdUserID)) {
                            if (nickCore.discordID.indexOf(cmdUserID) == -1) {
                                embed.setAsError("Nothing to Withdraw",
                                        "**:x: No Nickname Request was found for your discord ID**");
                                embed.sendDM(msg, member.getUser());
                                return;
                            }
                            int id = nickCore.requestID.get(nickCore.discordID.indexOf(cmdUserID));
                            String oldNickname = nickCore.oldNickname.get(nickCore.discordID.indexOf(cmdUserID));
                            String newNickname = nickCore.newNickname.get(nickCore.discordID.indexOf(cmdUserID));
                            if (oldNickname == null)
                                oldNickname = msg.getAuthor().getName() + " (No Previous Nickname)";
                            result = nickCore.withdrawRequest(msg.getAuthor().getIdLong(), false, false);

                            if (result.contains("FATAL ERROR")) {
                                discord.failedIntegrityCheck(this.getClass().getName(), msg, "Nickname: Request Withdraw");
                                return;
                            }
                            result = result.replace("?", guild.getMemberById(cmdUserID).getAsMention());
                            embed.setAsSuccess("Successful Withdraw of Name Request", result);

                            if (msg.getChannelType() == ChannelType.PRIVATE) {
                                embed.sendDM(msg, msg.getAuthor());
                            }
                            else embed.sendToMemberOutput(msg, msg.getAuthor());

                            embed.setAsInfo("Nickname Withdraw", result.replace("Successfully ", ""));
                            embed.sendToLogChannel();
                        }
                        else {
                            embed.setAsError("No Permissions",
                                    "**You don't need to use this command to withdraw a request, " +
                                            "this is because you're not in a role that prohibits nickname changes**");
                            embed.sendDM(msg, msg.getAuthor());
                        }
                    }
                    else if ((args[1].equalsIgnoreCase("accept")
                            || args[1].equalsIgnoreCase("acc") || args[1].equalsIgnoreCase("a"))
                            && isTeamMember(msg.getAuthor().getIdLong())) {
                        if (args.length == 3) {
                            requestHandler(msg, null,true, -1);
                        }
                    }
                    else if ((args[1].equalsIgnoreCase("deny") || args[1].equalsIgnoreCase("d")
                            && isTeamMember(msg.getAuthor().getIdLong()))) {
                        if (args.length == 3) {
                            requestHandler(msg, null,false, -1);
                        }
                    }
                    else if ((args[1].equalsIgnoreCase("history") || args[1].equalsIgnoreCase("h"))
                            && isTeamMember(msg.getAuthor().getIdLong())) {
                        if (args.length == 3) {
                            long targetDiscordID = 0;
                            List<String> oldNickArray = null;
                            try {
                                targetDiscordID = Long.parseLong(args[2]);
                                oldNickArray = nickCore.getHistory(targetDiscordID);
                            }
                            catch (NumberFormatException ex) {
                                try {
                                    targetDiscordID = msg.getMentions().getMembers().get(0).getIdLong();
                                    oldNickArray = nickCore.getHistory(targetDiscordID);
                                }
                                catch (IndexOutOfBoundsException e) {
                                    embed.setAsError("Invalid Mention", "**The Mention you entered isn't a valid one**\n " +
                                            "This could be because you misspelled the player's name when you tried to `@[Playername]`");
                                    embed.sendToTeamOutput(msg, null);
                                    return;
                                }
                            }
                            if (oldNickArray == null) {
                                embed.setAsError("No Name History", ":x: **This Player Has No Name History**");
                                embed.sendToTeamOutput(msg, msg.getAuthor());
                            }
                            else {
                                int index = 0;
                                List<String> pages = new ArrayList<>();
                                String tempString = "";
                                do {
                                    tempString = tempString.concat("**" + (index + 1) + ". "+ oldNickArray.get((oldNickArray.size() - 1) - index++) + "**\n");
                                    if (index % 10 == 0) {
                                        pages.add(tempString);
                                        tempString = "";
                                    }

                                } while (index < oldNickArray.size());

                                if (!tempString.isEmpty()) pages.add(tempString);

                                ListEmbed resultEmbed = new ListEmbed(new MessageEntry("Name History", EmbedDesign.INFO, msg, TargetChannelSet.TEAM), "<@!" + targetDiscordID + ">'s Name History is as Follows:",
                                        pages, "Total Old Names: **" + oldNickArray.size() + "**").invertButtonLabels().makeLabelsPlural();

                                discord.addAsReactionListEmbed(resultEmbed);
                            }
                        }
                        else {
                            embed.setAsError("Invalid Number of Arguements",
                                    "**You Entered an Invalid Number of Arguements**" +
                                            "\nFull Syntax: `" + mainConfig.commandPrefix + "nickname history <Mention or Discord ID>`");
                            embed.sendToTeamOutput(msg, null);
                        }
                    }
                    else if (args[1].equalsIgnoreCase("list") && isTeamMember(msg.getAuthor().getIdLong())) {
                        String[] splitString = new String[0];
                        String defaultTitle = "Nickname Request List";
                        log.info("Team Member " + member.getEffectiveName() + " just requested a list of nicknames");
                        try {
                            result = nickCore.getList();
                            splitString = result.split("\n\n");
                            discord.addAsReactionListEmbed(new NickRequestListEmbed(new MessageEntry(defaultTitle, EmbedDesign.INFO, msg, TargetChannelSet.TEAM),
                                    "**For Each of these requests please choose to accept or deny:**", Arrays.asList(splitString), null, this));
                        }
                        catch (NullPointerException ex) {
                            embed.setAsError("No Pending Requests", ":x: **There's no pending nickname requests**");
                            embed.sendToTeamOutput(msg, msg.getAuthor());
                        }
                    }
                    // /nickname forcechange <Mention or Discord ID> <New Nickname>
                    else if ((args[1].equalsIgnoreCase("forcechange") || args[1].equalsIgnoreCase("fch"))) {
                        boolean successfulExecution = false;
                        String defaultTitle = "Successful Nickname Change";
                        String defaultBody = "Staff Member: **" + msg.getAuthor().getAsMention() +
                                "**\nMember: **<Member>" +
                                "**\nOld Nickname: **<oldNick>" +
                                "**\nNew Nickname: **<newNick>**";
                        if (isTeamMember(msg.getAuthor().getIdLong()) && args.length == 4) {
                            String oldNickname = "";
                            String newNickname = "";
                            if (oldNickname == null) oldNickname = "None";
                            Member memberInQuestion = null;
                            try {
                                memberInQuestion = guild.getMemberById(Long.parseLong(args[2]));
                                oldNickname = memberInQuestion.getNickname();
                                if (oldNickname == null) oldNickname = "None";
                                if (args[3].equalsIgnoreCase("reset") ||
                                        args[3].equals(memberInQuestion.getUser().getName())) {
                                    memberInQuestion.modifyNickname(null).reason("Forcefully Changed by " + msg.getAuthor().getName()).queue();
                                    newNickname = memberInQuestion.getUser().getName() + " (Their Discord Username)";
                                    successfulExecution = true;
                                    log.info("Staff Member " + msg.getMember().getEffectiveName() + " successfully forcefully changed " + oldNickname +
                                            "'s effective name back to their discord username");
                                    addNameHistory(memberInQuestion.getIdLong(), oldNickname, msg);
                                }
                                else {
                                    memberInQuestion.modifyNickname(args[3]).reason("Forcefully Changed by " + msg.getAuthor().getName()).queue();
                                    newNickname = args[3];
                                    successfulExecution = true;

                                    if (oldNickname.equals("None")) {
                                        log.info("Staff Member " + msg.getMember().getEffectiveName() + " successfully forcefully changed "
                                                + memberInQuestion.getUser().getName() +
                                                "'s nickname to " + newNickname);
                                        addNameHistory(memberInQuestion.getIdLong(), memberInQuestion.getUser().getName(), msg);
                                    }
                                    else {
                                        log.info("Staff Member " + msg.getMember().getEffectiveName() + " successfully forcefully changed " + oldNickname +
                                                "'s nickname to " + newNickname);
                                        addNameHistory(memberInQuestion.getIdLong(), oldNickname, msg);
                                    }
                                }
                                embed.setAsSuccess("Nickname Updated", msg.getAuthor().getAsMention() + " successfully updated " +
                                        memberInQuestion.getAsMention());
                                embed.sendToTeamOutput(msg, null);
                            }
                            catch (NumberFormatException ex) {
                                if (msg.getMentions().getMembers().size() == 1) {
                                    memberInQuestion = msg.getMentions().getMembers().get(0);
                                    oldNickname = memberInQuestion.getNickname();
                                    if (oldNickname == null) oldNickname = memberInQuestion.getUser().getName();

                                    if (args[3].equalsIgnoreCase("reset")) {
                                        memberInQuestion.modifyNickname(null).reason("Forcefully Changed by " + msg.getAuthor().getName()).queue();
                                        newNickname = memberInQuestion.getUser().getName() + " (Their Discord Username)";
                                        successfulExecution = true;
                                        log.info("Staff Member " + msg.getMember().getEffectiveName() + " successfully forcefully changed " + oldNickname +
                                                "'s effective name back to their discord username");
                                        addNameHistory(memberInQuestion.getIdLong(), oldNickname, msg);
                                    }
                                    else {
                                        memberInQuestion.modifyNickname(args[3]).reason("Forcefully Changed by " + msg.getAuthor().getName()).queue();
                                        newNickname = args[3];
                                        successfulExecution = true;
                                        if (oldNickname.equals(memberInQuestion.getUser().getName())) {
                                            log.info("Staff Member " + msg.getMember().getEffectiveName() + " successfully forcefully changed "
                                                    + memberInQuestion.getUser().getName() +
                                                    "'s nickname to " + newNickname);
                                            addNameHistory(memberInQuestion.getIdLong(), memberInQuestion.getUser().getName(), msg);
                                        }
                                        else {
                                            log.info("Staff Member " + msg.getMember().getEffectiveName() + " successfully forcefully changed " + oldNickname +
                                                    "'s nickname to " + newNickname);
                                            addNameHistory(memberInQuestion.getIdLong(), oldNickname, msg);
                                        }
                                    }
                                    embed.setAsSuccess("Nickname Updated", msg.getAuthor().getAsMention() + " successfully updated " +
                                            memberInQuestion.getAsMention());
                                }
                                else {
                                    embed.setAsError("Too Many Mentioned Players", "**You have too many mentioned players in this command**" +
                                            "\nSyntax Reminder: `" + mainConfig.commandPrefix + "nickname forcechange <Mention or Discord ID> <New Nickname>`");
                                }
                                embed.sendToTeamOutput(msg, null);
                            }
                            catch (NullPointerException ex) {
                                embed.setAsError("Player Not Found", "**This Player Was Not Found in this Discord Server**");
                                embed.sendToTeamOutput(msg, null);
                            }
                            if (successfulExecution) {
                                if (oldNickname.equals(memberInQuestion.getUser().getName())) {
                                    defaultBody = defaultBody.replace("<Member>", memberInQuestion.getAsMention())
                                            .replace("<oldNick>", oldNickname.concat(" (Their Discord Username)"))
                                            .replace("<newNick>", newNickname);
                                }
                                else {
                                    defaultBody = defaultBody.replace("<Member>", memberInQuestion.getAsMention())
                                            .replace("<oldNick>", oldNickname).replace("<newNick>", newNickname);
                                }
                                embed.setAsSuccess(defaultTitle, defaultBody);
                                embed.sendToLogChannel();
                            }
                        }
                        else if (isTeamMember(msg.getAuthor().getIdLong()) && args.length != 4) {
                            embed.setAsError("Incorrect Arguments", "**You did not use the correct number of arguments**" +
                                    "\nSyntax Reminder: `" + mainConfig.commandPrefix + "nickname forcechange <Mention or Discord ID> <New Nickname>`");
                            embed.sendToTeamOutput(msg, null);
                        }
                        else {
                            embed.setAsError("No Permission", "**You Lack Permissions Do That**");
                            embed.sendToMemberOutput(msg, msg.getAuthor());
                        }
                    }
                    else if (args[1].equalsIgnoreCase("clearhistory") || args[1].equalsIgnoreCase("clh")) {
                        if (args.length == 3) {
                            AtomicReference<User> targetUser = new AtomicReference<>();

                            try {
                                targetUser.set(guild.getJDA().getUserById(Long.parseLong(args[2])));
                            }
                            catch (NumberFormatException ex) {
                                targetUser.set(msg.getMentions().getMembers().get(0).getUser());
                            }
                            catch (NullPointerException ex) {
                                guild.getJDA().retrieveUserById(Long.parseLong(args[2])).queue(user -> targetUser.set(user));
                            }

                            embed.setAsSuccess("Successfully Cleared Name History", nickCore.clearNameHistory(targetUser.get().getIdLong()));
                            embed.sendToTeamOutput(msg, msg.getAuthor());
                        }
                        else {
                            embed.setAsError("Incorrect Arguments", "**You did not use the correct number of arguments**" +
                                    "\nSyntax Reminder: `" + mainConfig.commandPrefix + "nickname clearhistory <Mention or Discord ID>`");
                            embed.sendToTeamOutput(msg, null);
                        }
                    }
                    else if (isCommand(args[0], args[1]) && !isTeamMember(msg.getAuthor().getIdLong())) {
                        embed.setAsError("No Permissions", "**You Lack Permissions To Perform `" + mainConfig.commandPrefix + "nickname "
                                + args[1] + "`**");
                        embed.sendDM(msg, msg.getAuthor());
                    }
                }
                // Handling any IOException any other method may throw
                catch (IOException ex) {
                    log.error("Nickname Command IOException Handling", ex);
                }
            });
        }
        // Indicates this user was not in the discord server
        catch (ErrorResponseException ex) {
            embed.setAsError("Not a Member", ":x: **You Were Not Found in our discord server!** " +
                    "\nPlease join the discord server and try again");
            embed.sendDM(msg, msg.getAuthor());
        }
    }
    // targetDiscordID argument is for accepting or denying the request via a reaction, it's -1 when it's via command
    void requestHandler(Message msg, Member handler, boolean requestAccepted, long targetDiscordID) throws IOException {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        String result = "";
        if (handler == null) {
            handler = msg.getMember();
        }
        if (targetDiscordID != -1) {
            args = new String[3];
            args[0] = "nn";
            if (requestAccepted) args[1] = "a";
            else args[1] = "d";
            args[2] = String.valueOf(targetDiscordID);
        }
        else {
            targetDiscordID = 0;
        }
        int targetRequestID = 0;
        String oldNickname = "";
        String newNickname = "";
        AtomicReference<Member> memberInQuestion = new AtomicReference<>();
        if (args.length == 3) {
            boolean isMention = false;
            try {
                if (targetDiscordID != 0) {
                    targetRequestID = nickCore.requestID.get(nickCore.discordID.indexOf(targetDiscordID));
                    oldNickname = nickCore.oldNickname.get(nickCore.discordID.indexOf(targetDiscordID));
                    newNickname = nickCore.newNickname.get(nickCore.discordID.indexOf(targetDiscordID));
                }
                else {
                    targetRequestID = Integer.parseInt(args[2]);
                    targetDiscordID = nickCore.discordID.get(nickCore.requestID.indexOf(targetRequestID));
                    oldNickname = nickCore.oldNickname.get(nickCore.requestID.indexOf(targetRequestID));
                    newNickname = nickCore.newNickname.get(nickCore.requestID.indexOf(targetRequestID));
                }
                guild.retrieveMemberById(targetDiscordID).queue(m -> memberInQuestion.set(m));
                if (requestAccepted) {
                    result = nickCore.acceptRequest(-1, targetRequestID);
                    addNameHistory(targetDiscordID, oldNickname, msg);
                }
                else {
                    result = nickCore.denyRequest(-1, targetRequestID);
                }
            }
            catch (IndexOutOfBoundsException ex) {
                embed.setAsError("Nickname Request Not Found",
                        "**Your nickname request cannot be found**");
                embed.sendToTeamOutput(msg, null);
                return;
            }
            catch (NumberFormatException ex) {
                isMention = true;
                targetDiscordID = msg.getMentions().getMembers().get(0).getIdLong();
                oldNickname = nickCore.oldNickname.get(nickCore.discordID.indexOf(targetDiscordID));
                newNickname = nickCore.newNickname.get(nickCore.discordID.indexOf(targetDiscordID));
                guild.retrieveMemberById(targetDiscordID).queue(m -> memberInQuestion.set(m));
                if (requestAccepted) {
                    result = handler.getAsMention() + " " + nickCore.acceptRequest(targetDiscordID, -1);
                    addNameHistory(targetDiscordID, oldNickname, msg);
                }
                else {
                    result = handler.getAsMention() + " " + nickCore.denyRequest(targetDiscordID, -1);
                }
            }
            if (isMention) {
                if (result.contains("FATAL ERROR")) {
                    discord.failedIntegrityCheck(this.getClass().getName(), msg, "Nickname: Request Handler - Mention");
                }
                else {
                    String[] getNewNicknameArray = result.split("New Nickname: ");
                    String getNewNickname = getNewNicknameArray[1].substring(2, getNewNicknameArray[1].lastIndexOf('*') - 1);
                    if (requestAccepted) {
                        String defaultTitle = "Successful Nickname Request Acceptance";
                        if (newNickname == null) {
                            memberInQuestion.get().modifyNickname(null)
                                    .reason("Staff Member "  + handler.getUser().getName() + " accepted their nickname request").queue();
                            embed.setAsSuccess(defaultTitle, result);
                            embed.sendToChannels(msg, TargetChannelSet.TEAM, TargetChannelSet.LOG);
                            String messageToPlayer = "**Your Nickname Request was Accepted** \n " +
                                    "Your new name on the Discord Server now matches your discord username";
                            embed.setAsSuccess(defaultTitle, messageToPlayer);
                            embed.sendDM(msg, guild.getMemberById(targetDiscordID).getUser());
                            log.info("Team Member " + handler.getEffectiveName() + " successfully accepted the nickname request of player " +
                                    guild.getMemberById(targetDiscordID).getUser().getName() + "," +
                                    " their nickname was erased and now it matches their discord name.");
                        }
                        else {
                            memberInQuestion.get().modifyNickname(getNewNickname)
                                    .reason("Staff Member " + handler.getUser().getName() + " accepted their nickname request").queue();
                            embed.setAsSuccess("Nickname Request Accepted", result);
                            embed.sendToChannels(msg, TargetChannelSet.TEAM, TargetChannelSet.LOG);
                            String messageToPlayer = "**Your Nickname Request was Accepted** \n " +
                                    "Your new name on the Discord Server is now **" + getNewNickname + "**";
                            embed.setAsSuccess(defaultTitle, messageToPlayer);
                            embed.sendDM(msg, guild.getMemberById(targetDiscordID).getUser());
                            log.info("Team Member " + handler.getEffectiveName() + " successfully accepted the nickname request of player " +
                                    guild.getMemberById(targetDiscordID).getUser().getName() + "," +
                                    " they had nickname " + oldNickname + " and their new nickname is " + newNickname);
                        }
                    }
                    else {
                        String defaultTitle = "Successful Nickname Request Rejection";
                        embed.setAsSuccess(defaultTitle, msg.getAuthor().getAsMention() + " " + result);
                        embed.sendToChannels(msg, TargetChannelSet.TEAM, TargetChannelSet.LOG);
                        String messageToPlayer = "**Your Nickname Request was Denied** \n" +
                                "*This was most likely because you haven't changed your " +
                                "Social Club name to match your requested nickname*"
                                + "\nIf you are sure it wasn't for that reason, you can ask a SAFE Team Member and they will tell you why" +
                                " your request was denied.";
                        embed.setAsError("Nickname Request Denied", messageToPlayer);
                        embed.sendDM(msg, guild.getMemberById(targetDiscordID).getUser());
                        log.info("Team Member " + handler.getEffectiveName() + " successfully rejected the nickname request of player " +
                                guild.getMemberById(targetDiscordID).getUser().getName() + "," +
                                " their nickname remains " + oldNickname);
                    }
                }
            }
            else {
                if (result.contains("FATAL ERROR")) {
                    discord.failedIntegrityCheck(this.getClass().getName(), msg, "Nickname: Request Handler - Request ID");
                }
                else {
                    String[] getNewNicknameArray = result.split("New Nickname: ");
                    String getNewNickname = getNewNicknameArray[1].substring(2, getNewNicknameArray[1].lastIndexOf('*') - 1);
                    if (requestAccepted) {
                        if (newNickname == null) {
                            memberInQuestion.get().modifyNickname(null).reason("Staff Member " + handler.getEffectiveName() +
                                    " accepted the nickname request of " + memberInQuestion.get().getUser().getName() +
                                    ", their display name now matches their discord username").queue();
                            embed.setAsSuccess("Successful Nickname Request Acceptance", result);
                            embed.sendToChannels(msg, TargetChannelSet.TEAM, TargetChannelSet.LOG);
                            String messageToPlayer = "**Your Nickname Request was Accepted** \n " +
                                    "Your nickname was removed on the Discord Server and now matches your discord username";
                            embed.setAsSuccess("Successful Nickname Request Acceptance", messageToPlayer);
                            embed.sendDM(msg, memberInQuestion.get().getUser());
                            log.info("Team Member " + handler.getEffectiveName() + " successfully accepted the nickname request of player " +
                                    guild.getMemberById(targetDiscordID).getUser().getName() + "," +
                                    " their nickname was erased and now it matches their discord name.");
                        }
                        else {
                            memberInQuestion.get().modifyNickname(getNewNickname).reason("Staff Member " + handler.getEffectiveName() +
                                    " accepted the nickname request of " + memberInQuestion.get().getUser().getName()).queue();
                            embed.setAsSuccess("Successful Nickname Request Acceptance", result);
                            embed.sendToChannels(msg, TargetChannelSet.TEAM, TargetChannelSet.LOG);
                            String messageToPlayer = "**Your Nickname Request was Accepted** \n " +
                                    "Your new name on the Discord Server is now " + getNewNickname;
                            embed.setAsSuccess("Successful Nickname Request Acceptance", messageToPlayer);
                            embed.sendDM(msg, memberInQuestion.get().getUser());
                            log.info("Team Member " + handler.getEffectiveName() + " successfully accepted the nickname request of player " +
                                    guild.getMemberById(targetDiscordID).getUser().getName() + ", " +
                                    "they had nickname " + oldNickname + " and their new nickname is " + newNickname);
                        }
                    }
                    else {
                        String defaultTitle = "Successful Nickname Request Rejection";
                        embed.setAsSuccess(defaultTitle, result);
                        embed.sendToTeamOutput(msg, null);
                        embed.setAsSuccess(defaultTitle, result);
                        embed.sendToLogChannel();
                        log.info("Team Member " + handler.getEffectiveName() + " successfully rejected the nickname request of player " +
                                guild.getMemberById(targetDiscordID).getUser().getName() + "," +
                                " their nickname remains " + oldNickname);
                        String messageToPlayer = "**Your Nickname Request was Denied** \n" +
                                "*This was most likely because you haven't changed your " +
                                "Social Club name to match your requested nickname*"
                                + "\nIf you are sure it wasn't for that reason, you can ask a SAFE Team Member and they will tell you why" +
                                " your request was denied.";
                        embed.setAsError("Nickname Request Denied", messageToPlayer);
                        embed.sendDM(msg, guild.getMemberById(targetDiscordID).getUser());

                    }
                }
            }
            if (nickCore.arraySizesEqual()) {
                nickCore.saveDatabase();
            }
            else {
                discord.failedIntegrityCheck(this.getClass().getName(), msg, "Request Handler - Final Integrity Check");
            }
        }
        else {
            embed.setAsError("Invaid Number of Arguements",
            "**You Entered an Invalid Number of Arguements**" +
                    "\n\nUsage: `" + mainConfig.commandPrefix + "nickname accept <Mention or Request ID>`");
            embed.sendToTeamOutput(msg, null);
        }
    }
    public void helpCommand(Message msg, boolean isTeamMember) {
        String[] args = msg.getContentRaw().substring(1).split(" ");

        if (args.length == 3) {
            switch (args[2].toLowerCase()) {
                case "req":
                case "request": help.requestCommand(); break;
                case "wd":
                case "withdraw": help.withdrawCommand(); break;
                case "a":
                case "acc":
                case "accept": help.acceptCommand(isTeamMember); break;
                case "d":
                case "deny": help.denyCommand(isTeamMember); break;
                case "h":
                case "history": help.historyCommand(isTeamMember); break;
                case "fch":
                case "forcechange": help.forceChangeCommand(); break;
                case "clh":
                case "clearhistory": help.clearHistoryCommand(isTeamMember); break;
                case "list": help.listCommand(); break;
            }
        }
        else if (args.length == 2) {
            embed.setAsError(mainConfig.commandPrefix + "nickname Command Help",
                    "This help requires another argument following `nickname` valid comamnds include:" +
                            "\n\n`" + mainConfig.commandPrefix + "help nickname request`" +
                            "\n`" + mainConfig.commandPrefix + "help nickname withdraw`" +
                            "\n`" + mainConfig.commandPrefix + "help nickname accept`" +
                            "\n`" + mainConfig.commandPrefix + "help nickname deny`" +
                            "\n`" + mainConfig.commandPrefix + "help nickname history`" +
                            "\n`" + mainConfig.commandPrefix + "help nickname forcechange`" +
                            "\n`" + mainConfig.commandPrefix + "help nickname clearhistory`" +
                            "\n`" + mainConfig.commandPrefix + "help nickname list`");

        }
        if (isTeamMember) embed.sendToTeamOutput(msg, msg.getAuthor());
        else embed.sendToChannel(msg, msg.getChannel().asTextChannel());
    }
    private void isBusy(Message msg) {
        isBusy = true;
        commandUser = msg.getAuthor();
    }
    private void isNotBusy() {
        isBusy = false;
        commandUser = null;
    }
    private boolean inNickRestrictedRole(long targetDiscordID) {
        List<Role> hasRoles = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        guild.retrieveMemberById(targetDiscordID).useCache(false).submit().whenComplete(new BiConsumer<Member, Throwable>() {
            @Override
            public void accept(Member member, Throwable throwable) {
                if (throwable == null) {
                    member.getRoles().forEach(role -> hasRoles.add(role));
                }
                else {
                    log.error("Unable to Retrieve Member Object from Discord Gateway with Discord ID " + targetDiscordID + ": " + throwable.getMessage());
                }
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return containsRestrictedRoles(hasRoles, targetDiscordID);
    }
    private boolean inNickRestrictedRole(Member m) {
        return containsRestrictedRoles(m.getRoles(), m.getIdLong());
    }

    private boolean containsRestrictedRoles(List<Role> hasRoles, long targetDiscordID) {
        if (hasRoles.isEmpty() || isTeamMember(targetDiscordID)) return false;
        else {
            int index = 0;
            while (index < nickConfig.restrictedRoles.size()) {
                if (hasRoles.contains(nickConfig.restrictedRoles.get(index++))) {
                    return true;
                }
            }
            return false;
        }
    }
    private boolean requestIsCoolingDown(long targetDiscordID) {
        int index = requestCooldownDiscordIDs.indexOf(targetDiscordID);
        try {
            if (c.isBefore(requestCooldownDates.get(index))) return true;
            else {
                requestCooldownDiscordIDs.remove(index);
                requestCooldownDates.remove(index);
                return false;
            }
        }
        catch (IndexOutOfBoundsException ex) {
            return false;
        }
    }
    private void startRequestCooldown(long targetDiscordID) {
        requestCooldownDiscordIDs.add(targetDiscordID);
        requestCooldownDates.add(ZonedDateTime.now(ZoneId.of("UTC")).plusMinutes(nickConfig.requestCoolDown));
    }
    public void reload(Message msg) {
        nickCore.reloadConfig();
        setupRestrictedRoles(true);
        requestCooldownDiscordIDs.clear();
        requestCooldownDates.clear();
        log.info("Nickname Configuration Successfully Reloaded");
    }
    private void setupRestrictedRoles(boolean isReload) {
        int index = 0;
        boolean successfulRun = true;
        String defaultOutput = "The Name Restricted Roles are: ";
        if (isReload) {
            nickConfig.restrictedRoles.clear();
            commandsSuspended = false;
        }
        while (index < nickConfig.restrictedRolesLong.size()) {
            try {
                nickConfig.restrictedRoles.add(guild.getRoleById(nickConfig.restrictedRolesLong.get(index)));
                defaultOutput = defaultOutput.concat("\n" + nickConfig.restrictedRoles.get(index).getAsMention());
            }
            catch (NullPointerException ex) {
                commandsSuspended = true;
                if (isReload) {
                    embed.setAsStop("Name Restricted Roles Not Found",
                            "I could not find one or more of the restricted roles " +
                                    "in the config file when it was reloaded" +
                                    "\n\n**Commands Have Been Suspended on the Nickname Feature Side**");
                    log.fatal("Name Restricted Roles Not Found on Reload");
                }
                else {
                    embed.setAsStop("Name Restricted Roles Not Found",
                            "I could not find one or more of the restricted roles " +
                                    "in the config file when I was started" +
                                    "\n\n**Commands Have Been Suspended on the Nickname Feature Side**");
                    log.fatal("Name Restricted Roles Not Found on Startup");
                }
                embed.sendToLogChannel();
                successfulRun = false;
                break;
            }
            index++;
        }
        if (successfulRun) {
            embed.setAsSuccess("Name Restricted Roles Setup", defaultOutput);
            embed.sendToLogChannel();
            log.info("Name Restricted Roles Setup");
        }
    }
    private void addNameHistory(long targetDiscordID, String oldName, Message msg) {
        List<String> oldNickArray = nickCore.oldNickDictionary.get(targetDiscordID);
        if (oldNickArray == null) oldNickArray = new ArrayList<>();
        if (!oldNickArray.isEmpty() && oldNickArray.get(oldNickArray.size() - 1).equals(oldName)) return;
        if (oldName == null) {
            List<String> finalOldNickArray = oldNickArray;
            guild.retrieveMember(UserSnowflake.fromId(targetDiscordID)).useCache(false).submit().whenComplete(new BiConsumer<Member, Throwable>() {
                @Override
                public void accept(Member member, Throwable throwable) {
                    if (throwable != null) {
                        log.fatal(throwable.getMessage());
                    }
                    else {
                        finalOldNickArray.add(member.getUser().getName());
                        nickCore.oldNickDictionary.put(targetDiscordID, finalOldNickArray);
                        if (nickCore.arraySizesEqual()) {
                            nickCore.saveDatabase();
                        }
                        else discord.failedIntegrityCheck(this.getClass().getName(), msg, "Name History");
                    }
                }
            });
        }
        else {
            oldNickArray.add(oldName);
            nickCore.oldNickDictionary.put(targetDiscordID, oldNickArray);
            if (nickCore.arraySizesEqual()) {
                nickCore.saveDatabase();
            }
            else discord.failedIntegrityCheck(this.getClass().getName(), msg, "Name History");
        }


    }
    public boolean isCommand(String cmd, String arg) {
        int index = 0;
        while (index < commands.size()) {
            if (commands.get(index).equalsIgnoreCase(cmd)) {
                int argsIndex = 0;
                while (argsIndex < nickArgs.size()) {
                    if (nickArgs.get(argsIndex++).equalsIgnoreCase(arg)) return true;
                }
            }
            index++;
        }
        return false;
    }
    public String getStatusString() {
        String defaultOutput =
                "\n\n*__Nickname Feature__*" +
                        "\nStatus: **?**";

        if (nickConfig.isEnabled()) {
            defaultOutput = defaultOutput.concat("\nCommand Status: **" + !commandsSuspended +
                    "**\nPing Time: **" + guild.getJDA().getGatewayPing() + "ms**");

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
        }
        else {
            defaultOutput = defaultOutput.replace("?", ":x: Disabled");
        }
        return defaultOutput;
    }
    NicknameMain getThis() {
        return this;
    }
    public NickConfiguration getConfig() {
        return nickConfig;
    }
}