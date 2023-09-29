package Angel;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public interface MainConfig {
    MainConfiguration mainConfig = new ModifyMainConfiguration(new FileHandler().getMainConfig());
    AngelExceptionHandler aue = new AngelExceptionHandler();
    EmbedEngine embed = new EmbedEngine();

    default Guild getGuild() {
        return mainConfig.guild;
    }

    default boolean isTeamMember(Member m) {
        return isStaffMember(m) ||
                m.getRoles().contains(mainConfig.teamRole);
    }

    default boolean isStaffMember(Member m) {
        return m.getRoles().contains(mainConfig.staffRole) ||
                m.getRoles().contains(mainConfig.adminRole) ||
                m.getIdLong() == mainConfig.owner.getIdLong();
    }

    // Permission Checkers that this class and the other features use:
    default boolean isTeamMember(long targetDiscordID) {
        AtomicReference<Member> member = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            getGuild().retrieveMemberById(targetDiscordID).useCache(false).queue(m -> {
                member.set(m);
                latch.countDown();
            });
            latch.await();
            return isTeamMember(member.get());
        }
        catch (ErrorResponseException | NullPointerException | InterruptedException ex) {
            aue.logCaughtException(Thread.currentThread(), ex);
            return false;
        }
    }
    default boolean isStaffMember(long targetDiscordID) {
        AtomicReference<Member> member = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            getGuild().retrieveMemberById(targetDiscordID).useCache(false).queue(m -> {
                member.set(m);
                latch.countDown();
            });
            latch.await();
            return isStaffMember(member.get());
        }
        catch (ErrorResponseException | NullPointerException | InterruptedException ex) {
            aue.logCaughtException(Thread.currentThread(), ex);
            return false;
        }
    }
}
