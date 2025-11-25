package Angel.BotAbuse;

import java.util.List;
import java.util.Map;

class BotAbuseFile {
    private final List<BotAbuseRecord> records;
    private final Map<String, String> reasonsDictionary;

     BotAbuseFile(List<BotAbuseRecord> records, Map<String, String> reasonsDictionary) {
        this.records = records;
        this.reasonsDictionary = reasonsDictionary;
    }

    List<BotAbuseRecord> getRecords() {
        return records;
    }

    Map<String, String> getReasonsDictionary() {
        return reasonsDictionary;
    }
}
