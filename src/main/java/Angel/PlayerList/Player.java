package Angel.PlayerList;

import Angel.CommonLogic;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Player implements CommonLogic {
    private final Logger log = LogManager.getLogger(Player.class);
    private final AtomicReference<Member> playerAccount = new AtomicReference<>(null);
    private final AtomicBoolean hasDuplicates = new AtomicBoolean(false);

    public Player(String searchName) {
        AtomicReference<List<Member>> verifiedMembers = new AtomicReference<>();
        Guild guild = getGuild();

        CountDownLatch finalCountDownLatch = new CountDownLatch(1);

        guild.loadMembers().onSuccess(members -> {
           List<Member> vM = new ArrayList<>();

           members.forEach(m -> {
               if (m.getRoles().contains(mainConfig.getMemberRole()) || m.getRoles().contains(mainConfig.getTeamRole())) {
                   vM.add(m);
               }
           });
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
        int lowestScore = 50;
        LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
        do {
            int score = levenshteinDistance.apply(searchName.toLowerCase(), verifiedMembers.get().get(index).getEffectiveName().toLowerCase());

            if (score < lowestScore) {
                log.info("Based on " + searchName + " we're thinking " + verifiedMembers.get().get(index).getEffectiveName() +
                        " is the best match with a score of " + score);
                accountCandidate = verifiedMembers.get().get(index);
                lowestScore = score;
            }
            index++;

        }
        while (index < verifiedMembers.get().size());

        playerAccount.set(accountCandidate);

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
