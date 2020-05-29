package Angel;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeoutException;

class Core { // This is where all the magic happens, where all the data is added and queried from the appropriate arrays to
    // Display all the requested data.
    FileHandler fileHandler;
    private RabbitMQSend rabbit;
    private BotAbuseVariables botAbuseVars = new BotAbuseVariables();
    private UndoVariables undoVars = new UndoVariables();
    private final Logger log = LogManager.getLogger(Core.class);
    private CoreConfiguration config = new CoreConfiguration() {};
    ArrayList<Long> discordID = new ArrayList<>();
    ArrayList<String> issuingTeamMember = new ArrayList<>();
    ArrayList<Integer> repOffenses = new ArrayList<>();
    ArrayList<Date> issuedDates = new ArrayList<>();
    ArrayList<Date> expiryDates = new ArrayList<>();
    ArrayList<String> reasons = new ArrayList<>();
    ArrayList<String> proofImages = new ArrayList<>();
    ArrayList<Long> currentBotAbusers = new ArrayList<>();
    private int indexOfLastOffense;
    private Calendar c;

    Core() {
        fileHandler = new FileHandler(this);
    }

    void startup(boolean reload) throws IOException, TimeoutException {
        if (!reload) {
            config.setup(fileHandler.getConfig());
            log.info("[System] Core Initiated...");
        }
        else {
            log.warn("[System] Program Restarting...");
            new ProcessBuilder().command("cmd.exe", "/c", "start", this.config.systemPath + "\\restart.bat").start();
            System.exit(1);
        }

        if (!config.testModeEnabled) {
            rabbit = new RabbitMQSend();
            rabbit.startup(config.host);
        }
        try {
            fileHandler.getDatabase();
        }
        catch (IllegalStateException ex) {
            log.warn("No Data Existed in the Arrays - Data File is Empty");
        }
    }

