package Angel.CheckIn.AFKTool;

import Angel.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class AFKCheckManagement extends Timer {
    // AFK Check Variables
    private final Logger log = LogManager.getLogger(AFKCheckManagement.class);
    private final List<AFKCheck> afkChecks = new ArrayList<>();
    private final List<AFKCheck> afkCheckQueue = new ArrayList<>();
    private final List<TextChannel> knownSessionChannels = new ArrayList<>();
    private final List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>();
    private final ScheduledThreadPoolExecutor services = new ScheduledThreadPoolExecutor(20);
    private final Guild guild;
    private final DiscordBotMain discord;
    private final JDA jda;
    private final EmbedEngine embed;
    private final MainConfiguration mainConfig;
    private AFKCheckListEmbed afkCheckListEmbed;
    private final int maxNumberOfAFKChecksPerSession = 3;

    public AFKCheckManagement(Guild guild, JDA jda, DiscordBotMain discord, EmbedEngine embed, MainConfiguration mainConfig) {
        this.guild = guild;
        this.jda = jda;
        this.embed = embed;
        this.mainConfig = mainConfig;
        this.discord = discord;
        services.setRemoveOnCancelPolicy(true);
    }
    //////////////////////////////////////////////////////
    // /afkcheck <Mentions (up to 3)> <Session Channel>
    //////////////////////////////////////////////////////
    public void startNewAfkCheck(Message msg, int length, int mentionOn) {
        String[] args = msg.getContentRaw().substring(1).split(" ");

        if (discord.isTeamMember(msg.getAuthor().getIdLong())) {
            if (msg.getMentionedMembers().size() >= 4) {
                embed.setAsError("Too Many Mentions",
                        "**To Be Honest... if you're going to AFK Check this many people... you should just run a Check-In at this point...**");
                embed.sendToTeamOutput(msg, msg.getAuthor());
            }
            else if (msg.getMentionedMembers().isEmpty()) {
                embed.setAsError("Invalid Syntax",
                        "I need mentions for this command! You may use `" + mainConfig.commandPrefix + "search <name>` to get mentions of players by effective name or even username." +
                                "\n\nSyntax: `" + mainConfig.commandPrefix + "afkcheck <Mentions (Up to 3)> [Session Channel]`" +
                                "\n**[Session Channel] is only required if you use this command outside of a session channel. " +
                                "When I'm not given this argument, I automatically assume that the channel this command was used in is the session channel.**");
                embed.sendToTeamOutput(msg, msg.getAuthor());
            }
            else if (msg.getMentionedChannels().size() >= 2) {
                embed.setAsError("Too Many Channels", "**Hey... there can only be one session channel**");
                embed.sendToTeamOutput(msg, msg.getAuthor());
            }
            else {
                List<Member> masterList = new ArrayList<>();

                int index = 0;

                do {
                    if (getAFKCheckObjByMember(msg.getMentionedMembers().get(index)) == null) {
                        masterList.add(msg.getMentionedMembers().get(index));
                    }
                    else {
                        embed.sendAsMessageEntryObj(new MessageEntry("AFK Check Already Running",
                                ":x: **An AFK Check is already running for " + msg.getMentionedMembers().get(index) + "**",
                                EmbedDesign.ERROR, mainConfig).setChannels(TargetChannelSet.SAME).setOriginalCmd(msg));
                    }
                } while (++index < msg.getMentionedMembers().size());

                masterList.forEach(m -> {
                    AFKCheck afkCheck;
                    if (msg.getMentionedChannels().isEmpty()) {
                        afkCheck = new AFKCheck(m, msg.getMember(), msg.getTextChannel(), length, mentionOn);
                    }
                    else {
                        afkCheck = new AFKCheck(m, msg.getMember(), msg.getMentionedChannels().get(0), length, mentionOn);
                    }
                    queueAFKCheck(msg, afkCheck);
                });
            }
        }
        else {
            embed.setAsError("No Permissions", ":x: **You Have No Permissions to Execute this command!**");
            embed.sendToMemberOutput(msg, msg.getAuthor());
        }
    }

    public void displayStatus(Message msg) {
        if (knownSessionChannels.isEmpty()) {
            embed.setAsError("No Known Session Channels",
                    "Hmm... it appears I don't know of any session channels which means there has never been an AFK Check started since the last time I was restarted.");
        }
        else {
            String defaultOutput = "";
            int index = 0;

            do {
                TextChannel sessionChannel = knownSessionChannels.get(index);
                defaultOutput = defaultOutput.concat(getNumberOfAFKChecksBySessionChannel(sessionChannel) + "/" +
                        maxNumberOfAFKChecksPerSession + " " + sessionChannel.getAsMention());
            } while (++index < knownSessionChannels.size());

            embed.setAsInfo("Status of Session Channels", defaultOutput);
        }
        embed.sendToTeamOutput(msg, msg.getAuthor());
    }

    public void displayAFKCheckList(Message msg) {
        if (!getAFKCheckList().isEmpty()) {
            afkCheckListEmbed = new AFKCheckListEmbed(new MessageEntry("AFK Checks In-Progress", EmbedDesign.INFO, mainConfig, msg, TargetChannelSet.TEAM),
                    "**This Information is current as of using the command:**", getAFKCheckList(), null, this);

            discord.addAsReactionListEmbed(afkCheckListEmbed);
        }
        else {
            embed.setAsError("No Check-Ins Running", ":x: **There are no Check-Ins Running**");
            embed.sendToTeamOutput(msg, msg.getAuthor());
        }
    }

    public boolean reprint(TextChannel channel) {
        int index = 0;
        List<AFKCheck> afkChecks = getAFKChecksBySessionChannel(channel);
        AFKCheck afkCheck = null;

        do {
            if (!afkChecks.get(index).isRemainingMinutesSafe()) {
                afkCheck = afkChecks.get(index);
                break;
            }
        } while (++index < afkChecks.size());

        if (afkCheck != null) {
            AtomicReference<Message> newMessage = new AtomicReference<>();
            afkCheck.getCurrentSessionChannelMsg().delete().queue();
            afkCheck.getSessionChannel().sendMessage(
                    afkCheck.getCurrentSessionChannelMsg().getContentRaw().replace(afkCheck.getMemberMentions(), "#")).queue(newMessage::set);
            newMessage.get().editMessage(newMessage.get().getContentRaw().replace("#", afkCheck.getMemberMentions())).queue();
            return true;
        }
        else {
            return false;
        }
    }

    public void startTimer() {
        log.info("Successfully Started Timer for AFK Checks");
        this.schedule(new TimerTask() {
            @Override
            public void run() {
                while (true) {
                    try {
                        afkChecks.forEach(afk -> {
                            try {Thread.sleep(100);} catch (InterruptedException e) {}
                            MessageEntry entry;
                            if (afk.hasPlayerSuccessfullyCheckedIn()) {
                                entry = new MessageEntry(afk.getMemberNames() + " AFK Check Status",
                                        "**" + afk.getMemberMentions() + " has successfully responded to your posted AFK check with " +
                                                afk.getRemainingTime() + " left on the clock!**",
                                        EmbedDesign.SUCCESS, mainConfig);
                                mainConfig.discussionChannel.sendMessage(afk.getOverseeingStaffMember().getAsMention()).queue();
                                mainConfig.discussionChannel.sendMessageEmbeds(entry.getEmbed()).queue();
                                // Remove Event Listener and Delete from afkCheck List and scheduledFutures List
                                scheduledFutures.remove(afkChecks.indexOf(afk)).cancel(true);
                                jda.removeEventListener(afkChecks.remove(afkChecks.indexOf(afk)));

                                embed.sendAsMessageEntryObj(new MessageEntry("AFK Check Completed",
                                        ":white_check_mark: **You have successfully completed an AFK check!** :white_check_mark:" +
                                        "\n\n***Please bear in mind that there is a rule that you must be attentive to discord " +
                                        "while in our official sessions as per GTA Rule #5.***" +
                                        "\n\n***This does not mean you are out of the woods, the reason why you were AFK checked " +
                                        "was there was a suspicion that you were not obeying that rule, " +
                                        "and it can happen a second time. " +
                                        "So if you would like to idle in GTA Online in order to let your businesses run, " +
                                        "then our advice would be to do so in a solo GTA Online Session***", EmbedDesign.WARNING, mainConfig,
                                        afk.getCheckInMessage(), afk.getSessionChannel()));
                            }
                            else if (afk.hasPlayerFailedCheckIn()) {
                                entry = new MessageEntry(afk.getMemberNames() + " AFK Check Status",
                                        "**" + afk.getMemberMentions() + " has failed to respond to your posted AFK check... I sense a suspension coming on...**",
                                        EmbedDesign.ERROR, mainConfig);
                                mainConfig.discussionChannel.sendMessage(afk.getOverseeingStaffMember().getAsMention()).queue();
                                mainConfig.discussionChannel.sendMessageEmbeds(entry.getEmbed()).queue();
                                // Remove Event Listener and Delete from afkCheck List
                                scheduledFutures.remove(afkChecks.indexOf(afk)).cancel(true);
                                jda.removeEventListener(afkChecks.remove(afkChecks.indexOf(afk)));
                            }
                            else if (afk.isCancelled()) {
                                // Remove Event Listener and Delete from afkCheck List
                                scheduledFutures.remove(afkChecks.indexOf(afk)).cancel(true);
                                jda.removeEventListener(afkChecks.remove(afkChecks.indexOf(afk)));
                            }
                        });
                    }
                    catch (ConcurrentModificationException ex) {}
                    if (afkChecks.isEmpty()) break;
                }

                afkCheckQueue.forEach(queue -> {
                    if (isSessionChannelSafe(queue.getSessionChannel())) {
                        services.scheduleAtFixedRate(queue, 0, 1, TimeUnit.SECONDS);
                        jda.addEventListener(queue);
                        log.info("Successfully Started AFK Check for " + queue.getMemberNames());
                        embed.setAsSuccess("Successfully Started AFK Check",
                                "**An AFK Check was successfully started for " + queue.getMemberMentions() + " from the queue**");
                        embed.sendToTeamOutput(null, queue.getOverseeingStaffMember().getUser());
                        afkChecks.add(afkCheckQueue.remove(afkCheckQueue.indexOf(queue)));
                    }
                });
            }
        }, 0, 1000);
    }
    void cancelAFKCheck(long discordID) {
        AtomicReference<Member> m = new AtomicReference<>();
        guild.retrieveMemberById(discordID).queue(member -> m.set(member));

        AFKCheck cancelledCheck = getAFKCheckObjByMember(m.get());

        cancelledCheck.cancelAFKCheck();

        log.info(cancelledCheck.getOverseeingStaffMember().getEffectiveName() + " just cancelled the AFK Check of " +
                cancelledCheck.getMemberNames() + " with " + cancelledCheck.getRemainingTime() + " left on the clock");
    }
    void refreshAFKCheckListEmbed() {
        discord.updateReactionListEmbed(afkCheckListEmbed, getAFKCheckList());
    }
    private void queueAFKCheck(Message msg, AFKCheck afkCheck) {
        if (isSessionChannelSafe(afkCheck.getSessionChannel())) {
            afkChecks.add(afkCheck);
            scheduledFutures.add(services.scheduleAtFixedRate(afkCheck, 0, 1, TimeUnit.SECONDS));
            jda.addEventListener(afkCheck);
            log.info("Successfully Started AFK Check for " + afkCheck.getMemberNames());
            TargetChannelSet target = TargetChannelSet.SAME;
            if (msg.getTextChannel() == afkCheck.getSessionChannel()) {
                target = TargetChannelSet.TEAM;
            }
            embed.sendAsMessageEntryObj(new MessageEntry("Successfully Started AFK Check",
                    "**An AFK Check was successfully started for *" + afkCheck.getMemberNames() +
                            "***", EmbedDesign.SUCCESS, mainConfig).setChannels(target).setOriginalCmd(msg));
        }
        else {
            afkCheckQueue.add(afkCheck);
            TargetChannelSet target = TargetChannelSet.SAME;
            if (msg.getTextChannel() == afkCheck.getSessionChannel()) {
                target = TargetChannelSet.TEAM;
            }

            embed.sendAsMessageEntryObj(new MessageEntry("AFK Check Queued",
                    "I was not able to start this AFK Check immediately since " +
                            "there's currently an AFK Check in that session channel with less than 5 minutes to go." +
                            "\n\n**It has been queued and will be started when current AFK Check has stopped.**", EmbedDesign.WARNING, mainConfig, msg, target));
        }

        if (!knownSessionChannels.contains(afkCheck.getSessionChannel())) {
            knownSessionChannels.add(afkCheck.getSessionChannel());
        }
    }
    private AFKCheck getAFKCheckObjByMember(Member m) {
        int index = 0;
        if (afkChecks.isEmpty()) return null;
        do {
            if (afkChecks.get(index).getAfkCheckVictims().getIdLong() == m.getIdLong()) {
                return afkChecks.get(index);
            }
        } while (++index < afkChecks.size());
        return null;
    }
    private List<String> getAFKCheckList() {
        List<String> results = new ArrayList<>();
        int index = 0;

        while (index < afkChecks.size()) {
            AFKCheck afk = afkChecks.get(index++);
            results.add("Session Channel: " + afk.getSessionChannel().getAsMention() +
                    "\nPlayer: " + afk.getMemberMentions() +
                    "\nInitiated By: " + afk.getOverseeingStaffMember().getAsMention() +
                    "\nTime Left: **" + afk.getRemainingTime() + "**"
            );
        }
        return results;
    }
    private List<AFKCheck> getAFKChecksBySessionChannel(TextChannel sessionChannel) {
        int index = 0;
        List<AFKCheck> list = new ArrayList<>();

        while (index < afkChecks.size()) {
            if (afkChecks.get(index).getSessionChannel() == sessionChannel) {
                list.add(afkChecks.get(index));
            }
            index++;
        }

        return list;
    }
    private int getNumberOfAFKChecksBySessionChannel(TextChannel sessionChannel) {
        return getAFKChecksBySessionChannel(sessionChannel).size();
    }
    private boolean isSessionChannelSafe(TextChannel channel) {
        List<AFKCheck> checks = getAFKChecksBySessionChannel(channel);
        int index = 0;

        while (index < checks.size()) {
            if (!checks.get(index++).isRemainingMinutesSafe()) {
                return false;
            }
        }
        return true;
    }
}