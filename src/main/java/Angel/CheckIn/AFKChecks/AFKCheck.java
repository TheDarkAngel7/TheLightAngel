package Angel.CheckIn.AFKChecks;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class AFKCheck extends ListenerAdapter implements Runnable {
    private final Logger log = LogManager.getLogger(AFKCheck.class);
    private final List<Member> afkChecked;
    private final Member overseeingStaffMember;
    private final TextChannel sessionChannel;
    private Message currentSessionChannelMsg;
    private final int length;
    private final int mentionOn;
    private int minutes;
    private int seconds;
    private boolean afkCheckRunning = true;
    private boolean successfulCheckIn = false;
    private boolean cancelled = false;
    private final String afkCheckMessage =
            "**$** are you not paying attention to discord? Please respond to this message within the next **%** minutes, " +
                    "otherwise we'll have to assume so and you'll be removed from the session.\n" +
            "\n" +
            ":warning: *Everyone else in the session, **do not tell them they're being AFK checked, otherwise that defeats the point***";

    AFKCheck(List<Member> afkCheckVictims, Member overseeingStaffMember, TextChannel sessionChannel, int length, int mentionOn) {
        this.afkChecked = afkCheckVictims;
        this.overseeingStaffMember = overseeingStaffMember;
        this.sessionChannel = sessionChannel;
        this.length = length;
        this.mentionOn = mentionOn;
        this.minutes = length;
        this.seconds = 0;
    }

    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        if (afkChecked.contains(event.getMember()) && event.getMessage().getChannel() == sessionChannel) {
            afkCheckRunning = false;
            successfulCheckIn = true;
        }
    }

    @Override
    public void run() {
        if (afkCheckRunning) {
            if (minutes == length && seconds == 0) {
                sessionChannel.sendMessage(afkCheckMessage.replace("$", getMemberNames()).replace("%", String.valueOf(length))).queue(m -> {
                    currentSessionChannelMsg = m;
                });
                log.info(overseeingStaffMember.getEffectiveName() + " just started an AFK Check on " + getMemberNames() + " for " +
                        length + " minutes");
            }

            if (minutes == mentionOn && seconds == 0) {
                sessionChannel.sendMessage(afkCheckMessage.replace("**$**", getMemberMentions()).replace(String.valueOf(length), String.valueOf(mentionOn))).queue(m -> {
                    currentSessionChannelMsg = m;
                });
                log.info(getMemberNames() + " only has " + mentionOn + " minutes remaining on their AFK Check!");
            }

            if (minutes == 0 && seconds == 0) {
                afkCheckRunning = false;
                log.info(getMemberNames() + " has missed their AFK Check!");

                currentSessionChannelMsg.editMessage(currentSessionChannelMsg.getContentRaw().replace(
                        "\n\n:warning: **Do Not Bump This Message, except with kickvotes or other emergencies**", "")).queue();
            }
            else if (seconds == 0) {
                minutes--;
                seconds = 59;
            }
            else {
                seconds--;
            }
        }
    }

    void cancelAFKCheck() {
        afkCheckRunning = false;
        cancelled = true;
    }

    String getRemainingTime() {
        if (minutes == 0 && seconds == 0) {
            return "0:00";
        }
        else if (seconds < 10) {
            return minutes + ":0" + seconds;
        }
        else {
            return minutes + ":" + seconds;
        }
    }
    String getMemberNames() {
        if (afkChecked.size() == 1) {
            return afkChecked.get(0).getEffectiveName();
        }
        else {
            int index = 1;
            String result = afkChecked.get(0).getEffectiveName();
            do {
                result = result.concat(" & " + afkChecked.get(index).getEffectiveName());
            } while (++index < afkChecked.size());
            return result;
        }
    }

    String getMemberMentions() {
        if (afkChecked.size() == 1) {
            return afkChecked.get(0).getAsMention();
        }
        else {
            int index = 1;
            String result = afkChecked.get(0).getAsMention();
            do {
                result = result.concat(" & " + afkChecked.get(index).getAsMention());
            } while (++index < afkChecked.size());
            return result;
        }
    }

    List<Member> getAfkCheckVictims() {
        return afkChecked;
    }
    Member getOverseeingStaffMember() {
        return overseeingStaffMember;
    }
    TextChannel getSessionChannel() {
        return sessionChannel;
    }
    boolean isCancelled() {
        return cancelled;
    }
    boolean hasPlayerSuccessfullyCheckedIn() {
        return successfulCheckIn && !afkCheckRunning && !isCancelled();
    }
    boolean hasPlayerFailedCheckIn() {
        return !successfulCheckIn && !afkCheckRunning && !isCancelled();
    }
    boolean isRemainingMinutesSafe() {
        return minutes >= mentionOn;
    }
    Message getCurrentSessionChannelMsg() {
        return currentSessionChannelMsg;
    }
}