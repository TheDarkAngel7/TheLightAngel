package Angel.BotAbuse;

import Angel.MainConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeoutException;

class BotAbuseCore { // This is where all the magic happens, where all the data is added and queried from the appropriate arrays to
    // Display all the requested data.
    Angel.BotAbuse.FileHandler fileHandler;
    private final Logger log = LogManager.getLogger(BotAbuseCore.class);
    BotAbuseConfiguration botConfig;
    MainConfiguration mainConfig;
    ArrayList<Long> discordID = new ArrayList<>();
    ArrayList<String> issuingTeamMember = new ArrayList<>();
    ArrayList<Integer> repOffenses = new ArrayList<>();
    ArrayList<Date> issuedDates = new ArrayList<>();
    ArrayList<Date> expiryDates = new ArrayList<>();
    ArrayList<String> reasons = new ArrayList<>();
    ArrayList<String> proofImages = new ArrayList<>();
    ArrayList<Long> currentBotAbusers = new ArrayList<>();
    Dictionary<String, String> reasonsDictionary = new Hashtable<>();
    private int indexOfLastOffense;
    private Calendar c;

    BotAbuseCore() throws IOException {
        this.fileHandler = new FileHandler(this);
    }
    void startup() throws IOException, TimeoutException {
        log.info("Bot Abuse Core Initiated...");
        try {
            fileHandler.getDatabase();
        }
        catch (IllegalStateException ex) {
            log.warn("No Data Existed in the Bot Abuse Arrays - Data File is Empty");
        }
    }
    void setBotConfig(BotAbuseConfiguration importBotConfig) {
        botConfig = importBotConfig;
    }

