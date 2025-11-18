package Angel.PlayerList;

import Angel.CommonLogic;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Players implements CommonLogic {

    private final AtomicReference<Member> playerAccount = new AtomicReference<>(null);
    private final AtomicBoolean hasDuplicates = new AtomicBoolean(false);

    public Players(String searchName) {
        AtomicReference<List<Member>> verifiedMembers = new AtomicReference<>();
        Guild guild = getGuild();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        JaroWinklerDistance jaroWinkler = new JaroWinklerDistance();

        guild.retrieveMembersByPrefix(searchName, 10).onSuccess(ms -> {
            if (ms.size() == 1) {
                playerAccount.set(ms.get(0));
            }
            else if (ms.size() > 1) {
                int index = 0;
                double leadScore = 0;
                Member accountCandidate = null;
                while (index < ms.size()) {
                    double score = jaroWinkler.apply(ms.get(index).getEffectiveName(), searchName);

                    if (score > leadScore && score > 0.9) {
                        accountCandidate = ms.get(index);
                    }
                }
                playerAccount.set(accountCandidate);
            }
            countDownLatch.countDown();
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (playerAccount.get() == null) {
            CountDownLatch finalCountDownLatch = new CountDownLatch(1);
            guild.findMembersWithRoles(mainConfig.getMemberRole(), mainConfig.getTeamRole()).onSuccess(vM -> {
                verifiedMembers.set(vM);
                finalCountDownLatch.countDown();
            });

            try {
                finalCountDownLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            int index = 0;
            Member accountCandidate = null;
            double leadScore = 0;



            do {
                double score = jaroWinkler.apply(verifiedMembers.get().get(index).getEffectiveName(), searchName);

                if (score > leadScore && score > 0.5) {
                    accountCandidate = verifiedMembers.get().get(index);
                    leadScore = score;
                }
                index++;

            }
            while (index < verifiedMembers.get().size());

            playerAccount.set(accountCandidate);
        }

    }

    public String getPlayerName() {
        return playerAccount.get().getEffectiveName();
    }

    public Member getDiscordAccount() {
        return playerAccount.get();
    }

    public boolean isCrewMember() {
        return isCrewMember(playerAccount.get());
    }

    public boolean isSupporter() {
        return isSupporter(playerAccount.get());
    }

    public boolean isStaff() {
        return isTeamMember(playerAccount.get());
    }

    public boolean hasDuplicates() {
        return hasDuplicates.get();
    }
}
