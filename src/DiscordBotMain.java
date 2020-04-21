import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.DisconnectEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nonnull;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class DiscordBotMain extends ListenerAdapter {
    Core core = new Core();
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


    DiscordBotMain() throws IOException, TimeoutException {
        core.startup();
        this.adminRoleID = core.config.adminRoleID;
        this.staffRoleID = core.config.staffRoleID;
        this.teamRoleID = core.config.teamRoleID;
        this.botAbuseRoleID = core.config.botAbuseRoleID;
    }
    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        MessageChannel discussionChannel = event.getJDA().getTextChannelsByName("team_discussion", false).get(0);
        MessageChannel outputChannel = event.getJDA().getTextChannelsByName("to_channel", false).get(0);
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
                        // For Printing in the Console and in Discord A Bot Abuse role has been removed.
                        System.out.println("[System] Removed Expired Bot Abuse for " +
                                event.getJDA().getGuilds().get(0).getMemberById(removedID).getEffectiveName());
                        try {
                            core.embed.setColor(Color.GREEN);
                            core.embed.setTitle("Successfully Removed Expired Bot Abuse");
                            core.embed.setThumbnail(checkIcon);
                            core.embed.addField("System Message", ":white_check_mark: [System] Removed Expired Bot Abuse for " + removedID, true);
                            outputChannel.sendMessage(core.embed.build()).queue();

                            guild.removeRoleFromMember(removedID,
                                    guild.getRoleById(botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                        }
                        catch (ErrorResponseException ex) {
                            // For Printing in Console and in Discord the Role couldn't be removed because the Discord ID was not found.
                            core.embed.setColor(Color.YELLOW);
                            core.embed.setTitle("Expired Bot Abuse Error");
                            core.embed.setTitle(warningIcon);
                            core.embed.addField("System Message",
                                    "Bot Abuse just expired for " +  event.getJDA().getGuilds().get(0).getMemberById(removedID).getAsMention()
                                            + " and they did not have the Bot Abuse role\n" +
                                            "They either do not Exist in the Discord Server or they simply did not have it", true);
                            outputChannel.sendMessage(core.embed.build()).queue();
                            System.out.println("[System - ERROR] Bot Abuse just expired for " +
                                    event.getJDA().getGuilds().get(0).getMemberById(removedID).getEffectiveName() +
                                    " and they did not have the Bot Abuse role");
                        }
                        core.embed.clearFields();
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
                    core.embed.setColor(Color.BLUE);
                    core.embed.setTitle("Role Scanner Information");
                    core.embed.setThumbnail(infoIcon);
                    while (index < core.currentBotAbusers.size()) {
                        if (serverMembers.contains(guild.getMemberById(core.currentBotAbusers.get(index))) &&
                        !guild.getMemberById(core.currentBotAbusers.get(index)).getRoles().contains(guild.getRoleById(botAbuseRoleID))) {
                            guild.addRoleToMember(guild.getMemberById(core.currentBotAbusers.get(index)),
                                    guild.getRoleById(botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                            core.embed.addField("System Message", "[System - Role Scanner] Added Bot Abuse Role to "
                                    + guild.getMemberById(core.currentBotAbusers.get(index)).getAsMention() +
                                    " because they didn't have the role... and they're supposed to have it." , true);
                            outputChannel.sendMessage(core.embed.build()).queue();
                            System.out.println("[System - Role Scanner] Added Bot Abuse to " +
                                    guild.getMemberById(core.currentBotAbusers.get(index)).getEffectiveName());
                            core.embed.clearFields();
                        }
                        index++;
                    }
                    index = 0;
                    while (index < serverMembers.size()) {
                        if (serverMembers.get(index).getRoles().contains(guild.getRoleById(botAbuseRoleID))
                                && !core.botAbuseIsCurrent(serverMembers.get(index).getIdLong())) {
                            guild.removeRoleFromMember(serverMembers.get(index).getIdLong(),
                                    guild.getRoleById(botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                            core.embed.addField("System Message", "[System - Role Scanner] Removed Bot Abuse Role from "
                                    + serverMembers.get(index).getAsMention() + " because they had the role... " +
                            "and they weren't supposed to have it." , true);
                            outputChannel.sendMessage(core.embed.build()).queue();
                            System.out.println("[System - Role Scanner] Removed Bot Abuse Role from "
                                    + serverMembers.get(index).getEffectiveName());
                            core.embed.clearFields();
                        }
                        index++;
                    }

                }
            }, 0, 900000);
        }
    }
    
    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        MessageChannel outputChannel = event.getGuild().getTextChannelsByName("to_channel", true).get(0);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // If they're supposed to be Bot Abused and they don't have the role on join
        if (core.botAbuseIsCurrent(event.getMember().getIdLong()) && !event.getMember().getRoles().contains(event.getGuild().getRoleById(this.botAbuseRoleID))) {
            event.getGuild().addRoleToMember(event.getMember().getIdLong(),
                    event.getJDA().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
            core.embed.setTitle("Join Event Information");
            core.embed.setColor(Color.BLUE);
            core.embed.setThumbnail(infoIcon);
            core.embed.addField("System Message", "[System - Join Event] Added the Bot Abuse Role to " + event.getMember().getAsMention() +
                    " since according to the data file they should have the Bot Abuse role", true);
            outputChannel.sendMessage(core.embed.build()).queue();
            System.out.println("[System - Join Event] Added Bot Abuse Role to " + event.getMember().getEffectiveName());
        }
        // If they're not supposed to be Bot Abused and they do have the role
        else if (!core.botAbuseIsCurrent(event.getMember().getIdLong()) && event.getMember().getRoles().contains(event.getGuild().getRoleById(this.botAbuseRoleID))) {
            event.getGuild().removeRoleFromMember(event.getMember().getIdLong(),
                    event.getJDA().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
            core.embed.setTitle("Join Event Information");
            core.embed.setColor(Color.BLUE);
            core.embed.setThumbnail(infoIcon);
            core.embed.addField("System Message", "[System - Join Event] Removed the Bot Abuse Role from " + event.getMember().getAsMention() +
                    " since according to the data file they shouldn't have it", true);
            outputChannel.sendMessage(core.embed.build()).queue();
            System.out.println("[System - Join Event] Removed Bot Abuse Role from " + event.getMember().getEffectiveName());
        }
        core.embed.clearFields();
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
        User owner = event.getJDA().getUserById("260562868519436308");
        if (event.getAuthor().isBot()) return;
        if (msg.getContentRaw().charAt(0) == '/')  {
            String[] args = msg.getContentRaw().substring(1).split(" ");
            msg.delete().complete();
            // Command Syntax /botabuse <Mention or Discord ID> <Reason (kick, offline, or staff)> <proof url>
            if (args[0].equals("botabuse") && args.length == 4) {
                if (author.getRoles().contains(event.getGuild().getRoleById(this.teamRoleID)) || author.getUser() == owner) {
                    setBotAbuse(msg);
                }
                else { // If they Don't have the Team role then it returns an error message
                    msg.getChannel().sendMessage(":x: " + msg.getAuthor().getAsMention() + " [System] You Lack Permissions to do that!").queue();
                }
            }
            else if (args[0].equals("botabuse")) {
                msg.getChannel().sendMessage(":x: " + msg.getAuthor().getAsMention() + " [System] You Entered an Invalid Number of Arguments").queue();
            }
            else if (args[0].equals("permbotabuse")) { // /permbotabuse <Mention or Discord ID> [Image]
                if (author.getRoles().contains(event.getGuild().getRoleById(this.teamRoleID))) {
                    permBotAbuse(msg);
                }
                else {
                    msg.getChannel().sendMessage(":x: " + msg.getAuthor().getAsMention() + " [System] You Lack Permissions to do that!").queue();
                }
            }
            else if (args[0].equals("check")) {
                // This handles a /check for someone to check their own Bot Abuse status or someone else's.
                checkCommand(msg);
            }
            else if (args[0].equals("transfer")) { // /transfer <Old Mention or Discord ID> <New Mention or Discord ID>
                if (!msg.getMember().getRoles().contains(guild.getRoleById(this.staffRoleID))
                        || msg.getMember().getRoles().contains(guild.getRoleById(this.adminRoleID))) {
                    try {
                        transferRecords(msg);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    msg.getChannel().sendMessage(":x: " + msg.getAuthor().getAsMention() + " [System] You Don't have Permission to do that!").queue();
                }
            }
            else if (args[0].equals("clear")) {
                if (!msg.getAuthor().getJDA().getRolesByName("Staff", false).isEmpty() ||
                        !msg.getAuthor().getJDA().getRolesByName("Administrators", false).isEmpty() || msg.getAuthor() == owner) {
                    clearCommand(msg);
                }
                else {
                    msg.getChannel().sendMessage(":x: " + msg.getAuthor().getAsMention() + "[System] You Don't have Permission to do that!").queue();
                }
            }
            else if (args[0].equals("checkhistory")) {
                try {
                    checkHistory(msg);
                }
                catch (IllegalStateException ex) {
                    // Take No Action
                }
            }
        }
        else if (msg.getMentionedMembers().contains(msg.getGuild().getMemberById(Long.parseLong("664520352315080727")))) {
            msg.getChannel().sendMessage(":blobnomping:").queue();
        }
        core.embed.clearFields();
    }
    ///////////////////////////////////////////////////////////////////
    // Divider Between Event Handlers and Command Handlers
    //////////////////////////////////////////////////////////////////
    private void setBotAbuse(Message msg) {
        MessageChannel outputChannel = msg.getGuild().getTextChannelsByName("to_channel", false).get(0);
        MessageChannel discussionChannel = msg.getGuild().getTextChannelsByName("team_discussion", false).get(0);

        String[] args = msg.getContentRaw().substring(1).split(" ");

        if (args[1].isEmpty()) {
            msg.getChannel().sendMessage(":x: " + msg.getAuthor().getAsMention() + " [System] I was expecting a target player").queue();
        }
        else if (msg.getMentionedMembers().isEmpty()) {
            try {
                String result = core.setBotAbuse(Long.parseLong(args[1]), false, args[2], args[3], msg.getMember().getAsMention());
                if (result.contains(":white_check_mark:")) {
                    core.embed.setColor(Color.GREEN);
                    core.embed.setTitle("Successful Bot Abuse");
                    core.embed.setThumbnail(checkIcon);
                    core.embed.addField("System Message", result, true);
                    outputChannel.sendMessage(core.embed.build()).queue();
                    msg.getGuild().addRoleToMember(msg.getGuild().getMemberById(Long.parseLong(args[1])),
                            msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                    discussionChannel.sendMessage(":white_check_mark: " + msg.getAuthor().getAsMention() + " Successfully Bot Abused "
                            + msg.getGuild().getMemberById(Long.parseLong(args[1])).getAsMention()).queue();
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                            + msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName());
                }
                else if (result.contains(":x:")) {
                    core.embed.setColor(Color.RED);
                    core.embed.setTitle("Whoops... Something went wrong");
                    core.embed.setThumbnail(errorIcon);
                    core.embed.addField("System Message", result, true);
                    discussionChannel.sendMessage(core.embed.build()).queue();
                }
            }
            catch (NumberFormatException ex) {
                discussionChannel.sendMessage(":x: " + msg.getAuthor().getAsMention() + " [System] The Discord ID cannot contain any letters or special characters").queue();
            }
            catch (NullPointerException ex) {
                core.embed.clearFields();
                core.embed.setColor(Color.YELLOW);
                core.embed.setTitle("Exception Caught but Successful Bot Abuse");
                core.embed.setThumbnail(warningIcon);
                core.embed.addField("System Message", "Caught a NullPointerException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Bot Abuse for that Discord ID to the Database**", true);
                discussionChannel.sendMessage(core.embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                        + args[1]);
            }
            catch (IllegalArgumentException ex) {
                core.embed.clearFields();
                core.embed.setColor(Color.YELLOW);
                core.embed.setTitle("Exception Caught but Successful Bot Abuse");
                core.embed.setThumbnail(warningIcon);
                core.embed.addField("System Message", "Caught a IllegalArgumentException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Bot Abuse for that Discord ID to the Database**", true);
                discussionChannel.sendMessage(core.embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                        + args[1]);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() == 1) {
            try {
                String result = core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                        false, args[2], args[3], msg.getMember().getAsMention());
                msg.getGuild().addRoleToMember(msg.getMentionedMembers().get(0),
                        msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                if (result.contains(":white_check_mark:")) {
                    core.embed.setColor(Color.GREEN);
                    core.embed.setTitle("Successful Bot Abuse");
                    core.embed.setThumbnail(this.checkIcon);
                    core.embed.addField("System Message", result, true);
                    discussionChannel.sendMessage(":white_check_mark: " + msg.getAuthor().getAsMention() + " Successfully Bot Abused " + msg.getMentionedMembers().get(0).getAsMention()).queue();
                    outputChannel.sendMessage(core.embed.build()).queue();
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " Successfully Bot Abused "
                            + msg.getMentionedMembers().get(0).getEffectiveName());
                }
                else if (result.contains(":x:")) {
                    core.embed.setColor(Color.RED);
                    core.embed.setTitle("Whoops... Something went wrong");
                    core.embed.setThumbnail(errorIcon);
                    core.embed.addField("System Message", result, true);
                    discussionChannel.sendMessage(core.embed.build()).queue();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() > 1 ) {
            msg.getChannel().sendMessage(":x: " + msg.getAuthor().getAsMention() + " [System] Too many Target IDs").queue();
        }
    }
    private void permBotAbuse(Message msg) {
        MessageChannel outputChannel = msg.getGuild().getTextChannelsByName("to_channel", false).get(0);
        MessageChannel discussionChannel = msg.getGuild().getTextChannelsByName("team_discussion", false).get(0);
        String[] args = msg.getContentRaw().substring(1).split(" ");

        // If length is 3, then an image url was provided.
        if (msg.getMentionedMembers().isEmpty() && args.length == 3) {
            try {
                core.embed.setColor(Color.GREEN);
                core.embed.setThumbnail(checkIcon);
                core.embed.setTitle("Successful Perm Bot Abuse");
                core.embed.addField("System Message", core.setBotAbuse(Long.parseLong(args[1]),
                        true, "staff", args[2] , msg.getMember().getAsMention()), true);
                outputChannel.sendMessage(core.embed.build()).queue();
                msg.getGuild().addRoleToMember(msg.getGuild().getMemberById(Long.parseLong(args[1])),
                        msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                discussionChannel.sendMessage(msg.getAuthor().getAsMention() + " Permanently Bot Abused " +
                        msg.getGuild().getMemberById(Long.parseLong(args[1])).getAsMention()).queue();
                System.out.println("[System - Admin Override] " + msg.getMember().getEffectiveName() +
                        " Successfully Permanently Bot Abused " + msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                core.embed.setColor(Color.RED);
                core.embed.setThumbnail(errorIcon);
                core.embed.setTitle("Error While Setting Perm Bot Abuse");
                core.embed.addField("System Message",msg.getMember().getAsMention() + " [System] Invalid User ID!", true);
                discussionChannel.sendMessage(core.embed.build()).queue();
            }
            catch (ArrayIndexOutOfBoundsException ex) {
                core.embed.setColor(Color.RED);
                core.embed.setThumbnail(errorIcon);
                core.embed.setTitle("Error While Setting Perm Bot Abuse");
                core.embed.addField("System Message",msg.getMember().getAsMention() + " [System] Invalid Number of Arguments!", true);
                discussionChannel.sendMessage(core.embed.build()).queue();
            }
            catch (NullPointerException ex) {
                core.embed.clearFields();
                core.embed.setColor(Color.YELLOW);
                core.embed.setTitle("Exception Caught but Successful Perm Bot Abuse");
                core.embed.setThumbnail(warningIcon);
                core.embed.addField("System Message", "Caught a NullPointerException" +
                        "\n**[System] The Bot Abuse role could not be added to that Discord ID as they Don't Exist in the Server!**" +
                        "\n **Successfully Added A Perm Bot Abuse for that Discord ID to the Database**", true);
                discussionChannel.sendMessage(core.embed.build()).queue();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() == 1 && args.length == 3) {
            try {
                core.embed.setColor(Color.GREEN);
                core.embed.setThumbnail(checkIcon);
                core.embed.setTitle("Successful Perm Bot Abuse");
                core.embed.addField("System Message", core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                        true, "staff", args[2], msg.getMember().getAsMention()),true);
                outputChannel.sendMessage(core.embed.build()).queue();
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
                core.embed.setColor(Color.GREEN);
                core.embed.setThumbnail(checkIcon);
                core.embed.setTitle("Successful Perm Bot Abuse");
                core.embed.addField("System Message",
                        core.setBotAbuse(Long.parseLong(args[1]), true, "staff", null, msg.getMember().getAsMention()), true);
                outputChannel.sendMessage(core.embed.build()).queue();
                msg.getGuild().addRoleToMember(msg.getGuild().getMemberById(Long.parseLong(args[1])),
                        msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(5, TimeUnit.MILLISECONDS);
                System.out.println("[System - Admin Override] " + msg.getMember().getEffectiveName()
                        + " Successfully Permanently Bot Abused " + msg.getGuild().getMemberById(args[1]).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                core.embed.setColor(Color.RED);
                core.embed.setThumbnail(errorIcon);
                core.embed.setTitle("Error in Setting Perm Bot Abuse");
                core.embed.addField("System Message", msg.getAuthor().getAsMention() + " [System] Invalid User ID!", true);
                discussionChannel.sendMessage(core.embed.build()).queue();
            }
            catch (ArrayIndexOutOfBoundsException ex) {
                core.embed.setColor(Color.RED);
                core.embed.setThumbnail(errorIcon);
                core.embed.setTitle("Error in Setting Perm Bot Abuse");
                core.embed.addField("System Message", msg.getAuthor().getAsMention() + "[System] Invalid Number of Arguements!", true);
                discussionChannel.sendMessage(core.embed.build()).queue();
            }
            catch (NullPointerException ex) {
                core.embed.clearFields();
                core.embed.setTitle("Exception Caught but Successful Perm Bot Abuse");
                core.embed.setColor(Color.YELLOW);
                core.embed.setThumbnail(warningIcon);
                core.embed.addField("System Message", ":white_check_mark: " + msg.getMember().getEffectiveName()
                        + " Permanently Bot Abused " + args[1] + " who does not exist on the Discord Server", true);
                discussionChannel.sendMessage(core.embed.build()).queue();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        else if (msg.getMentionedMembers().size() == 1 && args.length == 2) {
            try {
                core.embed.setColor(Color.GREEN);
                core.embed.setTitle("Successful Perm Bot Abuse");
                outputChannel.sendMessage(core.setBotAbuse(msg.getMentionedMembers().get(0).getIdLong(),
                        true, "staff", null, msg.getMember().getAsMention())).queue();
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
            core.embed.setColor(Color.RED);
            core.embed.setTitle("Fatal Error");
            core.embed.setThumbnail(errorIcon);
            core.embed.addField("System Message", ":x: " + msg.getAuthor().getAsMention() + " [System] Too Many Mentioned Players!", true);
            msg.getChannel().sendMessage(core.embed.build()).queue();
        }
        core.embed.clearFields();
    }
    private void checkCommand(Message msg) {
        MessageChannel discussionChannel = msg.getGuild().getTextChannelsByName("team_discussion", false).get(0);
        MessageChannel helpChannel = msg.getGuild().getTextChannelsByName("help_and_support", false).get(0);

        String[] args = msg.getContentRaw().substring(1).split(" ");
        core.embed.setColor(Color.BLUE);
        core.embed.setTitle("Bot Abuse Information");
        core.embed.setThumbnail(infoIcon);

        if (msg.getChannelType() == ChannelType.PRIVATE) {
            // Take No Action
        }
        // This handles a /check for someone to check their own Bot Abuse status
        else if (msg.getMentionedMembers().isEmpty() && args.length == 1) {
            core.embed.addField("System Message",
                    core.getInfo(msg.getAuthor().getIdLong(), 100, false), true);
            helpChannel.sendMessage("Hey " + msg.getAuthor().getAsMention() + ",").queue();
            helpChannel.sendMessage(core.embed.build()).queue();
            System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked their own Bot Abuse status");
        }
        // This handles if the player opts for a Direct Message instead "/check dm"
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2 && args[1].equals("dm")) {
            PrivateChannel channel = msg.getAuthor().openPrivateChannel().complete();
            core.embed.addField("System Message", core.getInfo(msg.getAuthor().getIdLong(), 100, false), true);
            channel.sendMessage(core.embed.build()).queue();
            System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked their own Bot Abuse status and opted for a DM");
        }
        // /check <Discord ID>
        else if (msg.getMentionedMembers().isEmpty() && msg.getMember().getRoles().contains(msg.getGuild().getRoleById(this.teamRoleID)) && args.length == 2) {
            try {
                core.embed.addField("System Message", core.getInfo(Long.parseLong(args[1]), 100 ,true), true);
                discussionChannel.sendMessage(core.embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked on "+
                        msg.getGuild().getMemberById(Long.parseLong(args[1])) + "'s Bot Abuse Status");
            }
            catch (NumberFormatException ex) {
                core.embed.setColor(Color.RED);
                core.embed.setTitle("Check Info Error");
                core.embed.setThumbnail(errorIcon);
                core.embed.addField("System Message",":x: **Invalid Discord ID**", true);
                discussionChannel.sendMessage(msg.getAuthor().getAsMention());
                discussionChannel.sendMessage(core.embed.build()).queue();
                System.out.println("[System] Team Member " + msg.getMember().getEffectiveName() + " just entered an invalid Discord ID");
            }
        }
        // /check <Mention>
        else if (msg.getMentionedMembers().size() == 1 && msg.getMember().getRoles().contains(msg.getGuild().getRoleById(this.teamRoleID)) && args.length == 2) {
            core.embed.addField("System Message", core.getInfo(msg.getMentionedMembers().get(0).getIdLong(), 100 ,true), true);
            discussionChannel.sendMessage(core.embed.build()).queue();
            System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked on "+
                    msg.getMentionedMembers().get(0).getEffectiveName() + "'s Bot Abuse Status");
        }
        // /check <Timezone Offset>
        else if (msg.getMentionedMembers().isEmpty() && args.length == 2) {
            if (core.checkOffset(args[1])) {
                core.embed.addField("System Message", core.getInfo(msg.getAuthor().getIdLong(), Float.parseFloat(args[1]), false), true);
                helpChannel.sendMessage("Hey " + msg.getAuthor().getAsMention() + ",").queue();
                helpChannel.sendMessage(core.embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() +
                        " just checked on their own Bot Abuse status using TimeZone offset " + args[1]);
            }
            else {
                core.embed.setColor(Color.RED);
                core.embed.setTitle("Check Info Error");
                core.embed.setThumbnail(errorIcon);
                core.embed.addField("System Message",":x: **Invalid Timezone Offset**", true);
                helpChannel.sendMessage(core.embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() + " just entered an invalid TimeZone offset");
            }
        }
        // /check [dm] <Timezone Offset>
        else if (msg.getMentionedMembers().isEmpty() && args.length == 3 && args[1].equals("dm")) {
            PrivateChannel channel = msg.getAuthor().openPrivateChannel().complete();
            if (core.checkOffset(args[2])) {
                core.embed.addField("System Message", core.getInfo(msg.getAuthor().getIdLong(), Float.parseFloat(args[2]), false), true);
                channel.sendMessage(core.embed.build()).queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() +
                        " just checked on their own Bot Abuse status while opting for a DM");
            }
            else {
                helpChannel.sendMessage(":x: " + msg.getAuthor().getAsMention() + " **Invalid Timezone Offset**").queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() +
                        " just entered an invalid Discord ID while opting for a DM");
            }
        }
        // /check <Timezone Offset> <Mention or Discord ID>
        else if (msg.getMember().getRoles().contains(msg.getGuild().getRoleById(this.teamRoleID)) && args.length == 3) {
            if (core.checkOffset(args[1])) {
                if (msg.getMentionedMembers().isEmpty()) {
                    core.embed.addField("System Message",
                            core.getInfo(Long.parseLong(args[2]), Float.parseFloat(args[1]), true), true);
                    discussionChannel.sendMessage(core.embed.build()).queue();
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked on "
                            + msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName()
                            + "'s Bot Abuse Status while using TimeZone offset" + args[1]);
                }
                else if (msg.getMentionedMembers().size() == 1) {
                    discussionChannel.sendMessage(
                            core.getInfo(msg.getMentionedMembers().get(0).getIdLong(), Float.parseFloat(args[1]), true)).queue();
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked on "
                            + msg.getMentionedMembers().get(0).getEffectiveName()
                            + "'s Bot Abuse Status while using TimeZone offset" + args[1]);
                }
            }
            else {
                core.embed.setColor(Color.RED);
                core.embed.setTitle("Check Info Error");
                core.embed.setThumbnail(errorIcon);
                core.embed.addField("System Message", ":x: [System] **Invalid Timezone Offset**", true);
                discussionChannel.sendMessage(core.embed.build()).queue();
                System.out.println("[System] Team Member " + msg.getMember().getEffectiveName() + " just entered an invalid TimeZone offset.");
            }

        }
        else {
            core.embed.setColor(Color.RED);
            core.embed.setTitle("Permission Error");
            core.embed.setThumbnail(errorIcon);
            core.embed.addField("System Message","You Don't have Permission to check on someone else's Bot Abuse status", true);
            helpChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
            helpChannel.sendMessage(core.embed.build()).queue();
        }
    }
    private void clearCommand(Message msg) {
        MessageChannel outputChannel = msg.getGuild().getTextChannelsByName("to_channel", false).get(0);
        MessageChannel discussionChannel = msg.getGuild().getTextChannelsByName("team_discussion", false).get(0);

        String[] args = msg.getContentRaw().substring(1).split(" ");


        int index = 0;
        // This Handles the list of mentioned members
        while (index < msg.getMentionedMembers().size()) {

            // We now check if they have the Bot Abuse role, if they do then it's removed.
            if (msg.getMentionedMembers().get(index).getRoles().contains(msg.getGuild().getRoleById(this.botAbuseRoleID))) {
                msg.getGuild().removeRoleFromMember(msg.getMentionedMembers().get(index).getIdLong(),
                        msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                core.embed.setColor(Color.BLUE);
                core.embed.setTitle("Bot Abuse Role Removed");
                core.embed.setThumbnail(infoIcon);
                core.embed.addField("System Message", "**Successfully Removed Bot Abuse Role from "
                        + msg.getMentionedMembers().get(index).getAsMention() + " as their Records just got Cleared**", true);
                outputChannel.sendMessage(core.embed.build()).queue();
                core.embed.clearFields();
            }
            core.embed.setColor(Color.GREEN);
            core.embed.setThumbnail(checkIcon);
            core.embed.setTitle("Successfully Cleared Records");
            try {
                int clearedRecords = core.clearRecords(msg.getMentionedMembers().get(index).getIdLong());
                if (clearedRecords == 0) {
                    core.embed.setColor(Color.RED);
                    core.embed.setTitle("No Records Cleared");
                    core.embed.setThumbnail(errorIcon);
                    core.embed.addField("System Message","** [System] No Records Found for " + msg.getMentionedMembers().get(0).getAsMention() + "**", true);
                    discussionChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
                    discussionChannel.sendMessage(core.embed.build()).queue();
                }
                else {
                    core.embed.addField("System Message", ":white_check_mark: **[System] Successfully Cleared " +
                            clearedRecords + " Records from " + msg.getMentionedMembers().get(index).getAsMention() + "**", true);
                    outputChannel.sendMessage(core.embed.build()).queue();
                    System.out.println("[System] Successfully Cleared " + clearedRecords + " Records from " + msg.getMentionedMembers().get(index).getUser().getAsTag());
                }
                index++;
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
            core.embed.clearFields();
        }
        index = 0;
        // We check for any plain discord IDs with this, we don't take any action on a NumberFormatException as that would indicate
        // a mention in that argument, which was already handled, so they're ignored
        while (index < args.length) {
            try {
                if (msg.getGuild().getMemberById(Long.parseLong(args[index])).getRoles().contains(msg.getGuild().getRoleById(this.botAbuseRoleID))) {
                    msg.getGuild().removeRoleFromMember(Long.parseLong(args[index]),
                            msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                    core.embed.setColor(Color.BLUE);
                    core.embed.setTitle("Bot Abuse Role Removed");
                    core.embed.setThumbnail(infoIcon);
                    core.embed.addField("System Message", "**Successfully Removed Bot Abuse Role from "
                            + msg.getGuild().getMemberById(Long.parseLong(args[index])).getEffectiveName() + " as their Records just got Cleared**", true);
                }
                int clearedRecords = core.clearRecords(Long.parseLong(args[index]));

                if (clearedRecords == 0) {
                    core.embed.setColor(Color.RED);
                    core.embed.setTitle("No Records Cleared");
                    core.embed.setThumbnail(errorIcon);
                    core.embed.addField("System Message", "**[System] No Records Found for " + args[index] + "**", true);
                    discussionChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
                    discussionChannel.sendMessage(core.embed.build()).queue();
                }
                else {
                    core.embed.setColor(Color.GREEN);
                    core.embed.setThumbnail(checkIcon);
                    core.embed.setTitle("Successfully Cleared Records");
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
                    if (clearedRecords == 0) {
                        core.embed.setColor(Color.RED);
                        core.embed.setTitle("Error in Clearing Records");
                        core.embed.setThumbnail(errorIcon);
                        discussionChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
                        discussionChannel.sendMessage(core.embed.build()).queue();
                    }
                    else {
                        core.embed.addField("System Message", ":white_check_mark: **[System] Successfully Cleared " +
                                clearedRecords + " Records from " + args[index] + "**", true);
                        outputChannel.sendMessage(core.embed.build()).queue();
                        System.out.println("[System] Successfully Cleared " + clearedRecords + " Records from " + args[index]);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
            core.embed.clearFields();
            index++;
        }
    }
    private void transferRecords(Message msg) throws Exception {
        MessageChannel outputChannel = msg.getGuild().getTextChannelsByName("to_channel", false).get(0);
        MessageChannel discussionChannel = msg.getGuild().getTextChannelsByName("team_discussion", false).get(0);

        String[] args = msg.getContentRaw().substring(1).split(" ");

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
                core.embed.addField("System Message",
                        core.transferRecords(msg.getMentionedMembers().get(0).getIdLong(), msg.getMentionedMembers().get(1).getIdLong()), true);
                outputChannel.sendMessage(core.embed.build()).queue();
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
                            core.embed.setColor(Color.YELLOW);
                            core.embed.setTitle("Exception Caught - Player Does Not Exist");
                            core.embed.setThumbnail(warningIcon);
                            core.embed.addField("System Message", "**[System] Could Not Remove the Bot Abuse Role from "
                                    + args[1] + " because they do not exist in the Discord Server**", true );
                            outputChannel.sendMessage(core.embed.build()).queue();
                            core.embed.clearFields();
                        }
                    }
                    core.embed.setColor(Color.GREEN);
                    core.embed.setTitle("Successful Transfer of Records");
                    core.embed.setThumbnail(checkIcon);
                    core.embed.addField("System Message",
                            core.transferRecords(Long.parseLong(args[1]), msg.getMentionedMembers().get(0).getIdLong()), true);
                    outputChannel.sendMessage(core.embed.build()).queue();
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
                            core.embed.setColor(Color.YELLOW);
                            core.embed.setTitle("Exception Caught - Player Does Not Exist");
                            core.embed.setThumbnail(warningIcon);
                            core.embed.addField("System Message", "**[System] Could Not Add the Bot Abuse Role to "
                                    + args[2] + " because they do not exist in the Discord Server**", true);
                            outputChannel.sendMessage(core.embed.build()).queue();
                            core.embed.clearFields();
                        }
                        msg.getGuild().removeRoleFromMember(msg.getMentionedMembers().get(0).getIdLong(),
                                msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                    }
                    core.embed.setColor(Color.GREEN);
                    core.embed.setTitle("Successful Transfer of Records");
                    core.embed.setThumbnail(checkIcon);
                    core.embed.addField("System Message",
                            core.transferRecords(msg.getMentionedMembers().get(0).getIdLong(), Long.parseLong(args[2])), true);
                    outputChannel.sendMessage(core.embed.build()).queue();
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
                        core.embed.setColor(Color.YELLOW);
                        core.embed.setTitle("Exception Caught - Player Does Not Exist");
                        core.embed.setThumbnail(warningIcon);
                        core.embed.addField("System Message", "**[System] Could Not Add the Bot Abuse Role to "
                                + args[2] + " because they do not exist in the Discord Server**", true);
                        outputChannel.sendMessage(core.embed.build()).queue();
                    }
                    try {
                        msg.getGuild().removeRoleFromMember(Long.parseLong(args[1]),
                                msg.getGuild().getRoleById(this.botAbuseRoleID)).completeAfter(50, TimeUnit.MILLISECONDS);
                    }
                    catch (ErrorResponseException ex) {
                        core.embed.setColor(Color.YELLOW);
                        core.embed.setTitle("Exception Caught - Player Does Not Exist");
                        core.embed.setThumbnail(warningIcon);
                        core.embed.addField("System Message", "**[System] Could Not Remove the Bot Abuse Role from "
                                + args[1] + " because they do not exist in the Discord Server**", true);
                        outputChannel.sendMessage(core.embed.build()).queue();
                    }
                }
                core.embed.setColor(Color.GREEN);
                core.embed.setTitle("Successful Transfer of Records");
                core.embed.setThumbnail(checkIcon);
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
                core.embed.setColor(Color.RED);
                core.embed.setTitle("Error while Parsing Transfer Command");
                core.embed.setThumbnail(errorIcon);
                core.embed.addField("System Message",
                        "[System] Invalid Number of Mentions!" +
                                "\nUsage: /transfer <Old Mention or Discord ID> <New Mention or Discord ID>", true);
                msg.getChannel().sendMessage(core.embed.build()).queue();
            }
            core.embed.clearFields();
        }
        else {
            core.embed.setColor(Color.RED);
            core.embed.setTitle("Error while Parsing Transfer Command");
            core.embed.setThumbnail(errorIcon);
            core.embed.addField("System Message",
                    "[System] Invalid Number of Arguments!" +
                            "\nUsage: /transfer <Old Mention or Discord ID> <New Mention or Discord ID>", true);
            msg.getChannel().sendMessage(core.embed.build()).queue();
        }
    }
    private void checkHistory(Message msg) throws IllegalStateException {
        Guild guild = msg.getGuild();
        Member author = guild.getMember(msg.getAuthor());
        MessageChannel outputChannel = msg.getGuild().getTextChannelsByName("to_channel", false).get(0);
        MessageChannel discussionChannel = msg.getGuild().getTextChannelsByName("team_discussion", false).get(0);

        String[] args = msg.getContentRaw().substring(1).split(" ");

        if (msg.getChannelType() == ChannelType.PRIVATE) {
            // Take No Action
        }
        // /checkhistory <Mention or Discord ID>
        else if (author.getRoles().contains(guild.getRoleById(this.teamRoleID)) && args.length == 2) {
            try {
                // If the user provides a Discord ID
                discussionChannel.sendMessage(core.seeHistory(Long.parseLong(args[1]), 100, true)).queue();
                outputChannel.sendMessage(":information_source: **[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                        msg.getGuild().getMemberById(Long.parseLong(args[1])).getAsMention() + "**").queue();
                System.out.println("[System] " + author.getEffectiveName() + " just checked the history of " +
                        msg.getGuild().getMemberById(Long.parseLong(args[1])).getEffectiveName());
            }
            catch (NumberFormatException ex) {
                try {
                    // The code above would throw a NumberFormatException if it's a mention
                    discussionChannel.sendMessage(core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), 100, true)).queue();
                    outputChannel.sendMessage(":information_source: **[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                            msg.getMentionedMembers().get(0).getAsMention() + "**").queue();
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked the history of " +
                            msg.getMentionedMembers().get(0).getEffectiveName());
                }
                // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
                catch (IllegalArgumentException e) {
                    this.lengthyHistory(core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), 100, true), discussionChannel);
                }
            }
            // The Try code would throw a NullPointerException if the Discord ID Provided does not exist on the server.
            catch (NullPointerException f) {
                outputChannel.sendMessage(":information_source: **[System] " + msg.getAuthor().getAsMention() + " just checked the history of " +
                        args[1] + " who currently does not exist within the Discord Server**").queue();
                System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked the history of "  +
                        args[1] + " who currently does not exist within the Discord Server");
            }
            // If the History is longer than 2000 characters, then this code would catch it and the history would be split down into smaller pieces to be sent.
            catch (IllegalArgumentException h) {
                this.lengthyHistory(core.seeHistory(Long.parseLong(args[1]), 100,true), discussionChannel);

            }
            catch (IndexOutOfBoundsException j) {
                discussionChannel.sendMessage("**You shouldn't need to check your own Bot Abuse History... you're a Team Member!**").queue();
            }
        }
        // No Permissions to check on someone elses Bot Abuse history
        else if (args.length > 1 && !author.getRoles().contains(guild.getRoleById(this.teamRoleID))) {
            msg.getChannel().sendMessage(":x: " + msg.getAuthor().getAsMention() +
                    " **[System] You Don't Have Permission to check on someone elses Bot Abuse History**").queue();
            System.out.println("[System] " + msg.getMember().getEffectiveName() +
                    " just tried to check someone elses Bot Abuse History but they did not have permission to");
        }
        // /checkhistory
        // Get the history of the player who used the command.
        else if (args.length == 1) {
            PrivateChannel channel = msg.getAuthor().openPrivateChannel().complete();
            try {
                channel.sendMessage(core.seeHistory(msg.getAuthor().getIdLong(), 100,false)).queue();
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
                    this.lengthyHistory(core.seeHistory(msg.getAuthor().getIdLong(), Float.parseFloat(args[1]), false), channel);
                    System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked their own Bot Abuse History");
                }
                else {
                    msg.getChannel().sendMessage(":x: **Invalid Timezone Offset**").queue();
                }
            }
            catch (IllegalArgumentException ex) {

            }
        }

        // /checkhistory <timeOffset> <Mention or Discord ID>
        else if (args.length == 3 && msg.getAuthor().getJDA().getRoles().contains(guild.getRoleById(this.teamRoleID))) {
            PrivateChannel channel = msg.getAuthor().openPrivateChannel().complete();
            if (core.checkOffset(args[1])) {
                try {
                    if (msg.getMentionedMembers().size() == 1) {
                        channel.sendMessage(core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), Float.parseFloat(args[1]), true)).queue();
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                    else if (msg.getMentionedMembers().isEmpty()) {
                        channel.sendMessage(core.seeHistory(Long.parseLong(args[2]), Float.parseFloat(args[1]), true)).queue();
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName());
                    }
                }
                catch (IllegalArgumentException ex) {
                    try {
                        this.lengthyHistory(core.seeHistory(Long.parseLong(args[2]), Float.parseFloat(args[1]), true), channel);
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getGuild().getMemberById(Long.parseLong(args[2])).getEffectiveName());
                    }
                    catch (NumberFormatException e) {
                        this.lengthyHistory(core.seeHistory(msg.getMentionedMembers().get(0).getIdLong(), Float.parseFloat(args[1]), true), channel);
                        System.out.println("[System] " + msg.getMember().getEffectiveName() + " just checked the history of " +
                                msg.getMentionedMembers().get(0).getEffectiveName());
                    }
                }
            }
        }
        else {
            msg.getChannel().sendMessage(":x: **[System] Something went Seriously wrong when that happened**").queue();
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
                channel.sendMessage(splitString[index]).queue();
            }
            catch (IllegalStateException ex) {
                // Take No Action
            }
            index++;
        }
    }
}