package Angel.PlayerList;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class Player implements PlayerListLogic {
    private final Logger log = LogManager.getLogger(Player.class);
    private final Member playerAccount;

    public Player(String searchName) {
        Guild guild = getGuild();

        List<Member> verifiedMembers = guild.getMembers().stream().
                filter(m -> m.getRoles().contains(mainConfig.getMemberRole()) ||
                        m.getRoles().contains(mainConfig.getTeamRole())).toList();

        int index = 0;
        Member accountCandidate = null;
        int lowestScore = 50;
        LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
        do {
            int score = levenshteinDistance.apply(searchName.toLowerCase(), verifiedMembers.get(index).getEffectiveName().toLowerCase());

            if (score < lowestScore) {
                log.debug("Based on {} we're thinking {} is the best match with a score of {}",
                        searchName, verifiedMembers.get(index).getEffectiveName(), score);
                accountCandidate = verifiedMembers.get(index);
                lowestScore = score;
            }
            if (lowestScore == 0) break;
            index++;

        }
        while (index < verifiedMembers.size());

        log.info("Based on the Query of {}, Discord Account: {} (ID: {})", searchName, accountCandidate.getEffectiveName(), accountCandidate.getIdLong());

        if (lowestScore >= 3) {
            log.info("Based on the high Levenshtein score, we're going to attempt to strip the query of any underscores and dashes - Possibly getting a better match");

            index = 0;
            do {
                int score = levenshteinDistance.apply(searchName.replaceAll("_", "")
                                .replaceAll("-", "").toLowerCase(),
                        verifiedMembers.get(index).getEffectiveName().replaceAll("_", "")
                                .replaceAll("-", "").toLowerCase());

                if (score < lowestScore) {
                    log.debug("With underscores and dashes removed from search query {}, we're thinking {} is the best match with a score of {}",
                            searchName, verifiedMembers.get(index).getEffectiveName(), score);
                    accountCandidate = verifiedMembers.get(index);
                    lowestScore = score;
                }
                if (lowestScore == 0) break;
                index++;

            } while (index < verifiedMembers.size());
            log.info("Based on Query of {} with underscores and dashes removed, Discord Account: {} (ID: {})",
                    searchName, accountCandidate.getEffectiveName(), accountCandidate.getIdLong());
        }

        this.playerAccount = accountCandidate;

    }

    public String getPlayerName() {
        return playerAccount.getEffectiveName();
    }

    public Member getDiscordAccount() {
        return playerAccount;
    }

    public boolean isCrewMember() {
        return isCrewMember(playerAccount);
    }

    public boolean isSupporter() {
        return isSupporter(playerAccount);
    }

    public boolean isStaff() {
        return isTeamMember(playerAccount);
    }
}
