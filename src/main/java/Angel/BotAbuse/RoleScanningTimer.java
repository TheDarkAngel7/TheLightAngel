package Angel.BotAbuse;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;

class RoleScanningTimer implements Runnable, BotAbuseLogic {
    private final Logger log = LogManager.getLogger(RoleScanningTimer.class);
    private final Guild guild = getGuild();
    private boolean timerRunning = false;

    @Override
    public void run() {
        Thread.currentThread().setName("Role Scanning Timer");
        timerRunning = true;
        String defaultTitle = "Role Scanner Information";

        // Check Each Players who was previously bot abused and make sure they have the role if they're supposed to have it
        baCore.getRecords().forEach(record -> {
            guild.retrieveMemberById(record.getDiscordID()).useCache(false).submit().whenComplete(new BiConsumer<Member, Throwable>() {
                @Override
                public void accept(Member m, Throwable throwable) {
                    if (throwable == null) {
                        if (baCore.botAbuseIsCurrent(record.getDiscordID()) && !m.getRoles().contains(baFeature.getConfig().getBotAbuseRole())) {
                            guild.addRoleToMember(m, baFeature.getConfig().getBotAbuseRole()).reason("Integrity Check Re-Added the Role").queue();
                            embed.setAsWarning(defaultTitle, "**My Role Scanning Utility has Added Bot Abuse Role to "
                                    + m.getAsMention() +
                                    " because they didn't have the role... and they're supposed to have it.**");
                            embed.sendToLogChannel();
                            log.info("Added Bot Abuse to " + m.getEffectiveName()
                                    + " because they didn't have the role... and they're supposed to have it.");
                        }
                    }
                    else {
                        aue.logCaughtException(Thread.currentThread(), throwable);
                    }
                }
            });
        });
    }
    void stopTimer() {
        timerRunning = false;
    }
    boolean isTimerRunning() {
        return timerRunning;
    }
}
