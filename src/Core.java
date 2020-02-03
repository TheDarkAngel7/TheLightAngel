import java.io.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

class Core { // This is where all the magic happens, where all the data is added and queried from the appropriate arrays to
    // Display all the requested data.
    ArrayList<Long> discordID = new ArrayList<>();
    ArrayList<Integer> repOffenses = new ArrayList<>();
    ArrayList<Date> issuedDates = new ArrayList<>();
    ArrayList<Date> expiryDates = new ArrayList<>();
    ArrayList<String> reasons = new ArrayList<>();
    ArrayList<String> proofImages = new ArrayList<>();
    ArrayList<Long> currentBotAbusers = new ArrayList<>();
    private int indexOfLastOffense;

    void startup() throws Exception {

        try {
            FileHandler fileHandler = new FileHandler();
            this.discordID = fileHandler.getDiscordIDs();
            this.repOffenses = fileHandler.getRepOffenses();
            this.issuedDates = fileHandler.getIssuedDates();
            this.expiryDates = fileHandler.getExpiryDates();
            this.reasons = fileHandler.getReasons();
            this.proofImages = fileHandler.getProofImages();
            this.currentBotAbusers = fileHandler.getCurrentBotAbusers();
        }
        catch (FileNotFoundException ex) {
            // Create a New File the immediately close it if the data file doesn't exist
            ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream("data/data.dat"));
            outputStream.close();
        }
    }

    String setBotAbuse(long targetDiscordID, boolean adminOverride, String reason, String imageURL) throws Exception {
        FileHandler fileHandler = new FileHandler();
        Calendar c = Calendar.getInstance();

        if (!adminOverride && (reason.equals("k") || reason.equals("kick"))) {
            this.reasons.add("Bumping a Kickvote");
        }
        else if (!adminOverride && (reason.equals("o") || reason.equals("offline"))) {
            this.reasons.add("Bumping a Offline Message");
        }
        else if (reason.equals("s") || reason.equals("staff")) {
            this.reasons.add("Contact Staff");
        }
        else {
            return ":x: [System] Invalid Reason!";
        }
        // We're checking to see if this player is currently Bot Abused - This Code would run if it was a moderator just using /botabuse like normal.
        if (!botAbuseIsCurrent(targetDiscordID) && !adminOverride) {
            // If they've been previously Bot Abused before then we need the index value of it
            if (indexOfLastOffense == -1) { // This is their first offense
                this.discordID.add(targetDiscordID);
                this.repOffenses.add(1);
                this.issuedDates.add(c.getTime());
                this.expiryDates.add(setExpiryDate(targetDiscordID));
                this.proofImages.add(imageURL);
                this.currentBotAbusers.add(targetDiscordID);
                fileHandler.writeArrayData(this.discordID, this.repOffenses, this.issuedDates, this.expiryDates, this.reasons, this.proofImages, this.currentBotAbusers);
            }
            else {
                // The Bot Abuse Time gets progressively longer - This isn't their first offense
                this.discordID.add(targetDiscordID);
                this.repOffenses.add(this.repOffenses.get(indexOfLastOffense) + 1);
                this.issuedDates.add(c.getTime());
                this.expiryDates.add(setExpiryDate(targetDiscordID));
                this.proofImages.add(imageURL);
                this.currentBotAbusers.add(targetDiscordID);
                fileHandler.writeArrayData(this.discordID, this.repOffenses, this.issuedDates, this.expiryDates, this.reasons, this.proofImages, this.currentBotAbusers);
            }

            System.out.println(this.discordID.toString() + "\n" + this.repOffenses.toString() +
                    "\n" + this.expiryDates.toString() + "\n" + this.reasons.toString() + "\n" + this.currentBotAbusers.toString());
            System.out.println("[System] Successfully Bot Abused " + targetDiscordID + " for " + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)));
            return ":white_check_mark: **[System] Successfully Bot Abused " + targetDiscordID +
                    "**\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                    "**\nDate Issued: **" + this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID)) +
                    "**\nExpiry Date: **" + getNewExpiryDate(targetDiscordID) +
                    "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                    "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) + "**";
        } // If a /permbotabuse was run and the Bot Abuse is still current.
        else if (botAbuseIsCurrent(targetDiscordID) && adminOverride) {
            // First we check to see if the current Bot Abuse is not permanent
            if (this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)) != null) {
                // The expiry date is changed to null for Permanent and the reason is updated to "Contact Staff"
                this.expiryDates.set(this.discordID.lastIndexOf(targetDiscordID), null);
                this.reasons.set(this.discordID.lastIndexOf(targetDiscordID), "Contact Staff");
                fileHandler.writeArrayData(this.discordID, this.repOffenses, this.issuedDates, this.expiryDates, this.reasons, this.proofImages, this.currentBotAbusers);
                return ":white_check_mark: **[System - Admin Override] Successfully Overrode Bot Abuse for " + targetDiscordID + " and it is now "
                        + this.getNewExpiryDate(targetDiscordID) + "**";
            }
            // Here we're saying player is already Permanently Bot Abused
            else {
                return ":x: **[System - Admin Override] This Player is Already Permanently Bot Abused**";
            }
        }
        else if (!botAbuseIsCurrent(targetDiscordID) && adminOverride) {
            if (this.indexOfLastOffense != -1) {
                this.discordID.add(targetDiscordID);
                // This Statement would throw an Index Out of Bounds Exception if indexOfLastOffense is -1, the If
                // statement is a work around. V V V
                this.repOffenses.add(this.repOffenses.get(indexOfLastOffense) + 1);
                this.issuedDates.add(c.getTime());
                this.expiryDates.add(null);
                this.proofImages.add(imageURL);
                this.currentBotAbusers.add(targetDiscordID);
            }
            else {
                // If we Try to Perm Bot Abuse someone that's never had a Bot Abuse offense before.
                this.discordID.add(targetDiscordID);
                this.repOffenses.add(1);
                this.issuedDates.add(c.getTime());
                this.expiryDates.add(null);
                this.proofImages.add(imageURL);
                this.currentBotAbusers.add(targetDiscordID);
            }
            fileHandler.writeArrayData(this.discordID, this.repOffenses, this.issuedDates, this.expiryDates, this.reasons, this.proofImages, this.currentBotAbusers);
            System.out.println(this.discordID.toString() + "\n" + this.repOffenses.toString() +
                    "\n" + this.expiryDates.toString() + "\n" + this.reasons.toString() + "\n" + this.currentBotAbusers.toString());

            // Output to return from a perm Bot Abuse, we check to see if proofImages in the corresponding index is null, if so Violation image will say "None Provided"
            // If an image was provided then the else statement would run
            if (this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) == null) {
                return ":white_check_mark: **[System] Successfully Bot Abused " + targetDiscordID +
                        "**\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nDate Issued: **" + this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nExpiry Date: **" + getNewExpiryDate(targetDiscordID) +
                        "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nViolation Image: **None Provided**";
            }
            else {
                return ":white_check_mark: **[System] Successfully Bot Abused " + targetDiscordID +
                        "**\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nDate Issued: **" + this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nExpiry Date: **" + getNewExpiryDate(targetDiscordID) +
                        "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) + "**";
            }
        }
        else {
            boolean permBotAbused;
            // Checking to see if a moderator tried to bot abuse someone that is Permanently Bot Abused.
            // The Expiry Date will be null if that's the case.
            permBotAbused = this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)) == null;
            if (!permBotAbused) {
                return ":x: **[System] This Player is Already Bot Abused!**\nDiscord ID: **" + targetDiscordID + "**\nOffense Number: **"
                        + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) + "**\nExpiry Date: **"
                        + this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)) + "**";
            }
            else {
                return ":x: **[System] This Player is Permanently Bot Abused!!**\nDiscord ID: **" + targetDiscordID + "**\nOffense Number: **"
                        + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) + "**\nExpiry Date: **Never**";
            }
        }
    }

    private Date setExpiryDate(long targetDiscordID) {
        Calendar c = Calendar.getInstance();
        Calendar cOld = Calendar.getInstance();
        // Take off 6 months
        cOld.add(Calendar.MONTH, -6);

        int index = 0;
        int prevOffenses = 0;

        while (index < this.discordID.size() - 1 && this.expiryDates.size() != 0) {
            // We check for discordID.size() - 1 & expiryDates.size() != 0 because the
            // discordID array has the ID already added to it and the expiryDates array hasn't been touched yet
            // so the size of discordID size would be 1 more than the size of the expiryDates array
            if (this.discordID.get(index) == targetDiscordID && this.expiryDates.get(index).after(cOld.getTime())) {
                // Here we're checking to see if the discordID at the current index matches the targetDiscordID array
                // We  also check the expiryDate at that index and see if it is after the Date where the records would
                // otherwise be ignored by the bot, records whose expiryDates are before the cOld time would be ignored.
                prevOffenses++;
            }
            index++;
        }
        if (prevOffenses < 4) {
            // The Times are Short for Testing Purposes, they would usually be in days or months.
            if (prevOffenses == 0) { // 0 Prior Offenses - 1st Offense
                c.add(Calendar.DAY_OF_MONTH, 7);
            }
            else if (prevOffenses == 1) { // 1 Prior Offense - 2nd Offense
                c.add(Calendar.DAY_OF_MONTH, 14);
            }
            else if (prevOffenses == 2) { // 2 Prior Offenses - 3rd Offense
                c.add(Calendar.DAY_OF_MONTH, 30);
            }
            else if (prevOffenses == 3) { // 3 Prior Offenses - 4th Offense
                c.add(Calendar.DAY_OF_MONTH, 60);
            }
            return c.getTime(); // Set the Expiry Date
        }
        else {
            // Add Null if this is their 5th offense - Permanent Bot Abuse
            return null;
        }
    }
    String getInfo(long targetDiscordID) { // This method is for queries
        int index = 0;
        int prevOffenses = 0;
        while (index < this.discordID.size()) {
            if (this.discordID.get(index) == targetDiscordID) {
                prevOffenses++;
            }
            index++;
        }
        if (botAbuseIsCurrent(targetDiscordID)) {
            // Checking to see if the queried player is perm bot abused
            if (this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)) == null) {
                return ":information_source: " + targetDiscordID + " Bot Abuse Info: " +
                        "\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nDate Issued: **" + this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nExpiry Date: **Never" +
                        "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\n\n:information_source: You have had " + (prevOffenses - 1) + " Previous Offenses" ;
            }
            else { // They Are Currently Bot Abused but not permanently
                return ":information_source: " + targetDiscordID + " Bot Abuse Info: " +
                        "\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nDate Issued: **" + this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nExpiry Date: **" + this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) + "**";
            }
        }
        // They're Not Bot Abused
        else {
            return ":white_check_mark: Lucky for you... you're not Bot Abused Currently" +
                    "\n\n:information_source: You have had " + prevOffenses + " Previous Offenses";
        }
    }
    private String getNewExpiryDate(long targetDiscordID) { // This Method gets called only when a new Bot Abuse is applied
        if (this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)) == null) {
            return "Permanent";
        }
        // They're Not Perm Bot Abused
        else {
            return this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)).toString();
        }
    }
    boolean botAbuseIsCurrent(long targetDiscordID) { // Returns True if the targetDiscordID is Bot Abused
        Calendar c = Calendar.getInstance();
        try {
            // The ExpiryDates array will have a null value for the targetDiscordID if it's a Permanent Bot Abuse - Return true
            if (this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)) == null) {
                return true;
            }
            else {
                // Otherwise return true or false if the date in the expiryDates array is after current time,
                // return true if the Bot Abuse is still current
                // return false if the Bot Abuse is not current
                this.indexOfLastOffense = discordID.lastIndexOf(targetDiscordID);
                return this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)).after(c.getTime());
            }
        }
        catch (IndexOutOfBoundsException ex) { // A -1 in the first if statement will cause this, a -1 indicates the target
            // discord ID wasn't found in the discordID array, which means it's their first offense and they've never been
            // Bot Abused before, so return false and set the indexOfLastOffense variable to -1
            this.indexOfLastOffense = -1;
            return false;
        }
    }
    long checkExpiredBotAbuse() throws Exception { // This is the method that gets run each second by the timer in DiscordBotMain
        Calendar c = Calendar.getInstance();
        int index = this.discordID.size() - 1;
        while (index >= 0) {
            long targetDiscordID = this.discordID.get(index);
            Date targetDate = this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID));
            // This Catches the program from trying to remove a permanent Bot Abuse
            if (targetDate == null) {
                // Take No Action
            }
            // If the targetDate is before the current time and the player is currently Bot Abused then remove their Bot Abuse
            else if (targetDate.before(c.getTime()) && this.currentBotAbusers.contains(targetDiscordID)) {
                FileHandler fileHandler = new FileHandler();
                this.currentBotAbusers.remove(targetDiscordID);
                fileHandler.writeArrayData(this.discordID, this.repOffenses, this.issuedDates,this.expiryDates, this.reasons, this.proofImages, this.currentBotAbusers);
                System.out.println(this.discordID.toString() + "\n" + this.repOffenses.toString() +
                        "\n" + this.expiryDates.toString() + "\n" + this.currentBotAbusers.toString());
                return targetDiscordID;

            }
            index--;
        }
        return 0;
    }
    String transferRecords(long oldDiscordID, long newDiscordID) throws Exception {
        FileHandler fileHandler = new FileHandler();
        int index = 0;
        int numTransferred = 0;
        // We scan over the entire discordID array and if the oldDiscordID matches we set the value to the newDiscordID
        while (index < this.discordID.size()) {
            if (oldDiscordID == this.discordID.get(index)) {
                this.discordID.set(index, newDiscordID);
                numTransferred++;
            }
            index++;
        }
        boolean wasBotAbused = false;
        // Now we check if the player is currently Bot abused, their discordID will appear in the currentBotAbusers array if so.
        if (this.currentBotAbusers.contains(oldDiscordID)) {
            this.currentBotAbusers.set(this.currentBotAbusers.indexOf(oldDiscordID), newDiscordID);
            wasBotAbused = true;
        }
        // If we had records transferred and they weren't Bot Abused
        if (numTransferred > 0 && !wasBotAbused) {
            fileHandler.writeArrayData(this.discordID, this.repOffenses, this.issuedDates, this.expiryDates, this.reasons, this.proofImages, this.currentBotAbusers);
            return ":white_check_mark: [System] Successfully Transferred the Records of " + oldDiscordID + " to " + newDiscordID + "\nThe Old Discord ID Was Not Bot Abused";
        }
        // If we had records transferred and they were Bot Abused
        else if (numTransferred > 0) {
            fileHandler.writeArrayData(this.discordID, this.repOffenses, this.issuedDates, this.expiryDates, this.reasons, this.proofImages, this.currentBotAbusers);
            return ":white_check_mark: [System] Successfully Transferred the Records of " + oldDiscordID + " to " + newDiscordID + "\nThe Old Discord ID Was Bot Abused at the Time and the Role was Transferred Over";
        }
        // If we had No Records Transferred
        else {
            System.out.println("[System] No Records Transferred");
            return ":warning: [System] No Records Transferred";
        }
    }
    int clearRecords (long targetDiscordID) throws Exception { // For Handling Clearing all records of a Discord ID - Returns the Number of Records Cleared
        FileHandler fileHandler = new FileHandler();
        int clearedRecords = 0;
        // If we want to clear the records of a Discord ID then we go through the discordID array and remove the elements in all the corresponding arrays.
        while (this.discordID.contains(targetDiscordID)) {
            this.repOffenses.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.issuedDates.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.expiryDates.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.reasons.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.proofImages.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.discordID.remove(this.discordID.lastIndexOf(targetDiscordID));
            clearedRecords++;
        }
        this.currentBotAbusers.remove(targetDiscordID);
        fileHandler.writeArrayData(this.discordID, this.repOffenses, this.issuedDates, this.expiryDates, this.reasons, this.proofImages, this.currentBotAbusers);
        return clearedRecords;
    }
    String seeHistory(long targetDiscordID, boolean isTeamMember) {
        int index = 0;
        int recordsCount = 0;
        String output = "**/checkhistory Results";
        if (isTeamMember) {
            output += "\n:information_source: " + targetDiscordID + "'s Bot Abuse History is as Follows:**";
        }
        else {
            output += "\n:information_source: Your Bot Abuse History is as Follows:**";
        }
        // We check the discordID array and then get all the elements in the corresponding index of the other arrays
        while (index < this.discordID.size()) {
            if (this.discordID.get(index) == targetDiscordID) {
                output += "\n\nOffense Number: " + this.repOffenses.get(index)
                        + "\nDate Issued: " + this.issuedDates.get(index)
                        + "\nDate Expired: " + this.expiryDates.get(index)
                        + "\nReason: " + this.reasons.get(index)
                        + "\nProof Image: " + this.proofImages.get(index);
                recordsCount++;
            }
            index++;
        }
        // If It was a non-Team member that used the /checkhistory command this would return and they don't have any history.
        if (recordsCount == 0 && !isTeamMember) {
            return ":white_check_mark: **You Have No Bot Abuse History**";
        }
        // If it was a Team member it would return as an error saying they don't have any history.
        else if (recordsCount == 0) {
            return ":x: **This Player Has No Bot Abuse History**";
        }
        // Otherwise return the constructed string from the while loop and above.
        else {
            output += "\n\nRecords Count: " + recordsCount;
            return output;
        }
    }
}