    String setBotAbuse(long targetDiscordID, boolean isPermanent, String reason, String imageURL, String teamMember)
            throws IOException {
        if (!botAbuseIsCurrent(targetDiscordID)) {
            if (!isPermanent && (reason.equals("k") || reason.equals("kick"))) {
                this.reasons.add("Bumping a Kickvote");
            }
            else if (!isPermanent && (reason.equals("o") || reason.equals("offline"))) {
                this.reasons.add("Bumping an Offline Message");
            }
            else if (reason.equals("s") || reason.equals("staff")) {
                this.reasons.add("Contact SAFE Team");
            }
            else {
                return ":x: [System] Invalid Reason!";
            }
        }
        // We're checking to see if this player is currently Bot Abused - This Code would run if it was a moderator just using /botabuse like normal.
        if (!botAbuseIsCurrent(targetDiscordID) && !isPermanent) {
            // If they've been previously Bot Abused before then we need the index value of it
            if (indexOfLastOffense == -1) { // This is their first offense
                this.discordID.add(targetDiscordID);
                this.issuingTeamMember.add(teamMember);
                this.repOffenses.add(1);
                this.issuedDates.add(Calendar.getInstance().getTime());
                this.expiryDates.add(setExpiryDate(targetDiscordID));
                this.proofImages.add(imageURL);
                this.currentBotAbusers.add(targetDiscordID);

            }
            else {
                // The Bot Abuse Time gets progressively longer - This isn't their first offense
                this.discordID.add(targetDiscordID);
                this.issuingTeamMember.add(teamMember);
                this.repOffenses.add(this.repOffenses.get(indexOfLastOffense) + 1);
                this.issuedDates.add(Calendar.getInstance().getTime());
                this.expiryDates.add(setExpiryDate(targetDiscordID));
                this.proofImages.add(imageURL);
                this.currentBotAbusers.add(targetDiscordID);
            }
            if (this.config.testModeEnabled) {
                System.out.println(this.discordID.toString() + "\n" + this.repOffenses.toString() +
                        "\n" + this.expiryDates.toString() + "\n" + this.reasons.toString() + "\n" + this.currentBotAbusers.toString());
            }
            if (arraySizesEqual()) {
                fileHandler.saveDatabase();
                this.sendMessage(0, targetDiscordID);
                return ":white_check_mark: **[System] Successfully Bot Abused " + targetDiscordID +
                        "**\nIssuing Team Member: **" + this.issuingTeamMember.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nDate Issued: **" + this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nExpiry Date: **" + getNewExpiryDate(targetDiscordID) +
                        "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) + "**";
            }
            else {
                log.fatal("FATAL ERROR: The Setting of the Bot Abuse did not run correctly and as a result I got inconsistent data");
                return "**[System] FATAL ERROR: The Setting of the Bot Abuse did not run correctly and as a result I got inconsistent data**";
            }
        } // If a /permbotabuse was run and the Bot Abuse is still current.
        else if (botAbuseIsCurrent(targetDiscordID) && isPermanent) {
            // First we check to see if the current Bot Abuse is not permanent
            if (this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)) != null) {
                this.issuingTeamMember.set(this.discordID.lastIndexOf(targetDiscordID), teamMember);
                // The expiry date is changed to null for Permanent and the reason is updated to "Contact Staff"
                this.expiryDates.set(this.discordID.lastIndexOf(targetDiscordID), null);
                this.reasons.set(this.discordID.lastIndexOf(targetDiscordID), "Contact Staff");
                if (this.config.testModeEnabled) {
                    System.out.println(this.discordID.toString() + "\n" + this.repOffenses.toString() +
                            "\n" + this.expiryDates.toString() + "\n" + this.reasons.toString() + "\n" + this.currentBotAbusers.toString());
                }
                fileHandler.saveDatabase();
                return ":white_check_mark: **[System - Admin Override] Successfully Overrode Bot Abuse for " + targetDiscordID + " and it is now "
                        + this.getNewExpiryDate(targetDiscordID) + "**";

            }
            // Here we're saying player is already Permanently Bot Abused
            else {
                return ":x: **[System - Admin Override] This Player is Already Permanently Bot Abused**";
            }
        }
        else if (!botAbuseIsCurrent(targetDiscordID) && isPermanent) {
            if (this.indexOfLastOffense != -1) {
                this.discordID.add(targetDiscordID);
                // This Statement would throw an Index Out of Bounds Exception if indexOfLastOffense is -1, the If
                // statement is a work around. V V V
                this.issuingTeamMember.add(teamMember);
                this.repOffenses.add(this.repOffenses.get(indexOfLastOffense) + 1);
                this.issuedDates.add(Calendar.getInstance().getTime());
                this.expiryDates.add(null);
                this.proofImages.add(imageURL);
                this.currentBotAbusers.add(targetDiscordID);
            }
            else {
                // If we Try to Perm Bot Abuse someone that's never had a Bot Abuse offense before.
                this.discordID.add(targetDiscordID);
                this.issuingTeamMember.add(teamMember);
                this.repOffenses.add(1);
                this.issuedDates.add(Calendar.getInstance().getTime());
                this.expiryDates.add(null);
                this.proofImages.add(imageURL);
                this.currentBotAbusers.add(targetDiscordID);
            }
            if (this.config.testModeEnabled) {
                System.out.println(this.discordID.toString() + "\n" + this.repOffenses.toString() +
                        "\n" + this.expiryDates.toString() + "\n" + this.reasons.toString() + "\n" + this.currentBotAbusers.toString());
            }
            if (arraySizesEqual()) {
                fileHandler.saveDatabase();
                this.sendMessage(1, targetDiscordID);
            }
            else {
                return "**[System] FATAL ERROR: The Setting of the Bot Abuse did not run correctly and as a result I got inconsistent data**";
            }
            // Output to return from a perm Bot Abuse, we check to see if proofImages in the corresponding index is null, if so Violation image will say "None Provided"
            // If an image was provided then the else statement would run
            if (this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) == null) {
                return ":white_check_mark: **[System] Successfully Bot Abused " + targetDiscordID +
                        "**\nIssuing Team Member: **" + this.issuingTeamMember.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nDate Issued: **" + this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nExpiry Date: **" + getNewExpiryDate(targetDiscordID) +
                        "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nViolation Image: **None Provided**";
            }
            else {
                return ":white_check_mark: **[System] Successfully Bot Abused " + targetDiscordID +
                        "**\nIssuing Team Member: **" + this.issuingTeamMember.get(this.discordID.lastIndexOf(targetDiscordID)) +
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
        c = Calendar.getInstance();
        Calendar cOld = Calendar.getInstance();

        if (this.config.testModeEnabled) {
            cOld.add(Calendar.HOUR_OF_DAY, -1); // Minus 1 Hour for Testing Purposes
        }
        else {
            // Take off 6 months
            cOld.add(Calendar.MONTH, -6);
        }
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
        if (prevOffenses < 4 && this.config.testModeEnabled) {
            // The Times are Short for Testing Purposes, they would usually be in days or months.
            if (prevOffenses == 0) { // 0 Prior Offenses - 1st Offense
                c.add(Calendar.MINUTE, 1);
            }
            else if (prevOffenses == 1) { // 1 Prior Offense - 2nd Offense
                c.add(Calendar.MINUTE, 3);
            }
            else if (prevOffenses == 2) { // 2 Prior Offenses - 3rd Offense
                c.add(Calendar.MINUTE, 5);
            }
            else if (prevOffenses == 3) { // 3 Prior Offenses - 4th Offense
                c.add(Calendar.MINUTE, 10);
            }
            return c.getTime(); // Set the Expiry Date
        }
        else if (prevOffenses < 4) {
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
    String undoBotAbuse(String teamMember, boolean isUndoingLast, long targetDiscordID) throws Exception {
        c = Calendar.getInstance();
        Calendar cTooLate = Calendar.getInstance();
        if (targetDiscordID == 0) {
            targetDiscordID = this.discordID.get(this.issuingTeamMember.lastIndexOf(teamMember));
        }
        cTooLate.setTime(this.issuedDates.get(this.issuingTeamMember.lastIndexOf(teamMember)));
        if (this.config.testModeEnabled) {
            cTooLate.add(Calendar.SECOND, 30);
        }
        else {
            cTooLate.add(Calendar.DAY_OF_MONTH, 5);
        }
        if (c.getTime().before(cTooLate.getTime()) && botAbuseIsCurrent(targetDiscordID) && isUndoingLast) {
            int index = this.issuingTeamMember.lastIndexOf(teamMember);
            this.discordID.remove(index);
            this.issuingTeamMember.remove(index);
            this.repOffenses.remove(index);
            this.issuedDates.remove(index);
            this.expiryDates.remove(index);
            this.reasons.remove(index);
            this.proofImages.remove(index);
            this.currentBotAbusers.remove(targetDiscordID);
        }
        else if ((c.getTime().before(cTooLate.getTime())) && botAbuseIsCurrent(targetDiscordID) && !isUndoingLast) {
            this.repOffenses.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.issuingTeamMember.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.issuedDates.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.expiryDates.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.reasons.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.proofImages.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.discordID.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.currentBotAbusers.remove(targetDiscordID);
        }
        else if (!(c.getTime().before(cTooLate.getTime())) && botAbuseIsCurrent(targetDiscordID)) {
            log.error("Undo Failed for " + targetDiscordID + " as this bot abuse is older than 5 days");
            return ":x: **[System] Undo Failed because Bot Abuses Older than 5 Days Cannot Be Undone.**";
        }
        else {
            log.error("Undo Failed for " + targetDiscordID + " as this player's bot abuse is no longer current");
            return ":x: **[System] Undo Failed Because This Bot Abuse Is No Longer Current!**";
        }

        if (arraySizesEqual()) {
            fileHandler.saveDatabase();
            this.sendMessage(2, targetDiscordID);
            log.info("Undo Successful for " + targetDiscordID);
            return ":white_check_mark: **[System] Undo Successful... So... Whatever it was you were doing... Try Again...**";
        }
        else {
            return ":x: **[System] FATAL ERROR: One of the Remove Operations in the Undo Method Did Not Run Successfully!" +
                    "\nPlease Wait While I Restart...**";
        }
    }
    String getInfo(long targetDiscordID, float timeOffset, boolean isTeamMember) { // This method is for queries
        int index = 0;
        int prevOffenses = 0;

        SimpleDateFormat sdfDateIssued = new SimpleDateFormat("MM-dd-yy HH:mm:ss zzz");
        SimpleDateFormat sdfDateExpired= new SimpleDateFormat("MM-dd-yy HH:mm:ss zzz");

        while (index < this.discordID.size()) {
            if (this.discordID.get(index) == targetDiscordID) {
                prevOffenses++;
            }
            index++;
        }
        if (botAbuseIsCurrent(targetDiscordID)) {
            Calendar dateIssued = Calendar.getInstance();
            Calendar dateToExpire = Calendar.getInstance();
            String result = ":information_source: " + targetDiscordID + " Bot Abuse Info: ";
            String trueOffset = this.offsetParsing(timeOffset);
            if (trueOffset != null) {
                sdfDateIssued.setTimeZone(TimeZone.getTimeZone(trueOffset));
                sdfDateExpired.setTimeZone(TimeZone.getTimeZone(trueOffset));
            }
            System.out.println(trueOffset);
            // Checking to see if the queried player is perm bot abused
            if (this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)) == null) {
                dateIssued.setTime(this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID)));

                if (!isTeamMember) {
                    return ":information_source: " + targetDiscordID + " Bot Abuse Info: " +
                            "\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nDate Issued: **" + sdfDateIssued.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **Never" +
                            "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "\n\n You have had " + (prevOffenses - 1) + " Previous Offenses**";
                }
                else {
                    return ":information_source: " + targetDiscordID + " Bot Abuse Info: " +
                            "\nIssuing Team Member: **" + this.issuingTeamMember.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nDate Issued: **" + sdfDateIssued.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **Never" +
                            "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "\n\nYou have had " + (prevOffenses - 1) + " Previous Offenses**";
                }
            }
            else { // They Are Currently Bot Abused but not permanently
                dateIssued.setTime(this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID)));
                dateToExpire.setTime(this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)));
                if (!isTeamMember && this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) == null) {
                    return ":information_source: " + targetDiscordID + " Bot Abuse Info: " +
                            "\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nDate Issued: **" + sdfDateIssued.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **" + sdfDateExpired.format(dateToExpire.getTime()) +
                            "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nViolation Image: **None Provided" +
                            "\n\nYou have had " + (prevOffenses - 1) + " Previous Offenses**";

                }
                else if (!isTeamMember) {
                    return ":information_source: " + targetDiscordID + " Bot Abuse Info: " +
                            "\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nDate Issued: **" + sdfDateIssued.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **" + sdfDateExpired.format(dateToExpire.getTime()) +
                            "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "\n\nYou have had " + (prevOffenses - 1) + " Previous Offenses**";
                }
                else {
                    return  result.concat(
                            "\nIssuing Team Member: " + this.issuingTeamMember.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nDate Issued: **" + sdfDateIssued.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **" + sdfDateExpired.format(dateToExpire.getTime()) +
                            "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "\n\nYou have had " + (prevOffenses - 1) + " Previous Offenses**");
                }
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
        c = Calendar.getInstance();
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
    long checkExpiredBotAbuse() throws Exception { // This is the method that gets run each second by the timer in Angel.DiscordBotMain
        c = Calendar.getInstance();
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
                this.currentBotAbusers.remove(targetDiscordID);
                if (this.config.testModeEnabled) {
                    System.out.println(this.discordID.toString() + "\n" + this.repOffenses.toString() +
                            "\n" + this.expiryDates.toString() + "\n" + this.reasons.toString() + "\n" + this.currentBotAbusers.toString());
                }
                fileHandler.saveDatabase();
                return targetDiscordID;

            }
            index--;
        }
        return 0;
    }
    String transferRecords(long oldDiscordID, long newDiscordID) throws IOException {
        int numTransferred = 0;
        // We scan over the entire discordID array and if the oldDiscordID matches we set the value to the newDiscordID
        while (this.discordID.contains(oldDiscordID)) {
            this.discordID.set(this.discordID.lastIndexOf(oldDiscordID), newDiscordID);
            numTransferred++;
        }
        boolean wasBotAbused = false;
        // Now we check if the player is currently Bot abused, their discordID will appear in the currentBotAbusers array if so.
        if (this.currentBotAbusers.contains(oldDiscordID)) {
            this.currentBotAbusers.set(this.currentBotAbusers.indexOf(oldDiscordID), newDiscordID);
            wasBotAbused = true;
        }
        // If we had records transferred and they weren't Bot Abused
        if (numTransferred > 0 && !wasBotAbused) {
            fileHandler.saveDatabase();
            return ":white_check_mark: **[System] Successfully Transferred " + numTransferred + " Records from " + oldDiscordID + " to " + newDiscordID + "\nThe Old Discord ID Was Not Bot Abused**";
        }
        // If we had records transferred and they were Bot Abused
        else if (numTransferred > 0) {
            fileHandler.saveDatabase();
            return ":white_check_mark: **[System] Successfully Transferred " + numTransferred + " Records from " + oldDiscordID + " to " + newDiscordID + "\nThe Old Discord ID Was Bot Abused at the Time and the Role was Transferred Over**";
        }
        // If we had No Records Transferred
        else {
            System.out.println("[System] No Records Transferred");
            return ":warning: [System] No Records Transferred";
        }
    }
    int clearRecords (long targetDiscordID) throws Exception { // For Handling Clearing all records of a Discord ID - Returns the Number of Records Cleared
        int clearedRecords = 0;
        // If we want to clear the records of a Discord ID then we go through the discordID array and remove the elements in all the corresponding arrays.
        while (this.discordID.contains(targetDiscordID)) {
            this.repOffenses.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.issuingTeamMember.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.issuedDates.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.expiryDates.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.reasons.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.proofImages.remove(this.discordID.lastIndexOf(targetDiscordID));
            this.discordID.remove(this.discordID.lastIndexOf(targetDiscordID));
            clearedRecords++;
        }
        this.currentBotAbusers.remove(targetDiscordID);
        if (this.config.testModeEnabled) {
            System.out.println(this.discordID.toString() + "\n" + this.repOffenses.toString() +
                    "\n" + this.expiryDates.toString() + "\n" + this.reasons.toString() + "\n" + this.currentBotAbusers.toString());
        }
        if (arraySizesEqual()) {
            log.info("Successfully Cleared Records for " + targetDiscordID);
            fileHandler.saveDatabase();
            return clearedRecords;
        }
        else {
            return -1;
        }
    }
    String seeHistory(long targetDiscordID, float timeOffset, boolean isTeamMember) {
        int index = 0;
        int recordsCount = 0;
        String output = "**/checkhistory Results";
        SimpleDateFormat sdfDateIssued = new SimpleDateFormat("MM-dd-yy HH:mm:ss zzz");
        SimpleDateFormat sdfDateExpired = new SimpleDateFormat("MM-dd-yy HH:mm:ss zzz");
        Calendar dateIssued = Calendar.getInstance();
        Calendar dateToExpire = Calendar.getInstance();

        if (isTeamMember) {
            output += "\n:information_source: " + targetDiscordID + "'s Bot Abuse History is as Follows: **";
        }
        else {
            output += "\n:information_source: Your Bot Abuse History is as Follows: **";
        }
        // Setting the TimeZones of both formatter objects
        String trueOffset = this.offsetParsing(timeOffset);
        if (trueOffset != null) {
            sdfDateIssued.setTimeZone(TimeZone.getTimeZone(trueOffset));
            sdfDateExpired.setTimeZone(TimeZone.getTimeZone(trueOffset));
        }
        System.out.println(trueOffset);
        // We check the discordID array and then get all the elements in the corresponding index of the other arrays
        while (index < this.discordID.size()) {
            if (this.discordID.get(index) == targetDiscordID) {
                dateIssued.setTime(this.issuedDates.get(index));
                dateToExpire.setTime(this.expiryDates.get(index));
                if (!isTeamMember) {
                    output = output.concat("\n\nOffense Number: **" + this.repOffenses.get(index)
                            + "\n**Date Issued: **" + sdfDateIssued.format(dateIssued.getTime())
                            + "\n**Date Expired: **" + sdfDateExpired.format(dateToExpire.getTime())
                            + "\n**Reason: **" + this.reasons.get(index)
                            + "\n**Proof Image: **" + this.proofImages.get(index) + "**");
                }
                else {
                    output = output.concat("\n\nOffense Number: **" + this.repOffenses.get(index)
                            + "\n**Issuing Team Member: **" + this.issuingTeamMember.get(index)
                            + "\n**Date Issued: **" + sdfDateIssued.format(dateIssued.getTime())
                            + "\n**Date Expired: **" + sdfDateExpired.format(dateToExpire.getTime())
                            + "\n**Reason: **" + this.reasons.get(index)
                            + "\n**Proof Image: **" + this.proofImages.get(index) + "**");
                }
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
    // Purpose 0 is a Bot Abuse Event while Purpose 1 is a Undo Event
    private void sendMessage(int purpose, long targetDiscordID) throws IOException {
        if (purpose == 0 || purpose == 1) {
            botAbuseVars.targetDiscordID = this.discordID.get(this.discordID.size() - 1);
            botAbuseVars.dateIssued = this.issuedDates.get(this.discordID.size() - 1);
            if (purpose == 0) {
                botAbuseVars.purpose = "botAbuse";
                botAbuseVars.dateToExpire = this.expiryDates.get(this.discordID.size() - 1);
            }
            else {
                botAbuseVars.purpose = "permBotAbuse";
                botAbuseVars.dateToExpire = null;
            }
            botAbuseVars.reason = this.reasons.get(this.discordID.size() - 1);
            botAbuseVars.imageURL = this.proofImages.get(this.discordID.size() - 1);
        }
        else if (purpose == 2) {
            undoVars.targetDiscordID = targetDiscordID;
        }
        if (config.testModeEnabled && (purpose == 0 || purpose == 1)) {
            System.out.println(fileHandler.gson.toJson(botAbuseVars));
        }
        else if (!config.testModeEnabled && (purpose == 0 || purpose == 1)) {
            rabbit.sendMessage(fileHandler.gson.toJson(botAbuseVars), "ReportCreatedEvent");
        }
        else if (config.testModeEnabled && purpose == 2) {
            System.out.println(fileHandler.gson.toJson(undoVars));
        }
        else if (!config.testModeEnabled && purpose == 2) {
            rabbit.sendMessage(fileHandler.gson.toJson(undoVars), "ReportUndoEvent");
        }
    }
    // This Method is primarily for Angel.DiscordBotMain, when users enter an offset,
    // this checks whether or not the string from the message checks out to be a valid number the program can use
    boolean checkOffset(String offset) {
        try {
            float parsedOffset = Float.parseFloat(offset);
            return parsedOffset <= 14 && parsedOffset >= -12;
        }
        catch (NumberFormatException ex) {
            return false;
        }
    }
    String offsetParsing(float timeOffset) {
        // What we do here is basically we process the timeOffset entered by the user into
        String strippedTimeOffset = String.valueOf(timeOffset);
        // Dividing any number by .5 would return a positive or negative odd number,
        // odd numbers have a remainder of 1 when divided by 2
        if (timeOffset != 100 && ((timeOffset / 0.5) % 2 == 1) || (timeOffset / -0.5) % 2 == 1) {
            // Ex 4.5 7.5
            if (strippedTimeOffset.charAt(1) == '.') {
                return "GMT+" + strippedTimeOffset.substring(0, 1) + ":30";
            }
            // Ex 10.5 -4.5
            else if (strippedTimeOffset.charAt(2) == '.') {
                // Ex Handles 10.5 11.5
                if (strippedTimeOffset.charAt(0) != '-') {
                    return "GMT+" + strippedTimeOffset.substring(0, 2) + ":30";
                }
                // Ex Handles -4.5 -6.5
                else {
                    return "GMT" + strippedTimeOffset.substring(0, 2) + ":30";
                }
            }
            // Ex Handles -10.5 -11.5
            else {
                return "GMT" + strippedTimeOffset.substring(0, 3) + ":30";
            }
        }
        else if (timeOffset >= 0 && timeOffset != 100) {
            // Because it's a float, we need to strip the trailing .0
            if (timeOffset >= 10) {
                return "GMT+" + strippedTimeOffset.substring(0, 2);
            } else {
                return "GMT+" + strippedTimeOffset.substring(0, 1);
            }
        }
        else if (timeOffset < 0 && timeOffset != 100) {
            if (timeOffset <= -10) {
                return "GMT" + strippedTimeOffset.substring(0, 3);
            }
            else {
                return "GMT" + strippedTimeOffset.substring(0, 2);
            }

        }
        else return null;
    }
    boolean arraySizesEqual() {
        return (this.discordID.size() == this.issuingTeamMember.size()) && (this.repOffenses.size() == this.issuedDates.size()) &&
                (this.expiryDates.size() == this.reasons.size()) && (this.reasons.size() == this.proofImages.size());
    }
}
abstract class CoreConfiguration {
    String systemPath;
    String host;
    boolean testModeEnabled;
    void setup(JsonObject configObj) {
        systemPath = configObj.get("systemPath").getAsString();
        host = configObj.get("host").getAsString();
        testModeEnabled = configObj.get("testModeEnabled").getAsBoolean();
    }
}
class BotAbuseVariables {
    String purpose = "botAbuse";
    long targetDiscordID;
    Date dateIssued;
    Date dateToExpire;
    String reason;
    String imageURL;
}
class UndoVariables {
    String purpose = "undoBotAbuse";
    long targetDiscordID;
}