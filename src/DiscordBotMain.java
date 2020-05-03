import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.DisconnectEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nonnull;
import java.awt.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class DiscordBotMain extends ListenerAdapter {
    Core core = new Core();
    EmbedBuilder embed = new EmbedBuilder();
    EmbedBuilder timerEmbed = new EmbedBuilder();
    EmbedBuilder timerEmbed2 = new EmbedBuilder();
    Member owner;
    private int timerRunning = 0;
    // Image Background Hex: #2F3136
    String checkIcon = "https://i.imgur.com/bakLhaw.png";
    String warningIcon = "https://i.imgur.com/5SD8jxX.png";
    String errorIcon = "https://i.imgur.com/KmZRhnK.png";
    String infoIcon = "https://i.imgur.com/WM8qFWT.png";
    String adminRoleID;
    String staffRoleID;
    String teamRoleID;
    String botAbuseRoleID;
    String teamChannelID;
    String helpChannelID;
    String logchannelID;

    DiscordBotMain() throws IOException, TimeoutException {
        core.startup(false);
        this.adminRoleID = core.config.adminRoleID;
        this.staffRoleID = core.config.staffRoleID;
        this.teamRoleID = core.config.teamRoleID;
        this.botAbuseRoleID = core.config.botAbuseRoleID;
        this.teamChannelID = core.config.teamChannel;
        this.helpChannelID = core.config.helpChannel;
        this.logchannelID = core.config.logChannel;
    }
    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        MessageChannel discussionChannel = event.getJDA().getTextChannelById(teamChannelID);
        MessageChannel outputChannel = event.getJDA().getTextChannelById(logchannelID);
        this.owner = event.getJDA().getGuilds().get(0).getMemberById("260562868519436308");
        try {
            System.out.println("[System] TheLightAngel is Ready!");
            discussionChannel.sendMessage(":wave: Hey Folks! I'm Ready To Fly!").queue();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        if (timerRunning == 0) {
            timerRunning = 1;
            String botAbuseRoleID = this.botAbuseRoleID;
            Guild guild = event.getJDA().getGuilds().get(0);
            System.out.println("[System] Timer is Running");
            Timer timer = new Timer();
            Timer timer2 = new Timer();
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
                            System.out.println("[System] Removed Expired Bot Abuse for " +
                                    event.getJDA().getGuilds().get(0).getMemberById(removedID).getEffectiveName());
                            timerEmbed.setColor(Color.GREEN);
                            timerEmbed.setTitle("Successfully Removed Expired Bot Abuse");
                            timerEmbed.setThumbnail(checkIcon);
                            timerEmbed.addField("System Message", ":white_check_mark: [System] Removed Expired Bot Abuse for " + removedID, true);
                            outputChannel.sendMessage(timerEmbed.build()).queue();

                            guild.removeRoleFromMember(removedID,
                                    guild.getRoleById(botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                        }
                        catch (ErrorResponseException ex) {
                            // For Printing in Console and in Discord the Role couldn't be removed because the Discord ID was not found.
                            timerEmbed.setColor(Color.YELLOW);
                            timerEmbed.setTitle("Expired Bot Abuse Error");
                            timerEmbed.setTitle(warningIcon);
                            timerEmbed.addField("System Message",
                                    "Bot Abuse just expired for " +  event.getJDA().getGuilds().get(0).getMemberById(removedID).getAsMention()
                                            + " and they did not have the Bot Abuse role\n" +
                                            "They either do not Exist in the Discord Server or they simply did not have it", true);
                            outputChannel.sendMessage(timerEmbed.build()).queue();
                            System.out.println("[System - ERROR] Bot Abuse just expired for " +
                                    event.getJDA().getGuilds().get(0).getMemberById(removedID).getEffectiveName() +
                                    " and they did not have the Bot Abuse role");
                        }
                        catch (NullPointerException ex) {
                            System.out.println("[System] Removed Expired Bot Abuse for " +
                                    removedID);
                            timerEmbed.setColor(Color.YELLOW);
                            timerEmbed.setTitle("Expired Bot Abuse Error");
                            timerEmbed.setTitle(warningIcon);
                            timerEmbed.addField("System Message", "Bot Abuse just expired for " + removedID
                            + " but they did not exist in the discord server!", true);
                            outputChannel.sendMessage(timerEmbed.build());
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
                    Guild guild = event.getJDA().getGuilds().get(0);
                    List<Member> serverMembers = guild.getMembers();
                    int index = 0;
                    timerEmbed2.setColor(Color.BLUE);
                    timerEmbed2.setTitle("Role Scanner Information");
                    timerEmbed2.setThumbnail(infoIcon);
                    while (index < core.currentBotAbusers.size()) {
                        if (serverMembers.contains(guild.getMemberById(core.currentBotAbusers.get(index))) &&
                        !guild.getMemberById(core.currentBotAbusers.get(index)).getRoles().contains(guild.getRoleById(botAbuseRoleID))) {
                            guild.addRoleToMember(guild.getMemberById(core.currentBotAbusers.get(index)),
                                    guild.getRoleById(botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                            timerEmbed2.addField("System Message", "[System - Role Scanner] Added Bot Abuse Role to "
                                    + guild.getMemberById(core.currentBotAbusers.get(index)).getAsMention() +
                                    " because they didn't have the role... and they're supposed to have it." , true);
                            outputChannel.sendMessage(timerEmbed.build()).queue();
                            System.out.println("[System - Role Scanner] Added Bot Abuse to " +
                                    guild.getMemberById(core.currentBotAbusers.get(index)).getEffectiveName());
                            timerEmbed2.clearFields();
                        }
                        index++;
                    }
                    index = 0;
                    while (index < serverMembers.size()) {
                        if (serverMembers.get(index).getRoles().contains(guild.getRoleById(botAbuseRoleID))
                                && !core.botAbuseIsCurrent(serverMembers.get(index).getIdLong())) {
                            guild.removeRoleFromMember(serverMembers.get(index).getIdLong(),
                                    guild.getRoleById(botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                            timerEmbed2.addField("System Message", "[System - Role Scanner] Removed Bot Abuse Role from "
                                    + serverMembers.get(index).getAsMention() + " because they had the role... " +
                            "and they weren't supposed to have it." , true);
                            outputChannel.sendMessage(timerEmbed2.build()).queue();
                            System.out.println("[System - Role Scanner] Removed Bot Abuse Role from "
                                    + serverMembers.get(index).getEffectiveName());
                            timerEmbed2.clearFields();
                        }
                        index++;
                    }

                }
            }, 0, 900000);
        }
    }
    
    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        MessageChannel outputChannel = event.getGuild().getTextChannelById(logchannelID);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // If they're supposed to be Bot Abused and they don't have the role on join
        if (core.botAbuseIsCurrent(event.getMember().getIdLong()) && !event.getMember().getRoles().contains(event.getGuild().getRoleById(this.botAbuseRoleID))) {
            event.getGuild().addRoleToMember(event.getMember().getIdLong(),
                    event.getJDA().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
            embed.setTitle("Join Event Information");
            embed.setColor(Color.BLUE);
            embed.setThumbnail(infoIcon);
            embed.addField("System Message", "[System - Join Event] Added the Bot Abuse Role to " + event.getMember().getAsMention() +
                    " since according to the data file they should have the Bot Abuse role", true);
            outputChannel.sendMessage(embed.build()).queue();
            System.out.println("[System - Join Event] Added Bot Abuse Role to " + event.getMember().getEffectiveName());
        }
        // If they're not supposed to be Bot Abused and they do have the role
        else if (!core.botAbuseIsCurrent(event.getMember().getIdLong()) && event.getMember().getRoles().contains(event.getGuild().getRoleById(this.botAbuseRoleID))) {
            event.getGuild().removeRoleFromMember(event.getMember().getIdLong(),
                    event.getJDA().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
            embed.setTitle("Join Event Information");
            embed.setColor(Color.BLUE);
            embed.setThumbnail(infoIcon);
            embed.addField("System Message", "[System - Join Event] Removed the Bot Abuse Role from " + event.getMember().getAsMention() +
                    " since according to the data file they shouldn't have it", true);
            outputChannel.sendMessage(embed.build()).queue();
            System.out.println("[System - Join Event] Removed Bot Abuse Role from " + event.getMember().getEffectiveName());
        }
        embed.clearFields();
    }
    @Override
    public void onDisconnect(@Nonnull DisconnectEvent event) {
        try {
            System.out.println("[System] Disconnected... Saving Data...");
            core.writeArrayData();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        Message msg = event.getMessage();
        Guild guild = event.getGuild();
        Member author = event.getGuild().getMember(msg.getAuthor());
        MessageChannel helpChannel = event.getGuild().getTextChannelById(helpChannelID);
        MessageChannel discussionChannel = event.getGuild().getTextChannelById(teamChannelID);
        if (event.getAuthor().isBot() || msg.getChannelType() == ChannelType.PRIVATE) return;
        if (msg.getContentRaw().charAt(0) == '/')  {
            String[] args = msg.getContentRaw().substring(1).split(" ");
            // Command Syntax /botabuse <Mention or Discord ID> <Reason (kick, offline, or staff)> <proof url>
            if (args[0].equalsIgnoreCase("botabuse")) {
                if ((author.getRoles().contains(event.getGuild().getRoleById(this.teamRoleID)) || author == owner) &&
                        (args.length == 3 || args.length == 4)) {
                    setBotAbuse(msg);
                }
                else if ((args.length < 3 || args.length > 4) && author.getRoles().contains(guild.getRoleById(this.teamRoleID))) {
                    embed.setTitle("Error - Invalid Arguements");
                    embed.setColor(Color.RED);
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message",
                            ":x: " + msg.getAuthor().getAsMention() + " [System] You Entered an Invalid Number of Arguments", true);
                    discussionChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                }
                else { // If they Don't have the Team role then it returns an error message
                    embed.setTitle("Error - No Permissions");
                    embed.setColor(Color.RED);
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", ":x: "
                            + msg.getAuthor().getAsMention() + " [System] You Lack Permissions to do that!", true);
                    helpChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    helpChannel.sendMessage(embed.build()).queue();
                }
            }
            else if (args[0].equalsIgnoreCase("permbotabuse")) { // /permbotabuse <Mention or Discord ID> [Image]
                if (author.getRoles().contains(event.getGuild().getRoleById(this.staffRoleID)) ||
                        author.getRoles().contains(event.getGuild().getRoleById(this.adminRoleID)) || author == owner) {
                    permBotAbuse(msg);
                }
                else {
                    embed.setTitle("Error - No Permissions");
                    embed.setColor(Color.RED);
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", ":x: "
                            + msg.getAuthor().getAsMention() + " [System] You Lack Permissions to do that!", true);
                    helpChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    helpChannel.sendMessage(embed.build()).queue();
                }
            }
            else if (args[0].equalsIgnoreCase("undo")) {
                if (author.getRoles().contains(guild.getRoleById(this.teamRoleID))) {
                    undoCommand(msg);
                }
                else {
                    embed.setTitle("Error - No Permissions");
                    embed.setColor(Color.RED);
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", ":x: "
                            + msg.getAuthor().getAsMention() + " [System] You Lack Permissions to do that!", true);
                    helpChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    helpChannel.sendMessage(embed.build()).queue();
                }
            }
            else if (args[0].equalsIgnoreCase("check")) {
                // This handles a /check for someone to check their own Bot Abuse status or someone else's.
                checkCommand(msg);
            }
            else if (args[0].equalsIgnoreCase("transfer")) { // /transfer <Old Mention or Discord ID> <New Mention or Discord ID>
                if (!msg.getMember().getRoles().contains(guild.getRoleById(this.staffRoleID))
                        || msg.getMember().getRoles().contains(guild.getRoleById(this.adminRoleID)) || author == owner) {
                    try {
                        transferRecords(msg);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    embed.setTitle("Error - No Permissions");
                    embed.setColor(Color.RED);
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", ":x: "
                            + msg.getAuthor().getAsMention() + " [System] You Lack Permissions to do that!", true);
                    helpChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    helpChannel.sendMessage(embed.build()).queue();
                }
            }
            else if (args[0].equalsIgnoreCase("clear")) {
                if (!msg.getMember().getRoles().contains(guild.getRoleById(this.staffRoleID))
                        || msg.getMember().getRoles().contains(guild.getRoleById(this.adminRoleID)) || author == owner) {
                    clearCommand(msg);
                }
                else {
                    embed.setTitle("Error - No Permissions");
                    embed.setColor(Color.RED);
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", ":x: "
                            + msg.getAuthor().getAsMention() + " [System] You Lack Permissions to do that!", true);
                    helpChannel.sendMessage("Hey " + msg.getMember().getAsMention() + ",").queue();
                    helpChannel.sendMessage(embed.build()).queue();
                }
            }
            else if (args[0].equalsIgnoreCase("checkhistory")) {
                try {
                    checkHistory(msg);
                }
                catch (IllegalStateException ex) {
                    // Take No Action
                }
            }
            else if ((args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("restart"))
                    && (msg.getMember().getRoles().contains(guild.getRoleById(this.staffRoleID)) ||
                    msg.getMember().getRoles().contains(guild.getRoleById(this.adminRoleID)) || author == owner)) {
                try {
                    System.out.println("[System] Staff Invoked Restart...");
                    core.startup(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if ((args[0].equalsIgnoreCase("botabuse") || args[0].equalsIgnoreCase("permbotabuse"))
                    && msg.getChannel() == discussionChannel && msg.getAttachments().size() == 1) {
                msg.delete().completeAfter(10, TimeUnit.SECONDS);
            }
            else {
                msg.delete().complete();
            }
        }
        else if (msg.getMentionedMembers().contains(msg.getGuild().getSelfMember())) {
            msg.getChannel().sendMessage(":blobnomping:").queue();
        }
        embed.clearFields();
    }
    ///////////////////////////////////////////////////////////////////
    // Divider Between Event Handlers and Command Handlers
    //////////////////////////////////////////////////////////////////
    private void setBotAbuse(Message msg) {
        MessageChannel outputChannel = msg.getGuild().getTextChannelById(logchannelID);
        MessageChannel discussionChannel = msg.getGuild().getTextChannelById(teamChannelID);

        String[] args = msg.getContentRaw().substring(1).split(" ");

        if (args[1].isEmpty()) {
            msg.getChannel().sendMessage(":x: " + msg.getAuthor().getAsMention() + " [System] I was expecting a target player").queue();
        }
        else if (msg.getMentionedMembers().isEmpty()) {
            try {
                if (msg.getAttachments().isEmpty()) {
                    String result = core.setBotAbuse(Long.parseLong(args[1]), false, args[2], args[3], msg.getMember().getAsMention());
                    if (result.contains(":white_check_mark:")) {
                        embed.setColor(Color.GREEN);
                        embed.setTitle("Successful Bot Abuse");
                        embed.setThumbnail(checkIcon);
                        embed.addField("System Message", result, true);
                        outputChannel.sendMessage(embed.build()).queue();
                        msg.getGuild().addRoleToMember(msg.getGuild().getMemberById(Long.parseLong(args[1])),
                                msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                        discussionChannel.sendMessage(":white_check_mark: " + msg.getAuthor().getAsMention() + " Successfully Bot Abused "
                                + msg.getGuild().getMemberById(Long.parseLong(args[1])).getAsMention()).queue();
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setColor(Color.RED);
                        embed.setTitle("Whoops... Something went wrong");
                        embed.setThumbnail(errorIcon);
                        embed.addField("System Message", result, true);
                        discussionChannel.sendMessage(embed.build()).queue();
                    }
                }
                else if (msg.getAttachments().size() == 1 && msg.getChannel() == discussionChannel) {
                    String result = core.setBotAbuse(Long.parseLong(args[1]),
                            false, args[2], msg.getAttachments().get(0).getProxyUrl(), msg.getMember().getAsMention());
                    if (result.contains(":white_check_mark:")) {
                        embed.setColor(Color.GREEN);
                        embed.setTitle("Successful Bot Abuse");
                        embed.setThumbnail(checkIcon);
                        embed.addField("System Message", result, true);
                        outputChannel.sendMessage(embed.build()).queue();
                        msg.getGuild().addRoleToMember(msg.getGuild().getMemberById(Long.parseLong(args[1])),
                                msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                        discussionChannel.sendMessage(":white_check_mark: " + msg.getAuthor().getAsMention() + " Successfully Bot Abused "
                                + msg.getGuild().getMemberById(Long.parseLong(args[1])).getAsMention()).queue();
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName());
                    }
                    else if (result.contains(":x:")) {
                        embed.setColor(Color.RED);
                        embed.setTitle("Whoops... Something went wrong");
                        embed.setThumbnail(errorIcon);
                        embed.addField("System Message", result, true);
                        discussionChannel.sendMessage(embed.build()).queue();
                    }
                }
            }
            catch (NumberFormatException ex) {
                discussionChannel.sendMessage(":x: " + msg.getAuthor().getAsMention() + " [System] The Discord ID cannot contain any letters or special characters").queue();
            }
            catch (NullPointerException ex) {
                embed.clearFields();
                embed.setColor(Color.YELLOW);
                embed.setTitle("Exception Caught but Successful Bot Abuse");
                embed.setThumbnail(warningIcon);
                embed.addField("System Message", "Caught a NullPointerException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Bot Abuse for that Discord ID to the Database**", true);
                discussionChannel.sendMessage(embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                        + args[1]);
            }
            catch (IllegalArgumentException ex) {
                embed.clearFields();
                embed.setColor(Color.YELLOW);
                embed.setTitle("Exception Caught but Successful Bot Abuse");
                embed.setThumbnail(warningIcon);
                embed.addField("System Message", "Caught a IllegalArgumentException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Bot Abuse for that Discord ID to the Database**", true);
                discussionChannel.sendMessage(embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Bot Abused "
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
                            msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                    if (result.contains(":white_check_mark:")) {
                        embed.setColor(Color.GREEN);
                        embed.setTitle("Successful Bot Abuse");
                        embed.setThumbnail(this.checkIcon);
                        embed.addField("System Message", result, true);
                        discussionChannel.sendMessage(":white_check_mark: " + msg.getAuthor().getAsMention() + " Successfully Bot Abused " + msg.getMentionedMembers().get(0).getAsMention()).queue();
                        outputChannel.sendMessage(embed.build()).queue();
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + msg.getMentionedMembers().get(0).getEffectiveName());
                    } else if (result.contains(":x:")) {
                        embed.setColor(Color.RED);
                        embed.setTitle("Whoops... Something went wrong");
                        embed.setThumbnail(errorIcon);
                        embed.addField("System Message", result, true);
                        discussionChannel.sendMessage(embed.build()).queue();
                    }
                }
                else if (msg.getAttachments().size() == 1 && msg.getChannel() == discussionChannel) {
                    String result = core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                            false, args[2], msg.getAttachments().get(0).getProxyUrl(), msg.getMember().getAsMention());
                    msg.getGuild().addRoleToMember(msg.getMentionedMembers().get(0),
                            msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                    if (result.contains(":white_check_mark:")) {
                        embed.setColor(Color.GREEN);
                        embed.setTitle("Successful Bot Abuse");
                        embed.setThumbnail(this.checkIcon);
                        embed.addField("System Message", result, true);
                        discussionChannel.sendMessage(":white_check_mark: " + msg.getAuthor().getAsMention() + " Successfully Bot Abused " + msg.getMentionedMembers().get(0).getAsMention()).queue();
                        outputChannel.sendMessage(embed.build()).queue();
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                                + msg.getMentionedMembers().get(0).getEffectiveName());
                    } else if (result.contains(":x:")) {
                        embed.setColor(Color.RED);
                        embed.setTitle("Whoops... Something went wrong");
                        embed.setThumbnail(errorIcon);
                        embed.addField("System Message", result, true);
                        discussionChannel.sendMessage(embed.build()).queue();
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() > 1 ) {
            embed.setColor(Color.RED);
            embed.setThumbnail(errorIcon);
            embed.setTitle("Error");
            embed.addField("System Message", ":x: [System] Too many Target IDs", true);
            discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
            discussionChannel.sendMessage(embed.build()).queue();
        }
    }
    private void permBotAbuse(Message msg) {
        MessageChannel outputChannel = msg.getGuild().getTextChannelById(logchannelID);
        MessageChannel discussionChannel = msg.getGuild().getTextChannelById(teamChannelID);
        String[] args = msg.getContentRaw().substring(1).split(" ");

        // If length is 3, then an image url was provided.
        if (msg.getMentionedMembers().isEmpty() && args.length == 3) {
            try {
                embed.setColor(Color.GREEN);
                embed.setThumbnail(checkIcon);
                embed.setTitle("Successful Perm Bot Abuse");
                embed.addField("System Message", core.setBotAbuse(Long.parseLong(args[1]),
                        true, "staff", args[2] , msg.getMember().getAsMention()), true);
                outputChannel.sendMessage(embed.build()).queue();
                msg.getGuild().addRoleToMember(msg.getGuild().getMemberById(Long.parseLong(args[1])),
                        msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                discussionChannel.sendMessage(msg.getAuthor().getAsMention() + " Permanently Bot Abused " +
                        msg.getGuild().getMemberById(Long.parseLong(args[1])).getAsMention()).queue();
                System.out.println("[System - Admin Override] " + msg.getMember().getEffectiveName() +
                        " Successfully Permanently Bot Abused " + msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                embed.setColor(Color.RED);
                embed.setThumbnail(errorIcon);
                embed.setTitle("Error While Setting Perm Bot Abuse");
                embed.addField("System Message",msg.getMember().getAsMention() + " [System] Invalid User ID!", true);
                discussionChannel.sendMessage(embed.build()).queue();
            }
            catch (ArrayIndexOutOfBoundsException ex) {
                embed.setColor(Color.RED);
                embed.setThumbnail(errorIcon);
                embed.setTitle("Error While Setting Perm Bot Abuse");
                embed.addField("System Message",msg.getMember().getAsMention() + " [System] Invalid Number of Arguments!", true);
                discussionChannel.sendMessage(embed.build()).queue();
            }
            catch (NullPointerException ex) {
                embed.clearFields();
                embed.setColor(Color.YELLOW);
                embed.setTitle("Exception Caught but Successful Perm Bot Abuse");
                embed.setThumbnail(warningIcon);
                embed.addField("System Message", "Caught a NullPointerException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Perm Bot Abuse for that Discord ID to the Database**", true);
                discussionChannel.sendMessage(embed.build()).queue();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() == 1 && args.length == 3) {
            try {
                embed.setColor(Color.GREEN);
                embed.setThumbnail(checkIcon);
                embed.setTitle("Successful Perm Bot Abuse");
                embed.addField("System Message", core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                        true, "staff", args[2], msg.getMember().getAsMention()),true);
                outputChannel.sendMessage(embed.build()).queue();
                msg.getGuild().addRoleToMember(msg.getMentionedMembers().get(0),
                        msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                System.out.println("[System - Admin Override] " + msg.getMember().getEffectiveName()
                        + " Successfully Permanently Bot Abused " + msg.getMentionedMembers().get(0).getEffectiveName());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2) {
            try {
                embed.setColor(Color.GREEN);
                embed.setThumbnail(checkIcon);
                embed.setTitle("Successful Perm Bot Abuse");
                embed.addField("System Message",
                        core.setBotAbuse(Long.parseLong(args[1]), true, "staff", null, msg.getMember().getAsMention()), true);
                outputChannel.sendMessage(embed.build()).queue();
                msg.getGuild().addRoleToMember(msg.getGuild().getMemberById(Long.parseLong(args[1])),
                        msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                System.out.println("[System - Admin Override] " + msg.getMember().getEffectiveName()
                        + " Successfully Permanently Bot Abused " + msg.getGuild().getMemberById(args[1]).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                embed.setColor(Color.RED);
                embed.setThumbnail(errorIcon);
                embed.setTitle("Error in Setting Perm Bot Abuse");
                embed.addField("System Message", msg.getAuthor().getAsMention() + " [System] Invalid User ID!", true);
                discussionChannel.sendMessage(embed.build()).queue();
            }
            catch (ArrayIndexOutOfBoundsException ex) {
                embed.setColor(Color.RED);
                embed.setThumbnail(errorIcon);
                embed.setTitle("Error in Setting Perm Bot Abuse");
                embed.addField("System Message", msg.getAuthor().getAsMention() + "[System] Invalid Number of Arguements!", true);
                discussionChannel.sendMessage(embed.build()).queue();
            }
            catch (NullPointerException ex) {
                embed.clearFields();
                embed.setTitle("Exception Caught but Successful Perm Bot Abuse");
                embed.setColor(Color.YELLOW);
                embed.setThumbnail(warningIcon);
                embed.addField("System Message", ":white_check_mark: " + msg.getMember().getEffectiveName()
                        + " Permanently Bot Abused " + args[1] + " who does not exist on the Discord Server", true);
                discussionChannel.sendMessage(embed.build()).queue();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() == 1 && args.length == 2) {
            try {
                embed.setColor(Color.GREEN);
                embed.setTitle("Successful Perm Bot Abuse");
                embed.setThumbnail(checkIcon);
                embed.addField("System Message", core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                        true, "staff", null, msg.getMember().getAsMention()), true);
                outputChannel.sendMessage(embed.build()).queue();
                msg.getGuild().addRoleToMember(msg.getMentionedMembers().get(0),
                        msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                System.out.println("[System - Admin Override] " + msg.getMember().getEffectiveName()
                        + " Successfully Permanently Bot Abused " + msg.getMentionedMembers().get(0).getEffectiveName());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            embed.setColor(Color.RED);
            embed.setTitle("Error");
            embed.setThumbnail(errorIcon);
            embed.addField("System Message", ":x: " + msg.getAuthor().getAsMention() + " [System] Too Many Mentioned Players!", true);
            msg.getChannel().sendMessage(embed.build()).queue();
        }
        embed.clearFields();
    }
    private void undoCommand(Message msg) {
        MessageChannel discussionChannel = msg.getGuild().getTextChannelById(teamChannelID);

        String[] args = msg.getContentRaw().substring(1).split(" ");
        embed.setColor(Color.GREEN);
        embed.setTitle("Successful Undo");
        embed.setThumbnail(checkIcon);
        try {
            if (args.length == 1) {
                msg.getGuild().removeRoleFromMember(msg.getGuild().getMemberById(core.discordID.get(core.issuingTeamMember.lastIndexOf(msg.getMember().getAsMention()))),
                        msg.getGuild().getRoleById(this.botAbuseRoleID)).complete();
                String result = core.undoBotAbuse(msg.getMember().getAsMention(), true, 0);
                if (result.contains("FATAL ERROR")) {
                    embed.setColor(Color.RED);
                    embed.setTitle("FATAL ERROR");
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                            " while I reload and then you can try to run that command again**", true);
                    discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                    System.out.println("[System - FATAL ERROR] Data Integiry Check on ArrayList objects Failed - Reloading to Prevent Damage");
                    core.startup(true);
                }
                else if (result.contains(":x:")) {
                    embed.setColor(Color.RED);
                    embed.setTitle("Error While Undoing");
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", result, true);
                    discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                }
                else {
                    embed.addField("System Message", result, true);
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " just undid their last Bot Abuse");
                    discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                }
            }
            else if (args.length == 2 && msg.getMentionedMembers().isEmpty()) {
                msg.getGuild().removeRoleFromMember(Long.parseLong(args[1]),
                        msg.getGuild().getRoleById(this.botAbuseRoleID)).complete();
                String result = core.undoBotAbuse(msg.getMember().getAsMention(), false, Long.parseLong(args[1]));
                if (result.contains("FATAL ERROR")) {
                    embed.setColor(Color.RED);
                    embed.setTitle("FATAL ERROR");
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                            " while I reload and then you can try to run that command again**", true);
                    discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                    System.out.println("[System - FATAL ERROR] Data Integiry Check on ArrayList objects Failed - Reloading to Prevent Damage");
                    core.startup(true);
                }
                else if (result.contains(":x:")) {
                    embed.setColor(Color.RED);
                    embed.setTitle("Error While Undoing");
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", result, true);
                    discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                }
                else {
                    embed.addField("System Message", result, true);
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " just undid the Bot Abuse for "
                    + msg.getGuild().getMemberById(args[1]).getEffectiveName());
                    discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                }
            }
            else if (args.length == 2 && msg.getMentionedMembers().size() == 1) {
                msg.getGuild().removeRoleFromMember(msg.getMentionedMembers().get(0),
                        msg.getGuild().getRoleById(this.botAbuseRoleID)).complete();
                String result = core.undoBotAbuse(msg.getMember().getAsMention(), false, msg.getMentionedMembers().get(0).getIdLong());
                if (result.contains("FATAL ERROR")) {
                    embed.setColor(Color.RED);
                    embed.setTitle("FATAL ERROR");
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                            " while I reload and then you can try to run that command again**", true);
                    discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                    System.out.println("[System - FATAL ERROR] Data Integiry Check on ArrayList objects Failed - Reloading to Prevent Damage");
                    core.startup(true);
                }
                if (result.contains(":x:")) {
                    embed.setColor(Color.RED);
                    embed.setTitle("Error While Undoing");
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", result, true);
                    discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                }
                else {
                    embed.addField("System Message", result, true);
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " just undid the Bot Abuse for "
                            + msg.getMentionedMembers().get(0).getEffectiveName());
                    discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        embed.clearFields();
    }
    private void checkCommand(Message msg) {
        MessageChannel discussionChannel = msg.getGuild().getTextChannelById(teamChannelID);
        MessageChannel helpChannel = msg.getGuild().getTextChannelById(helpChannelID);

        String[] args = msg.getContentRaw().substring(1).split(" ");
        embed.setColor(Color.BLUE);
        embed.setTitle("Bot Abuse Information");
        embed.setThumbnail(infoIcon);

        // This handles a /check for someone to check their own Bot Abuse status
        if (msg.getMentionedMembers().isEmpty() && args.length == 1) {
            String result = core.getInfo(msg.getAuthor().getIdLong(), 100, false);
            if (result.contains(":white_check_mark:")) {
                embed.setColor(Color.GREEN);
                embed.setThumbnail(checkIcon);
                embed.setTitle("You Are Not Bot Abused");
            }
            embed.addField("System Message", result , true);
            helpChannel.sendMessage("Hey " + msg.getAuthor().getAsMention() + ",").queue();
            helpChannel.sendMessage(embed.build()).queue();
            System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked their own Bot Abuse status");
        }
        // This handles if the player opts for a Direct Message instead "/check dm"
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2 && args[1].equalsIgnoreCase("dm")) {
            PrivateChannel channel = msg.getAuthor().openPrivateChannel().complete();
            String result = core.getInfo(msg.getAuthor().getIdLong(), 100, false);
            if (result.contains(":white_check_mark:")) {
                embed.setColor(Color.GREEN);
                embed.setThumbnail(checkIcon);
                embed.setTitle("You Are Not Bot Abused");
            }
            embed.addField("System Message", result, true);
            channel.sendMessage(embed.build()).queue();
            System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked their own Bot Abuse status and opted for a DM");
        }
        // /check <Discord ID>
        else if (msg.getMentionedMembers().isEmpty() && msg.getMember().getRoles().contains(msg.getGuild().getRoleById(this.teamRoleID)) && args.length == 2) {
            try {
                String result = core.getInfo(Long.parseLong(args[1]), 100 ,true);
                if (result.contains(":white_check_mark:")) {
                    embed.setColor(Color.RED);
                    embed.setThumbnail(errorIcon);
                    embed.setTitle("Player Not Bot Abused");
                    embed.addField("System Message", ":x: **This Player is Not Bot Abused**", true);
                }
                else {
                    embed.addField("System Message", result, true);
                }
                discussionChannel.sendMessage(embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked on "+
                        msg.getGuild().getMemberById(Long.parseLong(args[1])) + "'s Bot Abuse Status");
            }
            catch (NumberFormatException ex) {
                embed.setColor(Color.RED);
                embed.setTitle("Check Info Error");
                embed.setThumbnail(errorIcon);
                embed.addField("System Message",":x: **Invalid Discord ID**", true);
                discussionChannel.sendMessage(msg.getAuthor().getAsMention());
                discussionChannel.sendMessage(embed.build()).queue();
                System.out.println("[System] Team Member " + msg.getMember().getEffectiveName() + " just entered an invalid Discord ID");
            }
        }
        // /check <Mention>
        else if (msg.getMentionedMembers().size() == 1 && msg.getMember().getRoles().contains(msg.getGuild().getRoleById(this.teamRoleID)) && args.length == 2) {
            String result = core.getInfo(msg.getMentionedMembers().get(0).getIdLong(), 100 ,true);
            if (result.contains(":white_check_mark:")) {
                embed.setColor(Color.RED);
                embed.setThumbnail(errorIcon);
                embed.setTitle("Player Not Bot Abused");
                embed.addField("System Message", ":x: **This Player is Not Bot Abused**", true);
            }
            else {
                embed.addField("System Message", result, true);
            }
            discussionChannel.sendMessage(embed.build()).queue();
            System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked on "+
                    msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status");
        }
        // /check <Timezone Offset>
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2) {
            if (core.checkOffset(args[1])) {
                String result = core.getInfo(msg.getAuthor().getIdLong(), Float.parseFloat(args[1]), false);
                if (result.contains(":white_check_mark:")) {
                    embed.setColor(Color.GREEN);
                    embed.setThumbnail(checkIcon);
                    embed.setTitle("You Are Not Bot Abused");
                }
                embed.addField("System Message", result, true);
                helpChannel.sendMessage("Hey " + msg.getAuthor().getAsMention() + ",").queue();
                helpChannel.sendMessage(embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() +
                        " just checked on their own Bot Abuse status using TimeZone offset " + args[1]);
            }
            else {
                embed.setColor(Color.RED);
                embed.setTitle("Check Info Error");
                embed.setThumbnail(errorIcon);
                embed.addField("System Message",":x: **Invalid Timezone Offset**", true);
                helpChannel.sendMessage(embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() + " just entered an invalid TimeZone offset");
            }
        }
        // /check [dm] <Timezone Offset>
        else if (msg.getMentionedMembers().isEmpty() && args.length == 3 && args[1].equalsIgnoreCase("dm")) {
            PrivateChannel channel = msg.getAuthor().openPrivateChannel().complete();
            if (core.checkOffset(args[2])) {
                String result = core.getInfo(msg.getAuthor().getIdLong(), Float.parseFloat(args[2]), false);
                if (result.contains(":white_check_mark:")) {
                    embed.setColor(Color.GREEN);
                    embed.setThumbnail(checkIcon);
                    embed.setTitle("You Are Not Bot Abused");
                }
                embed.addField("System Message", result, true);
                channel.sendMessage(embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() +
                        " just checked on their own Bot Abuse status while opting for a DM");
            }
            else {
                embed.setColor(Color.RED);
                embed.setThumbnail(errorIcon);
                embed.addField("System Message", ":x: " + msg.getAuthor().getAsMention() + " **Invalid Timezone Offset**", true);
                helpChannel.sendMessage(embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() +
                        " just entered an invalid Discord ID while opting for a DM");
            }
        }
        // /check <Timezone Offset> <Mention or Discord ID>
        else if (msg.getMember().getRoles().contains(msg.getGuild().getRoleById(this.teamRoleID)) && args.length == 3) {
            if (core.checkOffset(args[1])) {
                if (msg.getMentionedMembers().isEmpty()) {
                    String result =  core.getInfo(Long.parseLong(args[2]), Float.parseFloat(args[1]), true);
                    if (result.contains(":white_check_mark:")) {
                        embed.setColor(Color.RED);
                        embed.setThumbnail(errorIcon);
                        embed.setTitle("Player Not Bot Abused");
                        embed.addField("System Message", ":x: **This Player is Not Bot Abused**", true);
                    }
                    else {
                        embed.addField("System Message", result, true);
                    }
                    discussionChannel.sendMessage(embed.build()).queue();
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked on "
                            + msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName()
                            + "'s Bot Abuse Status while using TimeZone offset " + args[1]);
                }
                else if (msg.getMentionedMembers().size() == 1) {
                    String result = core.getInfo(msg.getMentionedMembers().get(0).getIdLong(), Float.parseFloat(args[1]), true);
                    if (result.contains(":white_check_mark:")) {
                        embed.setColor(Color.RED);
                        embed.setThumbnail(errorIcon);
                        embed.setTitle("Player Not Bot Abused");
                        embed.addField("System Message", ":x: **This Player is Not Bot Abused**", true);
                    }
                    else {
                        embed.addField("System Message", result, true);
                    }
                    discussionChannel.sendMessage(embed.build()).queue();
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked on "
                            + msg.getMentionedMembers().get(0).getEffectiveName()
                            + "'s Bot Abuse Status while using TimeZone offset " + args[1]);
                }
            }
            else {
                embed.setColor(Color.RED);
                embed.setTitle("Check Info Error");
                embed.setThumbnail(errorIcon);
                embed.addField("System Message", ":x: [System] **Invalid Timezone Offset**", true);
                discussionChannel.sendMessage(embed.build()).queue();
                System.out.println("[System] Team Member " + msg.getMember().getEffectiveName() + " just entered an invalid TimeZone offset.");
            }

        }
        else {
            embed.setColor(Color.RED);
            embed.setTitle("Permission Error");
            embed.setThumbnail(errorIcon);
            embed.addField("System Message","You Don't have Permission to check on someone else's Bot Abuse status", true);
            helpChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
            helpChannel.sendMessage(embed.build()).queue();
        }
    }
    private void clearCommand(Message msg) {
        MessageChannel outputChannel = msg.getGuild().getTextChannelById(logchannelID);
        MessageChannel discussionChannel = msg.getGuild().getTextChannelById(teamChannelID);

        String[] args = msg.getContentRaw().substring(1).split(" ");


        int index = 0;
        // This Handles the list of mentioned members
        while (index < msg.getMentionedMembers().size()) {

            // We now check if they have the Bot Abuse role, if they do then it's removed.
            if (msg.getMentionedMembers().get(index).getRoles().contains(msg.getGuild().getRoleById(this.botAbuseRoleID))) {
                msg.getGuild().removeRoleFromMember(msg.getMentionedMembers().get(index).getIdLong(),
                        msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                embed.setColor(Color.BLUE);
                embed.setTitle("Bot Abuse Role Removed");
                embed.setThumbnail(infoIcon);
                embed.addField("System Message", "**Successfully Removed Bot Abuse Role from "
                        + msg.getMentionedMembers().get(index).getAsMention() + " as their Records just got Cleared**", true);
                outputChannel.sendMessage(embed.build()).queue();
                embed.clearFields();
            }
            embed.setColor(Color.GREEN);
            embed.setThumbnail(checkIcon);
            embed.setTitle("Successfully Cleared Records");
            try {
                int clearedRecords = core.clearRecords(msg.getMentionedMembers().get(index).getIdLong());
                if (clearedRecords == -1) {
                    embed.setColor(Color.RED);
                    embed.setTitle("FATAL ERROR");
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                            " while I reload and then you can try to run that command again**", true);
                    discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                    System.out.println("[System - FATAL ERROR] Data Integiry Check on ArrayList objects Failed - Reloading to Prevent Damage");
                    core.startup(true);
                    break;
                }
                else if (clearedRecords == 0) {
                    embed.setColor(Color.RED);
                    embed.setTitle("No Records Cleared");
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message","** [System] No Records Found for " + msg.getMentionedMembers().get(0).getAsMention() + "**", true);
                    discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                }
                else {
                    embed.addField("System Message", ":white_check_mark: **[System] Successfully Cleared " +
                            clearedRecords + " Records from " + msg.getMentionedMembers().get(index).getAsMention() + "**", true);
                    outputChannel.sendMessage(embed.build()).queue();
                    System.out.println("[System] Successfully Cleared " + clearedRecords + " Records from " + msg.getMentionedMembers().get(index).getUser().getAsTag());
                }
                index++;
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
            embed.clearFields();
        }
        index = 0;
        // We check for any plain discord IDs with this, we don't take any action on a NumberFormatException as that would indicate
        // a mention in that argument, which was already handled, so they're ignored
        while (index < args.length) {
            embed.setColor(Color.GREEN);
            embed.setThumbnail(checkIcon);
            embed.setTitle("Successfully Cleared Records");
            try {
                if (msg.getGuild().getMemberById(Long.parseLong(args[index])).getRoles().contains(msg.getGuild().getRoleById(this.botAbuseRoleID))) {
                    msg.getGuild().removeRoleFromMember(Long.parseLong(args[index]),
                            msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                    embed.setColor(Color.BLUE);
                    embed.setTitle("Bot Abuse Role Removed");
                    embed.setThumbnail(infoIcon);
                    embed.addField("System Message", "**Successfully Removed Bot Abuse Role from "
                            + msg.getGuild().getMemberById(Long.parseLong(args[index])).getEffectiveName() + " as their Records just got Cleared**", true);
                }
                int clearedRecords = core.clearRecords(Long.parseLong(args[index]));
                if (clearedRecords == -1) {
                    embed.setColor(Color.RED);
                    embed.setTitle("FATAL ERROR");
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                            " while I reload and then you can try to run that command again**", true);
                    discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                    System.out.println("[System - FATAL ERROR] Data Integiry Check on ArrayList objects Failed - Reloading to Prevent Damage");
                    core.startup(true);
                    break;
                }
                if (clearedRecords == 0) {
                    embed.setColor(Color.RED);
                    embed.setTitle("No Records Cleared");
                    embed.setThumbnail(errorIcon);
                    embed.addField("System Message", "**[System] No Records Found for " + args[index] + "**", true);
                    discussionChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
                    discussionChannel.sendMessage(embed.build()).queue();
                }
                else {
                    embed.setColor(Color.GREEN);
                    embed.setThumbnail(checkIcon);
                    embed.setTitle("Successfully Cleared Records");
                    outputChannel.sendMessage(":white_check_mark: **[System] Successfully Cleared " +
                            clearedRecords + " Records from " + msg.getGuild().getMemberById(Long.parseLong(args[index])).getAsMention()).queue();
                    System.out.println("[System] Successfully Cleared " + clearedRecords + " Records from " + msg.getGuild().getMemberById(Long.parseLong(args[index])).getAsMention() + "**");
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
                        embed.setColor(Color.RED);
                        embed.setTitle("FATAL ERROR");
                        embed.setThumbnail(errorIcon);
                        embed.addField("System Message", "**Ouch! That Really Didn't Go Well! Give me a few seconds" +
                                " while I reload and then you can try to run that command again**", true);
                        discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                        discussionChannel.sendMessage(embed.build()).queue();
                        System.out.println("[System - FATAL ERROR] Data Integiry Check on ArrayList objects Failed - Reloading to Prevent Damage");
                        core.startup(true);
                        break;
                    }
                    if (clearedRecords == 0) {
                        embed.setColor(Color.RED);
                        embed.setTitle("Error in Clearing Records");
                        embed.setThumbnail(errorIcon);
                        discussionChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
                        discussionChannel.sendMessage(embed.build()).queue();
                    }
                    else {
                        embed.addField("System Message", ":white_check_mark: **[System] Successfully Cleared " +
                                clearedRecords + " Records from " + args[index] + "**", true);
                        outputChannel.sendMessage(embed.build()).queue();
                        System.out.println("[System] Successfully Cleared " + clearedRecords + " Records from " + args[index]);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
            embed.clearFields();
            index++;
        }
    }
    private void transferRecords(Message msg) throws Exception {
        MessageChannel outputChannel = msg.getGuild().getTextChannelById(logchannelID);
        MessageChannel discussionChannel = msg.getGuild().getTextChannelById(teamChannelID);

        String[] args = msg.getContentRaw().substring(1).split(" ");
        embed.setColor(Color.GREEN);
        embed.setThumbnail(checkIcon);
        embed.setTitle("Successful Transfer of Records");

        if (args.length == 3) {
            if (msg.getMentionedMembers().size() == 2) {
                if (core.botAbuseIsCurrent(msg.getMentionedMembers().get(0).getIdLong())) {
                    msg.getGuild().addRoleToMember(msg.getMentionedMembers().get(1).getIdLong(),
                            msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                    msg.getGuild().removeRoleFromMember(msg.getMentionedMembers().get(0).getIdLong(),
                            msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Transferred the Records of "
                            + msg.getMentionedMembers().get(0).getEffectiveName() + " to " + msg.getMentionedMembers().get(1).getEffectiveName());
                }
                embed.addField("System Message",
                        core.transferRecords(msg.getMentionedMembers().get(0).getIdLong(), msg.getMentionedMembers().get(1).getIdLong()), true);
                outputChannel.sendMessage(embed.build()).queue();
            }
            else if (msg.getMentionedMembers().size() == 1) {
                try {
                    // If they provide a Discord ID First and a Mention Last
                    if (core.botAbuseIsCurrent(Long.parseLong(args[1]))) {
                        msg.getGuild().addRoleToMember(msg.getMentionedMembers().get(0).getIdLong(),
                                msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                        try {
                            msg.getGuild().removeRoleFromMember(Long.parseLong(args[1]),
                                    msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                        }
                        catch (ErrorResponseException ex) {
                            embed.setColor(Color.YELLOW);
                            embed.setTitle("Exception Caught - Player Does Not Exist");
                            embed.setThumbnail(warningIcon);
                            embed.addField("System Message", "**[System] Could Not Remove the Bot Abuse Role from "
                                    + args[1] + " because they do not exist in the Discord Server**", true );
                            outputChannel.sendMessage(embed.build()).queue();
                            embed.clearFields();
                        }
                    }
                    embed.setColor(Color.GREEN);
                    embed.setTitle("Successful Transfer of Records");
                    embed.setThumbnail(checkIcon);
                    embed.addField("System Message",
                            core.transferRecords(Long.parseLong(args[1]), msg.getMentionedMembers().get(0).getIdLong()), true);
                    outputChannel.sendMessage(embed.build()).queue();
                    try {
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Transferred the Records of "
                                + msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName()
                                + " to " + msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                    catch (NullPointerException e) {
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Transferred the Records of "
                                + args[1] + " to " +
                                msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                }
                catch (NumberFormatException ex) {
                    // If they provide a mention first and a Discord ID Last
                    if (core.botAbuseIsCurrent(msg.getMentionedMembers().get(0).getIdLong())) {
                        try {
                            msg.getGuild().addRoleToMember(Long.parseLong(args[2]),
                                    msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                        }
                        catch (ErrorResponseException e) {
                            embed.setColor(Color.YELLOW);
                            embed.setTitle("Exception Caught - Player Does Not Exist");
                            embed.setThumbnail(warningIcon);
                            embed.addField("System Message", "**[System] Could Not Add the Bot Abuse Role to "
                                    + args[2] + " because they do not exist in the Discord Server**", true);
                            outputChannel.sendMessage(embed.build()).queue();
                            embed.clearFields();
                        }
                        msg.getGuild().removeRoleFromMember(msg.getMentionedMembers().get(0).getIdLong(),
                                msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                    }
                    embed.setColor(Color.GREEN);
                    embed.setTitle("Successful Transfer of Records");
                    embed.setThumbnail(checkIcon);
                    embed.addField("System Message",
                            core.transferRecords(msg.getMentionedMembers().get(0).getIdLong(), Long.parseLong(args[2])), true);
                    outputChannel.sendMessage(embed.build()).queue();
                    try {
                        System.out.println("[System] " + msg.getAuthor().getAsTag() + " Successfully Transferred the Records of "
                                + msg.getMentionedMembers().get(0).getUser().getAsTag() + " to " +
                                msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName());
                    }
                    catch (NullPointerException e) {
                        System.out.println("[System] " + msg.getAuthor().getAsTag() + " Successfully Transferred the Records of "
                                + msg.getMentionedMembers().get(0).getEffectiveName() +
                                " to " + args[2]);
                    }
                }
            }
            else if (msg.getMentionedMembers().isEmpty()) {
                if (core.botAbuseIsCurrent(Long.parseLong(args[1]))) {
                    try {
                        msg.getGuild().addRoleToMember(Long.parseLong(args[2]),
                                msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                    }
                    catch (ErrorResponseException ex) {
                        embed.setColor(Color.YELLOW);
                        embed.setTitle("Exception Caught - Player Does Not Exist");
                        embed.setThumbnail(warningIcon);
                        embed.addField("System Message", "**[System] Could Not Add the Bot Abuse Role to "
                                + args[2] + " because they do not exist in the Discord Server**", true);
                        outputChannel.sendMessage(embed.build()).queue();
                    }
                    try {
                        msg.getGuild().removeRoleFromMember(Long.parseLong(args[1]),
                                msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                    }
                    catch (ErrorResponseException ex) {
                        embed.setColor(Color.YELLOW);
                        embed.setTitle("Exception Caught - Player Does Not Exist");
                        embed.setThumbnail(warningIcon);
                        embed.addField("System Message", "**[System] Could Not Remove the Bot Abuse Role from "
                                + args[1] + " because they do not exist in the Discord Server**", true);
                        outputChannel.sendMessage(embed.build()).queue();
                    }
                }
                embed.setColor(Color.GREEN);
                embed.setTitle("Successful Transfer of Records");
                embed.setThumbnail(checkIcon);
                outputChannel.sendMessage(core.transferRecords(Long.parseLong(args[1]), Long.parseLong(args[2]))).queue();
                try {
                    System.out.println("[System] " + msg.getAuthor().getAsTag() + " Successfully Transferred the Records of "
                            + msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName() + " to " +
                            msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName());
                }
                catch (NullPointerException ex) {
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Transferred the Records of " + args[1] + " to " +
                            args[2]);
                }

            }
            else {
                embed.setColor(Color.RED);
                embed.setTitle("Error while Parsing Transfer Command");
                embed.setThumbnail(errorIcon);
                embed.addField("System Message",
                        "[System] Invalid Number of Mentions!" +
                                "\nUsage: /transfer <Old Mention or Discord ID> <New Mention or Discord ID>", true);
                discussionChannel.sendMessage(msg.getMember().getAsMention()).queue();
                discussionChannel.sendMessage(embed.build()).queue();
            }
            embed.clearFields();
        }
        else {
            embed.setColor(Color.RED);
            embed.setTitle("Error while Parsing Transfer Command");
            embed.setThumbnail(errorIcon);
            embed.addField("System Message",
                    "[System] Invalid Number of Arguments!" +
                            "\nUsage: /transfer <Old Mention or Discord ID> <New Mention or Discord ID>", true);
            msg.getChannel().sendMessage(embed.build()).queue();
        }
    }
    private void checkHistory(Message msg) throws IllegalStateException {
        Guild guild = msg.getGuild();
        Member author = guild.getMember(msg.getAuthor());
        MessageChannel outputChannel = msg.getGuild().getTextChannelById(logchannelID);
        MessageChannel discussionChannel = msg.getGuild().getTextChannelById(teamChannelID);
        MessageChannel helpChannel = msg.getGuild().getTextChannelById(helpChannelID);

        String[] args = msg.getContentRaw().substring(1).split(" ");
        embed.setColor(Color.BLUE);
        embed.setTitle("Bot Abuse History Information");
        embed.setThumbnail(infoIcon);

        if (msg.getChannelType() == ChannelType.PRIVATE) {
            // Take No Action
        }
        // /checkhistory <Mention or Discord ID>
        else if (author.getRoles().contains(guild.getRoleById(this.teamRoleID)) && args.length == 2) {
            try {
                // If the user provides a Discord ID
                String result = core.seeHistory(Long.parseLong(args[1]), 100, true);
                if (result.contains(":x:")) {
                    embed.setColor(Color.RED);
                    embed.setThumbnail(errorIcon);
                }
                embed.addField("System Message", result, true);
                discussionChannel.sendMessage(embed.build()).queue();
                embed.clearFields();
                embed.addField("System Message", ":information_source: **[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                        msg.getGuild().getMemberById(Long.parseLong(args[1])).getAsMention() + "**", true);
                outputChannel.sendMessage(embed.build()).queue();
                System.out.println("[System] " + author.getEffectiveName() + " just checked the history of " +
                        msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                try {
                    // The code above would throw a NumberFormatException if it's a mention
                    String result = core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), 100, true);
                    if (result.contains(":x:")) {
                        embed.setColor(Color.RED);
                        embed.setThumbnail(errorIcon);
                    }
                    embed.addField("System Message", result, true);
                    discussionChannel.sendMessage(embed.build()).queue();
                    embed.clearFields();
                    embed.addField("System Message", ":information_source: **[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                            msg.getMentionedMembers().get(0).getAsMention() + "**", true);
                    outputChannel.sendMessage(embed.build()).queue();
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked the history of " +
                            msg.getMentionedMembers().get(0).getEffectiveName());
                }
                // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
                catch (IllegalArgumentException e) {
                    this.lengthyHistory(
                            core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), 100, true), discussionChannel);
                }
            }
            // The Try code would throw a NullPointerException if the Discord ID Provided does not exist on the server.
            catch (NullPointerException f) {
                embed.setColor(Color.YELLOW);
                embed.setThumbnail(warningIcon);
                embed.setTitle("Bot Abuse History - Player Does Not Exist in the Server");
                embed.addField("System Message","**[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                        args[1] + " who currently does not exist within the Discord Server**", true);
                outputChannel.sendMessage(embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked the history of "  +
                        args[1] + " who currently does not exist within the Discord Server");
            }
            // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
            catch (IllegalArgumentException h) {
                this.lengthyHistory(core.seeHistory(Long.parseLong(args[1]), 100,true), discussionChannel);
            }
            catch (IndexOutOfBoundsException j) {
                discussionChannel.sendMessage(msg.getMember().getAsMention() +
                        "**You shouldn't need to check your own Bot Abuse History... you're a Team Member!**").queue();
            }
        }
        // /checkhistory
        // Get the history of the player who used the command.
        else if (args.length == 1) {
            embed.setTitle("Your Bot Abuse History");
            PrivateChannel channel = msg.getAuthor().openPrivateChannel().complete();
            try {
                String result = core.seeHistory(msg.getAuthor().getIdLong(), 100,false);
                if (result.contains(":white_check_mark:")) {
                    embed.setColor(Color.GREEN);
                    embed.setThumbnail(checkIcon);
                }
                embed.addField("System Message", result, true);
                channel.sendMessage(embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked their own Bot Abuse History");
            }
            // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
            catch (IllegalArgumentException ex) {
                this.lengthyHistory(core.seeHistory(msg.getAuthor().getIdLong(), 100,false), channel);
            }
        }
        // /checkhistory <timeOffset>
        else if (args.length == 2) {
            PrivateChannel channel =  msg.getAuthor().openPrivateChannel().complete();
            try {
                if (core.checkOffset(args[1])) {
                    String result = core.seeHistory(msg.getAuthor().getIdLong(), Float.parseFloat(args[1]), false);
                    embed.setTitle("Your Bot Abuse History");
                    if (result.contains(":white_check_mark:")) {
                        embed.setColor(Color.GREEN);
                        embed.setThumbnail(checkIcon);
                    }
                    embed.addField("System Message", result, true);
                    channel.sendMessage(embed.build()).queue();
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked their own Bot Abuse History" +
                            " using TimeZone offset " + args[1]);
                }
                else {
                    embed.setColor(Color.RED);
                    embed.setThumbnail(errorIcon);
                    embed.setTitle("Error while Parsing Command");
                    embed.addField("System Message", ":x: **Invalid Timezone Offset**", true);
                    helpChannel.sendMessage(msg.getMember().getAsMention());
                    helpChannel.sendMessage(embed.build()).queue();
                }
            }
            catch (IllegalArgumentException ex) {
                this.lengthyHistory(core.seeHistory(msg.getAuthor().getIdLong(), Float.parseFloat(args[1]), false), channel);
            }
        }

        // /checkhistory <timeOffset> <Mention or Discord ID>
        else if (args.length == 3 && msg.getAuthor().getJDA().getRoles().contains(guild.getRoleById(this.teamRoleID))) {
            PrivateChannel channel = msg.getAuthor().openPrivateChannel().complete();
            embed.setTitle("Bot Abuse History");
            if (core.checkOffset(args[1])) {
                try {
                    if (msg.getMentionedMembers().size() == 1) {
                        String result = core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), Float.parseFloat(args[1]), true);
                        if (result.contains(":x:")) {
                            embed.setColor(Color.RED);
                            embed.setThumbnail(errorIcon);
                        }
                        embed.addField("System Message", result, true);
                        discussionChannel.sendMessage(embed.build()).queue();
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getMentionedMembers().get(0).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                    else if (msg.getMentionedMembers().isEmpty()) {
                        String result = core.seeHistory(Long.parseLong(args[2]), Float.parseFloat(args[1]), true);
                        if (result.contains(":x:")) {
                            embed.setColor(Color.RED);
                            embed.setThumbnail(errorIcon);
                        }
                        embed.addField("System Message", result, true);
                        discussionChannel.sendMessage(embed.build()).queue();
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                }
                catch (IllegalArgumentException ex) {
                    try {
                        this.lengthyHistory(core.seeHistory(Long.parseLong(args[2]), Float.parseFloat(args[1]), true), discussionChannel);
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                    catch (NumberFormatException e) {
                        this.lengthyHistory(
                                core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), Float.parseFloat(args[1]), true), discussionChannel);
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getMentionedMembers().get(0).getEffectiveName() + " using TimeZone offset " + args[1]);
                    }
                }
            }
        }
        // No Permissions to check on someone elses Bot Abuse history
        else if (args.length > 1 && !author.getRoles().contains(guild.getRoleById(this.teamRoleID))) {
            embed.setColor(Color.RED);
            embed.setThumbnail(errorIcon);
            embed.setTitle("Error - No Permissions");
            embed.addField("System Message", ":x: " + msg.getAuthor().getAsMention() +
                    " **[System] You Don't Have Permission to check on someone elses Bot Abuse History**", true);
            helpChannel.sendMessage(msg.getMember().getAsMention()).queue();
            helpChannel.sendMessage(embed.build()).queue();
            System.out.println("[System] " + msg.getMember().getEffectiveName() +
                    " just tried to check someone elses Bot Abuse History but they did not have permission to");
        }
        else {
            embed.setColor(Color.RED);
            embed.setThumbnail(errorIcon);
            embed.setTitle("Fatal Error");
            embed.addField("System Message", ":x: **[System] Something went Seriously wrong when that happened**", true);
            msg.getChannel().sendMessage(embed.build()).queue();
        }
    }
    ///////////////////////////////////////////////////////////
    // Miscellaneous Methods
    ///////////////////////////////////////////////////////////
    private void lengthyHistory(String stringToSplit, MessageChannel channel) {
        String[] splitString = stringToSplit.split("\n\n");
        int index = 0;
        while (index < splitString.length) {
            try {
                embed.addField("System Message", splitString[index], true);
                channel.sendMessage(embed.build()).queue();
                embed.clearFields();
            }
            catch (IllegalStateException ex) {
                // Take No Action
            }
            index++;
        }
    }
}