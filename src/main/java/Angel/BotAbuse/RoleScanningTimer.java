package Angel.BotAbuse;

import Angel.AngelExceptionHandler;
import Angel.EmbedEngine;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;

class RoleScanningTimer implements Runnable {
    private final Logger log = LogManager.getLogger(RoleScanningTimer.class);
    private final AngelExceptionHandler aue = new AngelExceptionHandler();
    private final BotAbuseMain baFeature;
    private final BotAbuseCore baCore;
    private final EmbedEngine embed;
    private final Guild guild;
    private boolean timerRunning = false;

    RoleScanningTimer(Guild guild, BotAbuseMain baFeature, EmbedEngine embed) {
        this.baFeature = baFeature;
        this.baCore = baFeature.getCore();
        this.embed = embed;
        this.guild = guild;
    }

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
                    if (throwable != null) {
                        aue.logCaughtException(Thread.currentThread(), throwable);
                    }
                    else {
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
