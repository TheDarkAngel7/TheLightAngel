package Angel.Sanctions.Database;

import com.google.gson.annotations.Expose;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class SanctionDatabaseContainer {
    protected static final Logger log = LogManager.getLogger(SanctionDatabaseContainer.class);

    @Expose
    private List<SanctionInfo> sanctionList = new ArrayList<>();

    public void addSanction(SanctionInfo sanctionInfo) {
        sanctionList.add(sanctionInfo);

        log.info("Added {} for Discord ID {} {}", (sanctionInfo instanceof BanInfo ? "Ban" : "Suspension"), sanctionInfo.getDiscordID(),
                (sanctionInfo.getFullDurationString().equalsIgnoreCase("permanently") ?
                        sanctionInfo.getFullDurationString() : "for " + sanctionInfo.getFullDurationString()));
    }

    public void removeSanction(long targetDiscordID) {
        int index = 0;

        while (index < sanctionList.size()) {
            if (sanctionList.get(index).getDiscordID() == targetDiscordID) {
                SanctionInfo sanctionInfo = sanctionList.remove(index);
                log.info("Removed {} for Discord ID {}", (sanctionInfo instanceof BanInfo ? "Ban" : "Suspension"), sanctionInfo.getDiscordID());
                break;
            }
            index++;
        }
    }

    public List<SanctionInfo> getSanctionList() {
        return sanctionList;
    }
}
