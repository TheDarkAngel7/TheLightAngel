package Angel.CheckIn;

import java.util.List;

class CheckInPardonList {
    private final List<CheckInResult> results;
    private final List<Boolean> isPardoned;

    CheckInPardonList(List<CheckInResult> results, List<Boolean> isPardoned) {
        this.results = results;
        this.isPardoned = isPardoned;
    }

    List<CheckInResult> getResultsList() {
        return results;
    }

    List<Boolean> getIsPardoned() {
        return isPardoned;
    }
}
