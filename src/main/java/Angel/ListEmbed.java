package Angel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListEmbed {
    private final MessageEntry entry;
    private final CustomListEmbed customListEmbed;
    private final List<String> alternateStrings;
    private String prefixString;
    private String firstPageIcon = "\u23EE\uFE0F *Go To First Record*";
    private String previousPageIcon = "\u2B05\uFE0F *Go To Previous Record*";
    private String stopIcon = "\u23F9\uFE0F *Click when you are done*";
    private String nextPageIcon = "\u27A1\uFE0F *Go To Next Record*";
    private String lastPageIcon = "\u23ED\uFE0F *Go To Last Record*";
    private String suffixString = null;
    private String buttonLabels = "\n\n **Use The Arrows to Navigate:**" +
            "\n" + firstPageIcon + "\t" + lastPageIcon +
            "\n" + previousPageIcon + "\t" + nextPageIcon +
            "\n" + stopIcon;
    private int currentIndex = 0;

    public ListEmbed(MessageEntry entry, String prefixString, List<String> alternateStrings, @Nullable String suffixString) {
        this.prefixString = prefixString;
        this.alternateStrings = new ArrayList<>();
        this.alternateStrings.addAll(alternateStrings);
        this.suffixString = suffixString;
        this.entry = entry.setMessage(getFirstPage());
        this.customListEmbed = null;
    }

    ListEmbed(MessageEntry entry, String prefixString, List<String> alternateStrings, @Nullable String suffixString, CustomListEmbed cListEmbed) {
        this.prefixString = prefixString;
        this.alternateStrings = new ArrayList<>();
        this.alternateStrings.addAll(alternateStrings);
        this.suffixString = suffixString;
        this.customListEmbed = cListEmbed;
        this.entry = entry.setMessage(getFirstPage());
    }

    MessageEntry getMessageEntry() {
        return entry;
    }

    CustomListEmbed getCustomListEmbed() {
        return customListEmbed;
    }

    boolean isCustomEmbed() {
        return customListEmbed != null;
    }

    public ListEmbed invertButtonLabels() {
        return invertButtonLabels(false);
    }

    public ListEmbed invertButtonLabels(boolean pluralLabels) {
        firstPageIcon = "\u23EE\uFE0F *Go To Newest Record*";
        previousPageIcon = "\u2B05\uFE0F *Go To a Newer Record*";
        nextPageIcon = "\u27A1\uFE0F *Go To Older Record*";
        lastPageIcon = "\u23ED\uFE0F *Go To Oldest Record*";

        if (pluralLabels) {
            firstPageIcon = firstPageIcon.replace("d*", "ds*");
            previousPageIcon = previousPageIcon.replace("d*", "ds*");
            nextPageIcon = nextPageIcon.replace("d*", "ds*");
            lastPageIcon = lastPageIcon.replace("d*", "ds*");
        }
        return this;
    }

    String getFirstPage() {
        currentIndex = 0;
        try {
            return getConstructedString(alternateStrings.get(0));
        }
        catch (IndexOutOfBoundsException ex) {
            entry.setDesign(EmbedDesign.SUCCESS).setTitle("No Records Remaining");
            return ":white_check_mark: **No Records Remaining, You're Done!**";
        }
    }
    String getNextPage() {
        if (++currentIndex >= alternateStrings.size() - 1) {
            return getLastPage();
        }
        else return getConstructedString(alternateStrings.get(currentIndex));
    }
    String getPreviousPage() {
        if (--currentIndex < 0) {
            return getFirstPage();
        }
        else return getConstructedString(alternateStrings.get(currentIndex));
    }
    String getLastPage() {
        currentIndex = alternateStrings.size() - 1;
        return getConstructedString(alternateStrings.get(currentIndex));
    }

    String deletePage(int pageNum) {
        return alternateStrings.remove(pageNum - 1);
    }

    int getCurrentPage() {
        return currentIndex + 1;
    }

    int getTotalPages() {
        return alternateStrings.size();
    }

    private String getConstructedString(String alternateString) {
        String constructedString = prefixString + "\n\n" + alternateString;
        if (suffixString != null) {
            constructedString = constructedString.concat("\n\n" + suffixString);
        }

        if (isCustomEmbed()) {
            constructedString = constructedString.concat("\n\n" + customListEmbed.getEmoteLabeling());
        }

        return constructedString.concat(buttonLabels);
    }
}