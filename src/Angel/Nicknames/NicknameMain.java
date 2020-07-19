package Angel.Nicknames;

import Angel.DiscordBotMain;
import Angel.EmbedHandler;
import Angel.MainConfiguration;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NicknameMain extends ListenerAdapter {
    private final Logger log = LogManager.getLogger(NicknameMain.class);
    private Guild guild;
    private DiscordBotMain discord;
    private Angel.Nicknames.FileHandler fileHandler;
    private NickConfiguration nickConfig;
    private EmbedHandler embed;
    private MainConfiguration mainConfig;
    private List<Role> restrictedRoles = new ArrayList<>();
    private NickCore nickCore;
    private ArrayList<Long> tempDiscordID = new ArrayList<>();
    private ArrayList<String> tempOldNick = new ArrayList<>();
    private ArrayList<String> tempNewNick = new ArrayList<>();
    private boolean commandsSuspended = false;
    private boolean ignoreNewNickname = false;

    public NicknameMain(MainConfiguration importMainConfig, EmbedHandler importEmbed, Guild importGuild, DiscordBotMain importDiscord) throws IOException {
        this.mainConfig = importMainConfig;
        this.embed = importEmbed;
        this.guild = importGuild;
        this.discord = importDiscord;

        this.nickCore = new NickCore(guild);
        this.fileHandler = new FileHandler(nickCore);
        try {
            nickConfig = new NickConfiguration(fileHandler.getConfig(), fileHandler.gson) {};
            nickConfig.setup();
            nickCore.startup();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        int index = 0;
        while (index < nickConfig.restrictedRoles.size()) {
            try {
                restrictedRoles.add(guild.getRoleById(nickConfig.restrictedRoles.get(index)));
            }
            catch (NullPointerException ex) {
                commandsSuspended = true;
                break;
            }
            index++;
        }
    }

    @Override
    public void onUserUpdateName(@Nonnull UserUpdateNameEvent event) {
        if (guild.getMember(event.getUser()).getNickname() == null && inNickRestrictedRole(event.getUser().getIdLong())) {
            ignoreNewNickname = true;
            guild.getMember(event.getUser()).modifyNickname(event.getOldName()).queue();
            embed.setAsInfo("Automatic Nickname Addition",
            "**Your name on this discord server was automatically set back to your old discord name**" +
                    " \n\nThis is due to the fact that you're in a role that prohibits name changes, your name in the SAFE Crew discord server" +
                    " must match your social club profile name");
            embed.sendDM(event.getUser());
        }
    }

    @Override
    public void onGuildMemberUpdateNickname(@Nonnull GuildMemberUpdateNicknameEvent event) {
        if (!ignoreNewNickname
                && inNickRestrictedRole(event.getMember().getIdLong())
                && !discord.isTeamMember(event.getMember().getIdLong())) {
            if (tempDiscordID.contains(event.getUser().getIdLong()) || nickCore.discordID.contains(event.getUser().getIdLong())) {
                if (tempDiscordID.contains(event.getUser().getIdLong())) {
                    ignoreNewNickname = true;
                    embed.setAsWarning("Instructions Warning",
                    "Please Re-Read the Instructions I just gave you above this message");
                    embed.sendDM(event.getUser());
                    event.getMember().modifyNickname(event.getOldNickname()).queue();

                }
                else if (nickCore.discordID.contains(event.getUser().getIdLong())) {
                    ignoreNewNickname = true;
                    embed.setAsError("Pending Request Info",
                    "You already have a pending nickname request");
                    embed.sendDM(event.getUser());
                    event.getMember().modifyNickname(event.getOldNickname()).queue();
                }
                else {
                    return;
                }
            }
            else {
                tempDiscordID.add(event.getUser().getIdLong());
                String results = "**You're in a role that prohibits modifying your name**";

                if (event.getOldNickname() != null && event.getNewNickname() != null) {
                    results = results.concat(
                            "\n\nOld NickName: **" + event.getOldNickname() + "**\nNew Nickname: **" + event.getNewNickname() + "**"
                    );
                }
                else if (event.getOldNickname() == null && event.getNewNickname() != null) {
                    results = results.concat(
                            "\n\nOld NickName: **No Nickname Found" + "**\nNew Nickname: **" + event.getNewNickname() + "**"
                    );
                }
                else if (event.getOldNickname() != null && event.getNewNickname() == null) {
                    results = results.concat(
                            "\n\nOld NickName: **" + event.getOldNickname() + "**\nNew Nickname: *Reset*"
                    );
                }
                tempOldNick.add(event.getOldNickname());
                tempNewNick.add(event.getNewNickname());

                results = results.concat("\n\nIf you wish to place a request to change your nickname please respond with" +
                        " `/nickname request` (or `/nn req` for short) and this will be submitted to the SAFE Team");

                embed.setAsError("You Cannot Modify Your Nickname", results);
                embed.sendDM(event.getUser());
                ignoreNewNickname = true;
                event.getMember().modifyNickname(event.getOldNickname()).queue();
            }
        }
        else {
            ignoreNewNickname = false;
        }
    }

    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
        try {
            String result = nickCore.withdrawRequest(event.getUser().getIdLong(), true);
            if (result != null) {
                embed.setAsInfo("Nickname Withdraw - Guild Leave Event", result);
                embed.sendToLogChannel();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        Message msg = event.getMessage();
        String[] args = msg.getContentRaw().substring(1).split(" ");
        if (msg.getContentRaw().charAt(0) == '/' && !commandsSuspended) {
            if (args[0].equalsIgnoreCase("nickname") || args[0].equalsIgnoreCase("nn")) {
                if (inNickRestrictedRole(event.getAuthor().getIdLong()) || discord.isTeamMember(event.getAuthor().getIdLong())) {
                    try {
                        nicknameCommand(msg);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    embed.setAsInfo("Nickname Command Info",
                    "There's no need to use this command, you're not in a role that prohibits nickname changes");
                    embed.sendToHelpChannel(msg.getChannel(), msg.getMember());
                }
            }
        }
        else if (msg.getContentRaw().charAt(0) == '/' && commandsSuspended) {
            String defaultTitle = "Commands Suspended";
            if (!discord.isTeamMember(event.getAuthor().getIdLong())) {
                embed.setAsStop(defaultTitle,"**Commands are Temporarily Suspended on the Nickname Feature side...**" +
                        "\n**Sorry for the inconvience...**");
                embed.sendToHelpChannel(msg.getChannel(), msg.getMember());
            }
            else {
                embed.setAsStop(defaultTitle,"**Commands are Temporarily Suspended on the Nickname Feature side...**" +
                        "\n**Please Post The Action you were trying to do in either a DM with" + mainConfig.owner.getAsMention() + " or in this channel.**");
                embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
            }
        }
    }

    private void nicknameCommand(Message msg) throws IOException {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        Member member = msg.getMember();
        String result = "";
        String DMResponse = "**You've successfully submitted a request to have your nickname changed**";
        String SocialClubInfo = "\n\n**If you haven't changed your name in Social Club already, " +
                "please do so now so that when the staff go to check your profile, your profile name matches your new requested nickname.**" +
                "\n*If you cannot change your social club name due to the cooldown please use `/nickname withdraw` (or `/nn wd` for short) now.*";
        long targetDiscordID = msg.getAuthor().getIdLong();

        if (args[1].equalsIgnoreCase("request") || args[1].equalsIgnoreCase("req")) {
            if (nickCore.discordID.contains(targetDiscordID)) {
                embed.setAsWarning("Nickname Request Pending", "**You already have a pending nickname change" +
                        " request, please be patient while the staff reviews your old nickname and your requested new nickname.**" +
                        "\n\n**If you haven't changed your name on Social Club please do, otherwise your request will be denied**");
            }
            // /nickname request (No Name Afterwards) They were likely sent a DM with a message to use this command due
            // to them trying to change it themselves while in the restricted role
            else if (args.length == 2 && msg.getChannelType() == ChannelType.PRIVATE) {
                PrivateChannel channel = msg.getPrivateChannel();
                int index = tempDiscordID.indexOf(targetDiscordID);
                if (index != -1 && msg.getChannelType() == ChannelType.PRIVATE) {
                    result = nickCore.submitRequest(targetDiscordID, tempOldNick.get(index), tempNewNick.get(index));
                    tempDiscordID.remove(index);
                    tempOldNick.remove(index);
                    tempNewNick.remove(index);
                    if (result.contains("FATAL ERROR")) {
                        failedIntegrityCheck(msg.getMember(), "Nickname: Request Submission with No Nickname Provided", msg.getChannel());
                    }
                    else {
                        embed.setAsSuccess("Nickname Request Submitted", DMResponse.concat(SocialClubInfo));
                        embed.sendDM(msg.getAuthor());
                        embed.setAsInfo("Nickname Request Received", result);
                        embed.sendToLogChannel();
                        log.info("New Nickname Request Received from " + channel.getUser().getName());
                    }
                }
                else {
                    embed.setAsError("Nickname Request Error",
                    "**You cannot do that**, the proper usage for this command is:" +
                            "\n`/nickname request <newNickname>`");
                    embed.sendDM(msg.getAuthor());
                }
            }
            else if (args.length == 3 && msg.getChannelType() != ChannelType.PRIVATE) {
                result = nickCore.submitRequest(targetDiscordID, msg.getMember().getNickname(), args[2]);
                if (result.contains("FATAL ERROR")) {
                    failedIntegrityCheck(msg.getMember(), "Nickname: Request Submission with a Nickname Provided", msg.getChannel());
                }
                else {
                    embed.setAsSuccess("Nickname Request Submitted", result.concat(SocialClubInfo));
                    embed.sendToHelpChannel(msg.getChannel(), msg.getMember());
                    embed.setAsInfo("Nickname Request Received", result);
                    embed.sendToLogChannel();
                }
            }
            else if (args.length == 3) {
                // /nickname request <newName>
                // In a Direct Message
                embed.setAsError("Command Error", "**This Command is only usable in the discord server in a channel I can see** " +
                        "\nI need to be able to get your old name and I cannot do that in a Direct Message channel");
                embed.sendDM(msg.getAuthor());
            }
        }
        else if (args[1].equalsIgnoreCase("withdraw") || args[1].equalsIgnoreCase("wd")) {
            if (args.length == 2) {
                int id = nickCore.requestID.get(nickCore.discordID.indexOf(msg.getAuthor().getIdLong()));
                String oldNickname = nickCore.oldNickname.get(nickCore.discordID.indexOf(msg.getAuthor().getIdLong()));
                String newNickname = nickCore.newNickname.get(nickCore.discordID.indexOf(msg.getAuthor().getIdLong()));
                if (oldNickname == null) oldNickname = msg.getAuthor().getName() + " (No Previous Nickname)";
                result = nickCore.withdrawRequest(msg.getAuthor().getIdLong(), false);

                if (result.contains("FATAL ERROR")) {
                    failedIntegrityCheck(msg.getMember(), "Nickname: Request Withdraw", msg.getChannel());
                    return;
                }
                result = result.replace("?", oldNickname);
                embed.setAsSuccess("Successful Withdraw of Name Request", result);

                if (msg.getChannelType() == ChannelType.PRIVATE) {
                    embed.sendDM(msg.getAuthor());
                }
                else embed.sendToHelpChannel(msg.getChannel(), msg.getMember());

                embed.setAsInfo("Nickname Withdraw",
                        "**" + msg.getAuthor().getAsMention() + " withdrew a nickname change request.**" +
                                "\n\nID: **" + id +
                                "**\nOld Nickname: **" + oldNickname +
                                "**\nNew Nickname: **" + newNickname + "**");
                embed.sendToLogChannel();
            }
        }
        else if ((args[1].equalsIgnoreCase("accept")
                || args[1].equalsIgnoreCase("acc") || args[1].equalsIgnoreCase("a"))
                && discord.isTeamMember(msg.getMember().getIdLong())) {
            if (args.length == 3) {
                requestHandler(msg, true);
            }
        }
        else if ((args[1].equalsIgnoreCase("deny") || args[1].equalsIgnoreCase("d")
                && discord.isTeamMember(msg.getMember().getIdLong()))) {
            if (args.length == 3) {
                requestHandler(msg, false);
            }
        }
        else if (args[1].equalsIgnoreCase("list") && discord.isTeamMember(msg.getAuthor().getIdLong())) {
            String[] splitString = new String[0];
            String defaultTitle = "Nickname Request List";
            try {
            result = nickCore.getList(guild);
            System.out.println(nickCore.discordID.size());
            splitString = result.split("\n\n");
            embed.setAsInfo(defaultTitle, result);
            if (msg.getChannelType() == ChannelType.PRIVATE) {
                embed.sendDM(msg.getAuthor());
            }
            else embed.sendToTeamDiscussionChannel(msg.getChannel(), guild.getMember(msg.getAuthor()));

            }
            catch (IllegalArgumentException ex) {
                int index = 0;
                while (index < splitString.length) {
                    embed.setAsInfo(defaultTitle, splitString[index++]);
                    if (msg.getChannelType() == ChannelType.PRIVATE) {
                        embed.sendDM(msg.getAuthor());
                    }
                    else embed.sendToTeamDiscussionChannel(msg.getChannel(), guild.getMember(msg.getAuthor()));
                }
            }
            catch (NullPointerException ex) {
                embed.setAsError("No Pending Requests", ":x: **There's no pending nickname requests**");
                embed.sendToTeamDiscussionChannel(msg.getChannel(), guild.getMember(msg.getAuthor()));
            }
        }
        // /nickname forcechange <Mention or Discord ID> <New Nickname>
        else if ((args[1].equalsIgnoreCase("forcechange") || args[1].equalsIgnoreCase("fch"))) {
            boolean successfulExecution = false;
            String defaultTitle = "Successful Nickname Change";
            String defaultBody = "Member: **<Member>" +
                    "**\nOld Nickname: **<oldNick>" +
                    "**\nNew Nickname: **<newNick>**";
            if (discord.isTeamMember(msg.getAuthor().getIdLong()) && args.length == 4) {
                String oldNickname = "";
                String newNickname = "";
                if (oldNickname == null) oldNickname = "None";
                Member memberInQuestion = null;
                try {
                    memberInQuestion = guild.getMemberById(Long.parseLong(args[2]));
                    oldNickname = memberInQuestion.getNickname();
                    if (oldNickname == null) oldNickname = "None";
                    ignoreNewNickname = true;
                    if (args[3].equalsIgnoreCase("Reset") || args[3].equalsIgnoreCase("reset")) {
                        memberInQuestion.modifyNickname(memberInQuestion.getUser().getName()).queue();
                        newNickname = memberInQuestion.getUser().getName() + " (Their Discord Name)";
                        successfulExecution = true;
                        log.info("Staff Member " + msg.getMember().getEffectiveName() + " successfully forcefully changed " + oldNickname +
                                "'s effective name back to their discord username");
                    }
                    else {
                        memberInQuestion.modifyNickname(args[3]).queue();
                        newNickname = args[3];
                        successfulExecution = true;
                        log.info("Staff Member " + msg.getMember().getEffectiveName() + " successfully forcefully changed " + oldNickname +
                                "'s nickname to " + newNickname);
                    }
                }
                catch (NumberFormatException ex) {
                    if (msg.getMentionedMembers().size() == 1) {
                        memberInQuestion = msg.getMentionedMembers().get(0);
                        oldNickname = memberInQuestion.getNickname();
                        if (oldNickname == null) oldNickname = "None";
                        ignoreNewNickname = true;
                        if (args[3].equalsIgnoreCase("Reset") || args[3].equalsIgnoreCase("reset")) {
                            memberInQuestion.modifyNickname(memberInQuestion.getUser().getName()).queue();
                            newNickname = memberInQuestion.getUser().getName() + " (Their Discord Name)";
                            successfulExecution = true;
                            log.info("Staff Member " + msg.getMember().getEffectiveName() + " successfully forcefully changed " + oldNickname +
                                    "'s effective name back to their discord username");
                        }
                        else {
                            memberInQuestion.modifyNickname(args[3]).queue();
                            newNickname = args[3];
                            successfulExecution = true;
                            log.info("Staff Member " + msg.getMember().getEffectiveName() + " successfully forcefully changed " + oldNickname +
                                    "'s nickname to " + newNickname);
                        }
                    }
                    else {
                        embed.setAsError("Too Many Mentioned Players", "**You have too many mentioned players in this command**" +
                                "\nSyntax Reminder: `/nickname forcechange <Mention or Discord ID> <New Nickname>`");
                        embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
                    }
                }
                catch (NullPointerException ex) {
                    embed.setAsError("Player Not Found", "**This Player Was Not Found in this Discord Server**");
                    embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
                }
                if (successfulExecution) {
                    defaultBody = defaultBody.replace("<Member>", memberInQuestion.getAsMention())
                            .replace("<oldNick>", oldNickname).replace("<newNick>", newNickname);
                    embed.setAsSuccess(defaultTitle, defaultBody);
                    embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
                }
                else ignoreNewNickname = false;
            }
            else if (discord.isTeamMember(msg.getAuthor().getIdLong()) && args.length != 4) {
                embed.setAsError("Incorrect Arguments", "**You did not use the correct number of arguments**" +
                        "\nSyntax Reminder: `/nickname forcechange <Mention or Discord ID> <New Nickname>`");
                embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
            }
            else {
                embed.setAsError("No Permission", "**You Lack Permissions Do That**");
                embed.sendToHelpChannel(msg.getChannel(), guild.getMember(msg.getAuthor()));
            }
        }
    }
    private void requestHandler(Message msg, boolean requestAccepted) throws IOException {
        String[] args = msg.getContentRaw().substring(1).split(" ");
        String result = "";
        long targetDiscordID = 0;
        int targetRequestID = 0;
        if (args.length == 3) {
            int index = 0;
            boolean isMention = false;
            try {
                targetRequestID = Integer.parseInt(args[2]);
                targetDiscordID = nickCore.discordID.get(nickCore.requestID.indexOf(targetRequestID));
                if (requestAccepted) {
                    result = nickCore.acceptRequest(-1, targetRequestID);
                }
                else {
                    result = nickCore.denyRequest(-1, targetRequestID);
                }
            }
            catch (IndexOutOfBoundsException ex) {
                embed.setAsError("Nickname Request Not Found",
                        "**Your nickname request cannot be found**");
                embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
            }
            catch (NumberFormatException ex) {
                isMention = true;
                targetDiscordID = msg.getAuthor().getIdLong();
                if (requestAccepted) {
                    result = nickCore.acceptRequest(targetDiscordID, -1);
                }
                else {
                    result = nickCore.denyRequest(targetDiscordID, -1);
                }
            }
            if (result == null) {
                embed.setAsError("Request Not Found", "**Nickname Request Was Not Found**");
                embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
            }
            else if (isMention) {
                if (result.contains("FATAL ERROR")) {
                    failedIntegrityCheck(msg.getMember(), "Nickname: Request Handler - Mention", msg.getChannel());
                }
                else {
                    String[] getNewNicknameArray = result.split("New Nickname: ");
                    System.out.println(getNewNicknameArray[1]);
                    String getNewNickname = getNewNicknameArray[1].substring(2, getNewNicknameArray[1].lastIndexOf('*') - 1);
                    System.out.println(getNewNickname);
                    if (requestAccepted) {
                        ignoreNewNickname = true;
                        if (getNewNickname == null) {
                            guild.getMemberById(targetDiscordID).modifyNickname(
                                    guild.getMemberById(targetDiscordID).getUser().getName()).queue();
                        }
                        else guild.getMemberById(targetDiscordID).modifyNickname(getNewNickname).queue();
                        embed.setAsSuccess("Successful Nickname Request Acceptance", result);
                        embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
                        String messageToPlayer = "**Your Nickname Request was Accepted** \n " +
                                "Your new name on the Discord Server is now " + getNewNickname;
                        embed.setAsSuccess("Successful Nickname Request Acceptance", messageToPlayer);
                        embed.sendDM(guild.getMemberById(targetDiscordID).getUser());
                    }
                    else {
                        embed.setAsError("Successful Nickname Request Denial", result);
                        embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
                    }
                }
            }
            else {
                if (result.contains("FATAL ERROR")) {
                    failedIntegrityCheck(msg.getMember(), "Nickname: Request Handler - Request ID", msg.getChannel());
                }
                else {
                    String[] getNewNicknameArray = result.split("New Nickname: ");
                    System.out.println(getNewNicknameArray[1]);
                    String getNewNickname = getNewNicknameArray[1].substring(2, getNewNicknameArray[1].lastIndexOf('*') - 1);
                    System.out.println(getNewNickname);
                    if (requestAccepted) {
                        ignoreNewNickname = true;
                        if (getNewNickname == null) {
                            guild.getMemberById(targetDiscordID).modifyNickname(
                                    guild.getMemberById(targetDiscordID).getUser().getName()).queue();
                        }
                        else guild.getMemberById(targetDiscordID).modifyNickname(getNewNickname).queue();
                        embed.setAsSuccess("Successful Nickname Request Acceptance", result);
                        embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
                        String messageToPlayer = "**Your Nickname Request was Accepted** \n " +
                                "Your new name on the Discord Server is now " + getNewNickname;
                        embed.setAsSuccess("Successful Nickname Request Acceptance", messageToPlayer);
                        embed.sendDM(guild.getMemberById(targetDiscordID).getUser());
                    }
                    else {
                        embed.setAsError("Successful Nickname Request Denial", result);
                        embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
                    }
                }
            }
        }
        else {
            embed.setAsError("Invaid Number of Arguements",
            "**You Entered an Invalid Number of Arguements**" +
                    "\n\nUsage: `/nickname accept <Mention or Request ID>`");
            embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
        }
    }

    private boolean inNickRestrictedRole(long targetDiscordID) {
        List<Role> hasRoles = guild.getMemberById(targetDiscordID).getRoles();

        if (hasRoles.isEmpty() || discord.isTeamMember(targetDiscordID)) return false;
        else {
            int index = 0;
            while (index < restrictedRoles.size()) {
                if (hasRoles.contains(restrictedRoles.get(index))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void failedIntegrityCheck(Member author, String cause, MessageChannel channel) throws IOException {
        embed.setAsStop("FATAL ERROR",
                "**Ouch! That Really Didn't Go Well! **" +
                "\n**You may use */restart* to try to restart me. If you don't feel comfortable doing that... " + mainConfig.owner.getAsMention()
                + " has been notified.**" +
                "\n\n**Cause: " + cause + "**" +
                "\n\n**Commands have Been Suspended**");
        embed.sendToTeamDiscussionChannel(channel, author);
        log.fatal("Integrity Check on ArrayList Objects Failed - Cause: " + cause);
        commandsSuspended = true;
    }
}