    String setBotAbuse(long targetDiscordID, boolean isPermanent, String reason, @Nullable String imageURL, String teamMember)
            throws IOException, NullPointerException {
        reason = reason.toLowerCase();
        SimpleDateFormat sdf = this.getDefaultSDF();
        if (!botAbuseIsCurrent(targetDiscordID)) {
            if (isPermanent) {
                this.reasons.add("Contact SAFE Team");
            }
            else {
                String getReason = reasonsDictionary.get(reason);
                if (getReason != null) {
                    this.reasons.add(getReason);
                }
                else {
                    return "**:x: [System] Invalid Reason!\nReason:** *" + reason + "*";
                }
            }
        }
        // We're checking to see if this player is currently Bot Abused - This Code would run if it was a moderator just using /botabuse like normal.
        if (!botAbuseIsCurrent(targetDiscordID) && !isPermanent) {
            // If they've been previously Bot Abused before then we need the index value of it
            if (indexOfLastOffense == -1) { // This is their first offense
                this.discordID.add(targetDiscordID);
                this.issuingTeamMember.add(teamMember);
                this.repOffenses.add(1);
                this.issuedDates.add(c.getTime());
                this.expiryDates.add(setExpiryDate(targetDiscordID));
                this.proofImages.add(imageURL);
                this.currentBotAbusers.add(targetDiscordID);

            }
            else {
                // The Bot Abuse Time gets progressively longer - This isn't their first offense
                this.discordID.add(targetDiscordID);
                this.issuingTeamMember.add(teamMember);
                this.repOffenses.add(this.repOffenses.get(indexOfLastOffense) + 1);
                this.issuedDates.add(c.getTime());
                this.expiryDates.add(setExpiryDate(targetDiscordID));
                this.proofImages.add(imageURL);
                this.currentBotAbusers.add(targetDiscordID);
            }
            if (this.mainConfig.testModeEnabled) {
                System.out.println(this.discordID.toString() + "\n" + this.repOffenses.toString() +
                        "\n" + this.expiryDates.toString() + "\n" + this.reasons.toString() + "\n" + this.currentBotAbusers.toString());
            }
            if (arraySizesEqual()) {
                fileHandler.saveDatabase();
                return ":white_check_mark: **[System] Successfully Bot Abused <@!" + targetDiscordID + ">" +
                        "**\nIssuing Team Member: **" + this.issuingTeamMember.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nDate Issued: **" + sdf.format(this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID))) +
                        "**\nExpiry Date: **" + getNewExpiryDate(targetDiscordID) +
                        "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) + "**";
            }
            else {
                return "**[System] FATAL ERROR: The Setting of the Bot Abuse did not run correctly and as a result I got inconsistent data**";
            }
        } // If a /permbotabuse was run and the Bot Abuse is still current.
        else if (botAbuseIsCurrent(targetDiscordID) && isPermanent) {
            // First we check to see if the current Bot Abuse is not permanent
            if (this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)) != null) {
                this.issuingTeamMember.set(this.discordID.lastIndexOf(targetDiscordID), teamMember);
                // The expiry date is changed to null for Permanent and the reason is updated to "Contact Staff"
                this.expiryDates.set(this.discordID.lastIndexOf(targetDiscordID), null);
                this.reasons.set(this.discordID.lastIndexOf(targetDiscordID), "Contact SAFE Team");
                if (this.mainConfig.testModeEnabled) {
                    System.out.println(this.discordID.toString() + "\n" + this.repOffenses.toString() +
                            "\n" + this.expiryDates.toString() + "\n" + this.reasons.toString() + "\n" + this.currentBotAbusers.toString());
                }
                fileHandler.saveDatabase();
                return ":white_check_mark: **[System - Admin Override] Successfully Overrode Bot Abuse for "
                        + targetDiscordID + " and it is now "
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
                this.issuedDates.add(c.getTime());
                this.expiryDates.add(null);
                this.proofImages.add(imageURL);
                this.currentBotAbusers.add(targetDiscordID);
            }
            else {
                // If we Try to Perm Bot Abuse someone that's never had a Bot Abuse offense before.
                this.discordID.add(targetDiscordID);
                this.issuingTeamMember.add(teamMember);
                this.repOffenses.add(1);
                this.issuedDates.add(c.getTime());
                this.expiryDates.add(null);
                this.proofImages.add(imageURL);
                this.currentBotAbusers.add(targetDiscordID);
            }
            if (this.mainConfig.testModeEnabled) {
                System.out.println(this.discordID.toString() + "\n" + this.repOffenses.toString() +
                        "\n" + this.expiryDates.toString() + "\n" + this.reasons.toString() + "\n" + this.currentBotAbusers.toString());
            }
            if (arraySizesEqual()) {
                fileHandler.saveDatabase();
            }
            else {
                return "**[System] FATAL ERROR: The Setting of the Bot Abuse did not run correctly and as a result I got inconsistent data**";
            }
            // Output to return from a perm Bot Abuse, we check to see if proofImages in the corresponding index is null, if so Violation image will say "None Provided"
            // If an image was provided then the else statement would run
            if (this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) == null) {
                return ":white_check_mark: **[System] Successfully Bot Abused <@!" + targetDiscordID + ">" +
                        "**\nIssuing Team Member: **" + this.issuingTeamMember.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nDate Issued: **" + sdf.format(this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID))) +
                        "**\nExpiry Date: **" + getNewExpiryDate(targetDiscordID) +
                        "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nViolation Image: **None Provided**";
            }
            else {
                return ":white_check_mark: **[System] Successfully Bot Abused <@!" + targetDiscordID + ">" +
                        "**\nIssuing Team Member: **" + this.issuingTeamMember.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nDate Issued: **" + sdf.format(this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID))) +
                        "**\nExpiry Date: **" + getNewExpiryDate(targetDiscordID) +
                        "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) + "**";
            }
        }
        else {
            // Checking to see if a moderator tried to bot abuse someone that is Permanently Bot Abused.
            // The Expiry Date will be null if that's the case.
            if (!botAbuseIsPermanent(targetDiscordID)) {
                return ":x: **[System] This Player is Already Bot Abused!**\nDiscord Account: <@!" + targetDiscordID + ">" +
                        "\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nExpiry Date: **" + sdf.format(this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID))) + "**";
            }
            else {
                return ":x: **[System] This Player is Permanently Bot Abused!!**\nDiscord Account: <@!" + targetDiscordID + ">" +
                        "\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                        "**\nExpiry Date: **Never**";
            }
        }
    }

    private Date setExpiryDate(long targetDiscordID) {
        Calendar cExp = Calendar.getInstance();
        int prevOffenses = this.getHotOffenses(targetDiscordID, true);
        if (prevOffenses < botConfig.botAbuseTimes.size()) {
            if (mainConfig.testModeEnabled) {
                switch (prevOffenses) {
                    case 0: cExp.add(Calendar.MINUTE, 1); break; // 0 Prior Offenses - 1st Offense
                    case 1: cExp.add(Calendar.MINUTE, 3); break; // 1 Prior Offense - 2nd Offense
                    case 2: cExp.add(Calendar.MINUTE, 5); break; // 2 Prior Offenses - 3rd Offense
                    case 3: cExp.add(Calendar.MINUTE, 10); break; // 3 Prior Offenses - 4th Offense
                }
            }
            // If Test Mode isn't enabled, use the configured times in days
            // prevOffenses would equal to the index value where the days are located
            else cExp.add(Calendar.DAY_OF_MONTH, botConfig.botAbuseTimes.get(prevOffenses));
            return cExp.getTime();
        }
        // Add Null if this is their 5th offense (Testing Mode) or
        // prevOffenses exceeds the size of the botAbuseTimes array - Permanent Bot Abuse
        else return null;
    }
    String undoBotAbuse(String teamMember, boolean isUndoingLast, long targetDiscordID) throws IOException {
        Calendar cTooLate = Calendar.getInstance();
        if (isUndoingLast) {
            // targetDiscordID would be 0 if this condition is true,
            // this gets the Discord ID of the player that they bot abused last
            targetDiscordID = this.discordID.get(this.issuingTeamMember.lastIndexOf(teamMember));
        }
        cTooLate.setTime(this.issuedDates.get(this.issuingTeamMember.lastIndexOf(teamMember)));
        if (this.mainConfig.testModeEnabled) {
            cTooLate.add(Calendar.SECOND, 30);
        }
        else {
            cTooLate.add(Calendar.DAY_OF_MONTH, botConfig.maxDaysAllowedForUndo);
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
            log.error("Undo Failed for " + targetDiscordID + " as this bot abuse is older than the configured "
                    + botConfig.maxDaysAllowedForUndo + " days");
            return ":x: **[System] Undo Failed for <@!" + targetDiscordID + "> because Bot Abuses Older than " + botConfig.maxDaysAllowedForUndo
                    + " Days Cannot Be Undone.**";
        }
        else {
            log.error("Undo Failed for <@!" + targetDiscordID + "> as this player's bot abuse is no longer current");
            return ":x: **[System] Undo Failed Because This Bot Abuse Is No Longer Current!**";
        }

        if (arraySizesEqual()) {
            fileHandler.saveDatabase();
            log.info("Undo Successful for " + targetDiscordID);
            return ":white_check_mark: **[System] Undo Successful... So... Whatever it was you were doing... Try Again...**";
        }
        else {
            return ":x: **[System] FATAL ERROR: One of the Remove Operations in the Undo Method Did Not Run Successfully!" +
                    "\nPlease Wait While I Restart...**";
        }
    }
    String getInfo(long targetDiscordID, double timeOffset, boolean isTeamMember) { // This method is for queries
        int prevOffenses = this.getLifetimeOffenses(targetDiscordID);

        SimpleDateFormat sdf = this.getDefaultSDF();

        if (botAbuseIsCurrent(targetDiscordID)) {
            Calendar dateIssued = Calendar.getInstance();
            Calendar dateToExpire = Calendar.getInstance();
            String result = ":information_source: <@!" + targetDiscordID + ">'s Bot Abuse Info: ";
            String trueOffset = this.offsetParsing(timeOffset);
            if (trueOffset != null) {
                sdf.setTimeZone(TimeZone.getTimeZone(trueOffset));
            }
            if (mainConfig.testModeEnabled) System.out.println(trueOffset);
            // Checking to see if the queried player is perm bot abused
            dateIssued.setTime(this.issuedDates.get(this.discordID.lastIndexOf(targetDiscordID)));
            if (botAbuseIsPermanent(targetDiscordID)) {
                if (!isTeamMember && this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) == null) {
                    return result.concat(
                            "\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nDate Issued: **" + sdf.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **" + sdf.format(dateToExpire.getTime()) +
                            "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nViolation Image: **None Provided**" +
                            "\n\nYou have had " + (prevOffenses - 1) + " Previous Lifetime Offenses**");
                }
                else if (!isTeamMember) {
                    return result.concat(
                            "\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nDate Issued: **" + sdf.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **Never" +
                            "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "\n\n You have had " + (prevOffenses - 1) + " Previous Lifetime Offenses**");
                }
                else {
                    return result.concat(
                            "\nIssuing Team Member: **" + this.issuingTeamMember.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nDate Issued: **" + sdf.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **Never" +
                            "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "\n\nYou have had " + (prevOffenses - 1) + " Previous Lifetime Offenses**");
                }
            }
            else { // They Are Currently Bot Abused but not permanently
                dateToExpire.setTime(this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)));
                if (!isTeamMember) {
                    return result.concat(
                            "\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nDate Issued: **" + sdf.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **" + sdf.format(dateToExpire.getTime()) +
                            "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "\n\nYou have had " + (prevOffenses - 1) + " Previous Lifetime Offenses**" +
                            "\nYou also have " + this.getHotOffenses(targetDiscordID, false) + " Hot offenses");
                }
                else {
                    return result.concat(
                            "\nIssuing Team Member: " + this.issuingTeamMember.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "\nOffense Number: **" + this.repOffenses.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nDate Issued: **" + sdf.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **" + sdf.format(dateToExpire.getTime()) +
                            "**\nReason: **" + this.reasons.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "**\nViolation Image: **" + this.proofImages.get(this.discordID.lastIndexOf(targetDiscordID)) +
                            "\n\nThey have had " + (prevOffenses - 1) + " Previous Lifetime Offenses**" +
                            "\nThey also have " + this.getHotOffenses(targetDiscordID, false) + " Hot offenses");
                }
            }
        }
        // They're Not Bot Abused
        else {
            return ":white_check_mark: Lucky for you... you're not Bot Abused Currently" +
                    "\n" +
                    "\nNumber of Lifetime Bot Abuses: **" + this.getLifetimeOffenses(targetDiscordID) + "**" +
                    "\nNumber of Hot Bot Abuses: **" + this.getHotOffenses(targetDiscordID, false) + "**" +
                    "\n\n*Hot Bot Abuses are offenses that took place less than **" + botConfig.hotOffenseMonths + "** months old*" +
                    "\n*Psst... They're also called \"Hot\" because they haven't cooled down*";
        }
    }
    private String getNewExpiryDate(long targetDiscordID) { // This Method gets called only when a new Bot Abuse is applied
        SimpleDateFormat sdf = this.getDefaultSDF();
        if (botAbuseIsPermanent(targetDiscordID)) {
            return "Permanent";
        }
        // They're Not Perm Bot Abused
        else {
            return sdf.format(this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)));
        }
    }
    int getHotOffenses(long targetDiscordID, boolean setupNewOffense) {
        Calendar cOld = Calendar.getInstance();
        int discordIDSize = this.discordID.size();
        if (setupNewOffense) discordIDSize = discordIDSize - 1;

        if (this.mainConfig.testModeEnabled) {
            cOld.add(Calendar.HOUR_OF_DAY, -1); // Minus 1 Hour for Testing Purposes
        }
        else {
            // Take off the configured number of months
            cOld.add(Calendar.MONTH, botConfig.hotOffenseMonths * -1);
        }
        int index = 0;
        int prevOffenses = 0;
        // We check for discordID.size() - 1 & expiryDates.size() != 0 because the
        // discordID array has the ID already added to it and the expiryDates array hasn't been touched yet
        // so the size of discordID size would be 1 more than the size of the expiryDates array
        while (index < discordIDSize && this.expiryDates.size() != 0) {
            // Here we're checking to see if the discordID at the current index matches the targetDiscordID
            // We also check the expiryDate at that index and see if it is after the Date where the records would
            // otherwise be ignored by the bot, records whose expiryDates are before the cOld time would be ignored.
            if (this.discordID.get(index) == targetDiscordID && this.expiryDates.get(index).after(cOld.getTime())) {
                prevOffenses++;
            }
            index++;
        }
        return prevOffenses;
    }
    // There's a difference between Hot Offenses and Lifetime Offenses
    // Hot Offenses are offenses that took place less than an hour ago in testing mode, and less than the configured number of months outside of testing mode
    // Lifetime Offenses are offenses that took place and they add up forever, regardless of how long ago they took place
    int getLifetimeOffenses(long targetDiscordID) {
        int index = 0;
        int prevOffenses = 0;

        while (index < this.discordID.size()) {
            if (this.discordID.get(index++) == targetDiscordID) {
                prevOffenses++;
            }
        }
        return prevOffenses;
    }
    boolean botAbuseIsCurrent(long targetDiscordID) { // Returns True if the targetDiscordID is Bot Abused
        try {
            // The ExpiryDates array will have a null value for the targetDiscordID if it's a Permanent Bot Abuse - Return true
            if (botAbuseIsPermanent(targetDiscordID)) {
                return true;
            }
            else {
                // Otherwise return true or false if the date in the expiryDates array is after current time,
                // return true if the Bot Abuse is still current
                // return false if the Bot Abuse is not current
                this.indexOfLastOffense = discordID.lastIndexOf(targetDiscordID);
                return c.getTime().before(this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)));
            }
        }
        catch (IndexOutOfBoundsException ex) { // discordId.lastIndexOf(targetDiscordID) returning -1
            // in the else statement will cause this, a -1 indicates the target discord ID wasn't found in the discordID
            // array, which means it's their first offense and they've never been
            // Bot Abused before, so return false.
            this.indexOfLastOffense = -1;
            return false;
        }
    }
    boolean botAbuseIsPermanent(long targetDiscordID) {
        return this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID)) == null;
    }
    long checkExpiredBotAbuse() throws IOException { // This is the method that gets run each second by the timer in Angel.DiscordBotMain
        // Because this method gets run every second, we advance the calendar object too.
        c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("GMT"));
        int index = this.discordID.size() - 1;
        while (index >= 0) {
            long targetDiscordID = this.discordID.get(index--);
            Date targetDate = this.expiryDates.get(this.discordID.lastIndexOf(targetDiscordID));
            // This Catches the program from trying to remove a permanent Bot Abuse
            if (botAbuseIsPermanent(targetDiscordID)) {
                // Take No Action
            }
            // If the targetDate is before the current time and the player is currently Bot Abused then remove their Bot Abuse
            else if (targetDate.before(c.getTime()) && this.currentBotAbusers.contains(targetDiscordID)) {
                this.currentBotAbusers.remove(targetDiscordID);
                if (this.mainConfig.testModeEnabled) {
                    System.out.println(this.discordID.toString() + "\n" + this.repOffenses.toString() +
                            "\n" + this.expiryDates.toString() + "\n" + this.reasons.toString() + "\n" + this.currentBotAbusers.toString());
                }
                fileHandler.saveDatabase();
                return targetDiscordID;
            }
        }
        // If the while loop completes without removing any expired bot abuses,
        // then return 0 to indicate nothing got removed
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
            return ":white_check_mark: **[System] Successfully Transferred " + numTransferred + " Records from " +
                    "" + oldDiscordID + " to " + newDiscordID +
                    "\nThe Old Discord ID Was Not Bot Abused**";
        }
        // If we had records transferred and they were Bot Abused
        else if (numTransferred > 0) {
            fileHandler.saveDatabase();
            return ":white_check_mark: **[System] Successfully Transferred " + numTransferred + " Records from " +
                    "" + oldDiscordID + " to " + newDiscordID +
                    "\nThe Old Discord ID Was Bot Abused at the Time and the Role was Transferred Over**";
        }
        // If we had No Records Transferred
        else {
            return ":warning: [System] No Records Transferred";
        }
    }
    int clearRecords (long targetDiscordID) throws IOException { // For Handling Clearing all records of a Discord ID - Returns the Number of Records Cleared
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
        if (this.mainConfig.testModeEnabled) {
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
    String seeHistory(long targetDiscordID, double timeOffset, boolean isTeamMember) {
        int index = 0;
        int recordsCount = 0;
        String output = "**/checkhistory Results";
        SimpleDateFormat sdf = this.getDefaultSDF();
        Calendar dateIssued = Calendar.getInstance();
        Calendar dateToExpire = Calendar.getInstance();
        if (isTeamMember) {
            output += "\n:information_source: <@!" + targetDiscordID + ">'s Bot Abuse History is as Follows: **";
        }
        else {
            output += "\n:information_source: Your Bot Abuse History is as Follows: **";
        }
        // Setting the TimeZones of both formatter objects
        String trueOffset = this.offsetParsing(timeOffset);
        if (trueOffset != null) {
            sdf.setTimeZone(TimeZone.getTimeZone(trueOffset));
        }
        // We check the discordID array and then get all the elements in the corresponding index of the other arrays
        while (index < this.discordID.size()) {
            if (this.discordID.get(index) == targetDiscordID) {
                boolean recordIsPermanent = this.expiryDates.get(index) == null;
                dateIssued.setTime(this.issuedDates.get(index));
                if (!recordIsPermanent) {
                    dateToExpire.setTime(this.expiryDates.get(index));
                }
                if (!isTeamMember && !recordIsPermanent) {
                    output = output.concat("\n\nOffense Number: **" + this.repOffenses.get(index)
                            + "\n**Date Issued: **" + sdf.format(dateIssued.getTime())
                            + "\n**Date Expired: **" + sdf.format(dateToExpire.getTime())
                            + "\n**Reason: **" + this.reasons.get(index)
                            + "\n**Proof Image: **" + this.proofImages.get(index) + "**");
                }
                else if (isTeamMember && !recordIsPermanent) {
                    output = output.concat("\n\nOffense Number: **" + this.repOffenses.get(index)
                            + "\n**Issuing Team Member: **" + this.issuingTeamMember.get(index)
                            + "\n**Date Issued: **" + sdf.format(dateIssued.getTime())
                            + "\n**Date Expired: **" + sdf.format(dateToExpire.getTime())
                            + "\n**Reason: **" + this.reasons.get(index)
                            + "\n**Proof Image: **" + this.proofImages.get(index) + "**");
                }
                else if (!isTeamMember) {
                    output = output.concat("\n\nOffense Number: **" + this.repOffenses.get(index)
                            + "\n**Date Issued: **" + sdf.format(dateIssued.getTime())
                            + "\n**Expiry Date: **Never"
                            + "\n**Reason: **" + this.reasons.get(index)
                            + "\n**Proof Image: **" + this.proofImages.get(index) + "**");
                }
                else {
                    output = output.concat("\n\nOffense Number: **" + this.repOffenses.get(index)
                            + "\n**Issuing Team Member: **" + this.issuingTeamMember.get(index)
                            + "\n**Date Issued: **" + sdf.format(dateIssued.getTime())
                            + "\n**Expiry Date: **Never"
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
            output = output.concat("\n\nRecords Count: " + recordsCount
            + "\nHot Records Count: " + getHotOffenses(targetDiscordID, false));
            return output;
        }
    }
    // Reasons Manager Methods
    // keyReasonWild can either be used as mapping to an existing key or a new reason, nicknamed a "Wild Card"
    String addReason(boolean mapToExistingKey, String newKey, String keyReasonWild)
            throws IOException {
        newKey = newKey.toLowerCase();
        if (mapToExistingKey) keyReasonWild = keyReasonWild.toLowerCase();
        String returnValue;
        if (!mapToExistingKey) {
            reasonsDictionary.put(newKey, keyReasonWild);
            returnValue = "**[System] Successfully Mapped the reason *" + keyReasonWild + "* to the reason key *" + newKey + "***";
        }
        else {
            reasonsDictionary.put(newKey, reasonsDictionary.get(keyReasonWild));
            returnValue = "**[System] Successfully Mapped the reason *" + reasonsDictionary.get(keyReasonWild) +
                    "* to the reason key *" + newKey + "***";
        }
        fileHandler.saveDatabase();
        return returnValue;
    }
    String deleteReason(String key) throws IOException {
        String returnValue;
        String removedReason = reasonsDictionary.remove(key);
        if (removedReason != null) {
            returnValue = "**:white_check_mark: [System] Successfully Removed the reason *" + removedReason
                    + "* which was mapped to key *" + key + "***";
            log.info("Successfully Removed the Reason \"" + removedReason + "\" which was mapped to key \"" + key + "\"");
            fileHandler.saveDatabase();
        }
        else {
            returnValue = "**:x: [System] No Reason Existed under key *" + key + "***";
            log.error("No Reason Found with key \"" + key + "\"");
        }
        return returnValue;
    }
    // This Method is primarily for Angel.DiscordBotMain, when users enter an offset,
    // this checks whether or not the string from the message checks out to be a valid number the program can use
    boolean checkOffset(String offset) {
        try {
            double parsedOffset = Double.parseDouble(offset);
            return parsedOffset <= 14 && parsedOffset >= -12;
        }
        catch (NumberFormatException ex) {
            return false;
        }
    }
    String offsetParsing(double timeOffset) {
        // What we do here is basically we process the timeOffset entered by the user into
        String strippedTimeOffset = String.valueOf(timeOffset);
        // Dividing any number that has a .5 trailing by .5 would return a positive or negative odd number,
        // odd numbers have a remainder of 1 when divided by 2
        if (timeOffset != 100 && ((timeOffset / 0.5) % 2 == 1) || (timeOffset / -0.5) % 2 == 1) {
            // Ex 4.5 7.5
            if (strippedTimeOffset.charAt(1) == '.') {
                return "GMT+" + strippedTimeOffset.substring(0, 1).concat(":30");
            }
            // Ex 10.5 -4.5
            else if (strippedTimeOffset.charAt(2) == '.') {
                // Ex Handles 10.5 11.5
                if (strippedTimeOffset.charAt(0) != '-') {
                    return "GMT+" + strippedTimeOffset.substring(0, 2).concat(":30");
                }
                // Ex Handles -4.5 -6.5
                else {
                    return "GMT" + strippedTimeOffset.substring(0, 2).concat(":30");
                }
            }
            // Ex Handles -10.5 -11.5
            else {
                return "GMT" + strippedTimeOffset.substring(0, 3).concat(":30");
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
    boolean timingsAreValid() {
        if (botConfig.botAbuseTimes.get(botConfig.botAbuseTimes.size() - 1) <= (botConfig.hotOffenseMonths * 30) / 2) {
            int index = 1;
            int dayTotal = 0;
            while (index < botConfig.botAbuseTimes.size()) {
                if (botConfig.botAbuseTimes.get(index) <= botConfig.botAbuseTimes.get(index - 1)) {
                    botConfig.botAbuseTimes.sort(Comparator.naturalOrder());
                    index = 1;
                    dayTotal = 0;
                }
                else if (dayTotal > botConfig.hotOffenseMonths * 30) return false;
                else {
                    if (index == 1) dayTotal = dayTotal + botConfig.botAbuseTimes.get(0);
                    dayTotal = dayTotal + botConfig.botAbuseTimes.get(index++);
                }
            }
            return true;
        }
        else return false;
    }
    private SimpleDateFormat getDefaultSDF() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yy HH:mm:ss zzz");
        sdf.setTimeZone(TimeZone.getTimeZone(mainConfig.timeZone));
        return sdf;
    }
}