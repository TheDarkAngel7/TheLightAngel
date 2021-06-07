package Angel.BotAbuse;

import Angel.EmbedHandler;
import net.dv8tion.jda.api.entities.Guild;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class RoleScanningTimer implements Runnable {
    private final Logger log = LogManager.getLogger(RoleScanningTimer.class);
    private BotAbuseMain baFeature;
    private EmbedHandler embed;
    private Guild guild;
    private boolean timerRunning = false;

    RoleScanningTimer(Guild guild, BotAbuseMain baFeature, EmbedHandler embed) {
        this.baFeature = baFeature;
        this.embed = embed;
        this.guild = guild;
    }

    @Override
    public void run() {
        timerRunning = true;
        String defaultTitle = "Role Scanner Information";
        guild.loadMembers(m -> {
            if (m.getRoles().contains(baFeature.getConfig().getBotAbuseRole())
                    && !baFeature.getCore().botAbuseIsCurrent(m.getIdLong())) {
                guild.removeRoleFromMember(m, baFeature.getConfig().getBotAbuseRole()).reason("Integrity Check Removed the Role").queue();
                embed.setAsInfo(defaultTitle, "[Role Scanner] Removed Bot Abuse Role from "
                        + m.getAsMention() + " because they had the role... " +
                        "and they weren't supposed to have it.");
                embed.sendToLogChannel();
                log.info("Removed Bot Abuse Role from " + m.getEffectiveName() +
                        " because they had the role... and they were not supposed to have it");
            }
            if (baFeature.getCore().botAbuseIsCurrent(m.getIdLong()) &&
                    !m.getRoles().contains(baFeature.getConfig().getBotAbuseRole())) {
                guild.addRoleToMember(m, baFeature.getConfig().getBotAbuseRole()).reason("Integrity Check Re-Added the Role").queue();
                embed.setAsInfo(defaultTitle, "[Role Scanner] Added Bot Abuse Role to "
                        + m.getAsMention() +
                        " because they didn't have the role... and they're supposed to have it.");
                embed.sendToLogChannel();
                log.info("Added Bot Abuse to " + m.getEffectiveName()
                        + " because they didn't have the role... and they're supposed to have it.");
            }
        });
    }
    void stopTimer() {
        timerRunning = false;
    }
    boolean isTimerRunning() {
        return timerRunning;
    }
}
