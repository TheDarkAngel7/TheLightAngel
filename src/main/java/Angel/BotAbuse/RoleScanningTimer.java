package Angel.BotAbuse;

import Angel.EmbedHandler;
import net.dv8tion.jda.api.entities.Guild;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Timer;
import java.util.TimerTask;

class RoleScanningTimer extends Timer {
    private final Logger log = LogManager.getLogger(RoleScanningTimer.class);
    private BotAbuseMain baFeature;
    private BotAbuseConfiguration botConfig;
    private EmbedHandler embed;
    private Guild guild;
    private boolean timerRunning = false;

    RoleScanningTimer(Guild guild, BotAbuseMain baFeature, BotAbuseConfiguration botConfig, EmbedHandler embed) {
        this.baFeature = baFeature;
        this.botConfig = botConfig;
        this.embed = embed;
        this.guild = guild;
    }

    void startTimer() {
        log.info("Role Scanning Timer startTimer() executed");
        timerRunning = true;
        this.schedule(new TimerTask() {
            @Override
            public void run() {
                String defaultTitle = "Role Scanner Information";
                guild.loadMembers(m -> {
                    if (m.getRoles().contains(baFeature.botConfig.botAbuseRole)
                            && !baFeature.getCore().botAbuseIsCurrent(m.getIdLong())) {
                        guild.removeRoleFromMember(m, baFeature.botConfig.botAbuseRole).queue();
                        embed.setAsInfo(defaultTitle, "[Role Scanner] Removed Bot Abuse Role from "
                                + m.getAsMention() + " because they had the role... " +
                                "and they weren't supposed to have it.");
                        embed.sendToLogChannel();
                        log.info("Removed Bot Abuse Role from " + m.getEffectiveName() +
                                " because they had the role... and they were not supposed to have it");
                    }
                });
                baFeature.getCore().getRecords().forEach(r -> {
                    guild.getJDA().retrieveUserById(r.getDiscordID()).queue(user -> {
                        if (guild.isMember(user)) {
                            guild.retrieveMemberById(r.getDiscordID(), true).queue(member -> {
                                if (!member.getRoles().contains(baFeature.botConfig.botAbuseRole) &&
                                        r.isCurrentlyBotAbused()) {
                                    guild.addRoleToMember(member,
                                            baFeature.botConfig.botAbuseRole).queue();
                                    embed.setAsInfo(defaultTitle, "[Role Scanner] Added Bot Abuse Role to "
                                            + member.getAsMention() +
                                            " because they didn't have the role... and they're supposed to have it.");
                                    embed.sendToLogChannel();
                                    log.info("Added Bot Abuse to " + member.getEffectiveName()
                                            + " because they didn't have the role... and they're supposed to have it.");
                                }
                            });
                        }
                    });
                });
            }
            // Configurable Periodic Scan of Players that should be Bot Abused to ensure that they have the role
            // Followed by a Scan of All Players to look for any Bot Abuse roles that did not get removed when they should have
        },0, botConfig.roleScannerInterval * 60000);
    }
    void stopTimer() {
        timerRunning = false;
        this.cancel();
        this.purge();
    }

    boolean isTimerRunning() {
        return timerRunning;
    }
}
