package Angel.Sanctions.Database;

import Angel.Sanctions.Exceptions.InvalidExpirationDateException;
import com.google.gson.annotations.Expose;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.RoleColors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SuspensionInfo extends SanctionInfo {
    protected static final Logger log = LogManager.getLogger(SuspensionInfo.class);

    @Expose
    private final List<Long> revokedRoleIDs;

    public SuspensionInfo(long targetDiscordID, String duration, String reason, List<Role> revokedRoles) throws InvalidExpirationDateException {
        super(targetDiscordID, duration, reason);
        this.revokedRoleIDs = revokedRoles.stream()
                .map(Role::getIdLong)
                .toList();
    }

    public List<Role> getRevokedRoles() {
       return revokedRoleIDs.stream()
               .map(id -> getGuild().getRoleById(id))
               .filter(Objects::nonNull)
               .toList();
    }

    public Role getHoldingRole() {
        if (getExpiryDate() == null) {
            return getGuild().getRoleById(sanctionConfig.getBlockedRoleID());
        }
        else {
            return getGuild().getRoleById(sanctionConfig.getSuspensionRoleID());
        }
    }

    @Override
    public void applySanction(String duration) {
        Member m = getGuild().getMemberById(getDiscordID());

        if (m == null) {
            log.warn("Unable to Apply Suspension Role for {} due to Member not being in Discord Server", getDiscordID());
            return;
        }

        try {
            getGuild().modifyMemberRoles(m, Collections.singletonList(getHoldingRole()), getRevokedRoles())
                    .queue(success -> log.info("Successfully Suspended {} for {}", m.getEffectiveName(), duration),
                            error -> log.error("Rest Action Error: Unable to Suspend {} for {}", m.getEffectiveName(), duration, error)
                    );

            getTeamBureauChannel().sendMessage("**" + m.getEffectiveName() + "** - Suspended for " + duration + " ").queue();
        }
        catch (Exception e) {
            log.error("Unable to Suspend {} for {}", getDiscordID(), duration, e);
        }
    }

    @Override
    public void reapplySanction() {
        Member m = getGuild().getMemberById(getDiscordID());

        if (m == null) {
            log.warn("Unable to Reapply Suspension Role for {} due to Member not being in Discord Server", getDiscordID());
            return;
        }

        try {
            getGuild().modifyMemberRoles(m, Collections.singletonList(getHoldingRole()), getRevokedRoles())
                    .queue(success -> log.info("Successfully Reapplied Suspension for {} ", m.getEffectiveName()),
                            error -> log.error("Rest Action Error: Unable to Reapply Suspension for {}", m.getEffectiveName(), error)
                    );
        }
        catch (Exception e) {
            log.error("Unable to Reapply Suspension for {}", getDiscordID(), e);
        }
    }

    @Override
    public void reverseSanction() {
        Member m = getGuild().getMemberById(getDiscordID());

        if (m == null) {
            log.warn("Unable to Remove Suspension Role for {} due to Member not being in Discord Server", getDiscordID());
            return;
        }

        try {
            getGuild().removeRoleFromMember(m, getHoldingRole()).submit().whenComplete((result, throwable)-> {

                if (throwable == null) {
                    boolean hasOtherRedRoles = m.getRoles().stream()
                            .filter(role -> role.getIdLong() != getHoldingRole().getIdLong())
                            .map(Role::getColors)
                            .map(RoleColors::getPrimary)
                            .filter(Objects::nonNull)
                            .anyMatch(color -> color.getRed() > 200);

                    if (hasOtherRedRoles) {
                        log.warn("Removed Suspension Role but did not reapply revoked roles to {} due to other red roles being present", m.getEffectiveName());
                    }
                    else {
                        getGuild().modifyMemberRoles(m, getRevokedRoles(), Collections.emptyList())
                                .queue(success -> log.info("Successfully Reapplied Revoked Roles to {}", m.getEffectiveName()),
                                        error -> log.error("Unable to Reapply Revoked Roles to {}", m.getEffectiveName(), error));
                    }
                }
                else {
                    log.error("Unable to Remove Suspension Role for {}", m.getEffectiveName(), throwable);
                }
            });
        }
        catch (Exception e) {
            log.error("Unable to Remove the Suspension for {}", getDiscordID(), e);
        }
    }

    @Override
    public boolean sanctionApplied() {
        Member m = getGuild().getMemberById(getDiscordID());

        if (m == null) return false;

        else return m.getRoles().contains(getHoldingRole()) && m.getRoles().stream()
                .noneMatch(getRevokedRoles()::contains);
    }
}
