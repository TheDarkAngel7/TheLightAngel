package Angel.BotAbuse;

import Angel.MainConfiguration;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Guild;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

class BotAbuseCore { // This is where all the magic happens, where all the data is added and queried from the appropriate arrays to
    // Display all the requested data.
    Angel.BotAbuse.FileHandler fileHandler;
    private final Logger log = LogManager.getLogger(BotAbuseCore.class);
    private final Guild guild;
    private BotAbuseConfiguration botConfig;
    private final MainConfiguration mainConfig;
    private List<BotAbuseRecord> records = new ArrayList<>();
    Dictionary<String, String> reasonsDictionary = new Hashtable<>();
    private Calendar c;

    BotAbuseCore(Guild guild, MainConfiguration mainConfig) {
        this.fileHandler = new FileHandler(this);
        this.guild = guild;
        this.mainConfig = mainConfig;
    }
    void startup(boolean firstStartup) throws IOException {
        try {
            if (firstStartup) log.info("Bot Abuse Core Initiated...");
            else log.info("Bot Abuse Database Gotten From Resume...");
            fileHandler.getDatabase();
        }
        catch (IllegalStateException ex) {
            log.warn("No Data Existed in the Bot Abuse Arrays - Data File is Empty");
        }
    }
    void setBotConfig(BotAbuseConfiguration botConfig) {
        this.botConfig = botConfig;
    }
    JsonObject getConfig() throws IOException {
        return fileHandler.getConfig();
    }
    void saveDatabase() throws IOException {
        fileHandler.saveDatabase();
    }

    String setBotAbuse(long targetDiscordID, boolean isPermanent, String reason, @Nullable String imageURL, long teamMember)
            throws IOException, NullPointerException {
        reason = reason.toLowerCase();
        String getReason = "";
        BotAbuseRecord thisRecord = null;
        SimpleDateFormat sdf = this.getDefaultSDF();
        if (!botAbuseIsCurrent(targetDiscordID)) {
            if (isPermanent) {
                getReason = "Contact SAFE Team";
            }
            else {
                getReason = reasonsDictionary.get(reason);
                if (getReason == null) {
                    return "**:x: Invalid Reason!\nReason:** *" + reason + "*";
                }
            }
        }
        // We're checking to see if this player is currently Bot Abused - This Code would run if it was a moderator just using /botabuse like normal.
        if (!botAbuseIsCurrent(targetDiscordID) && !isPermanent) {
            // If they've been previously Bot Abused before then we need the index value of it
            if (getLastRecord(targetDiscordID) == null) { // This is their first offense
                thisRecord = new BotAbuseRecord(
                        getNextID(), targetDiscordID, teamMember, 1, c.getTime(), getExpiryDate(targetDiscordID), getReason, imageURL);
                records.add(thisRecord);
            }
            else {
                // The Bot Abuse Time gets progressively longer - This isn't their first offense
                thisRecord = new BotAbuseRecord(getNextID(), targetDiscordID, teamMember, getLastRecord(targetDiscordID).getRepOffenses() + 1,
                        c.getTime(), getExpiryDate(targetDiscordID), getReason, imageURL);
                records.add(thisRecord);
            }
            if (this.mainConfig.testModeEnabled) {
                System.out.println(thisRecord.getDiscordID() + "\n" + thisRecord.getRepOffenses() +
                        "\n" + thisRecord.getExpiryDate().toString() + "\n" + thisRecord.getReason());
            }
            fileHandler.saveDatabase();
            return ":white_check_mark: **Successfully Bot Abused <@!" + targetDiscordID + ">" +
                    "**\nBot Abuse ID: **" + thisRecord.getId() +
                    "**\nIssuing Team Member: <@!" + thisRecord.getIssuingTeamMember() + ">" +
                    "\nOffense Number: **" + thisRecord.getRepOffenses() +
                    "**\nDate Issued: **" + sdf.format(thisRecord.getIssuedDate()) +
                    "**\nExpiry Date: **" + getNewExpiryDate(targetDiscordID) +
                    "**\nReason: **" + thisRecord.getReason()+
                    "**\nViolation Image: **" + thisRecord.getProofImage() + "**";
        } // If a /permbotabuse was run and the Bot Abuse is still current.
        else if (botAbuseIsCurrent(targetDiscordID) && isPermanent) {
            thisRecord = getLastRecord(targetDiscordID);
            // First we check to see if the current Bot Abuse is not permanent
            if (thisRecord.getExpiryDate() != null) {
                thisRecord.setNewTeamMember(teamMember);
                // The expiry date is changed to null for Permanent and the reason is updated to "Contact Staff"
                thisRecord.setExpiryDateAsPermanent();
                thisRecord.setReason("Contact SAFE Team");
                if (this.mainConfig.testModeEnabled) {
                    System.out.println(thisRecord.getDiscordID() + "\n" + thisRecord.getRepOffenses() +
                            "\n" + thisRecord.getExpiryDate().toString() + "\n" + thisRecord.getReason());
                }
                fileHandler.saveDatabase();
                return ":white_check_mark: **[System - Admin Override] Successfully Overrode Bot Abuse for <@!"
                        + targetDiscordID + "> and it is now "
                        + this.getNewExpiryDate(targetDiscordID) + "**";
            }
            // Here we're saying player is already Permanently Bot Abused
            else {
                return ":x: **[System - Admin Override] This Player is Already Permanently Bot Abused**";
            }
        }
        else if (!botAbuseIsCurrent(targetDiscordID) && isPermanent) {
            if (getLastRecord(targetDiscordID) == null) {
                thisRecord = new BotAbuseRecord(getNextID(), targetDiscordID, teamMember, getLastRecord(targetDiscordID).getRepOffenses() + 1,
                        c.getTime(), null, getReason, imageURL);

            }
            else {
                // If we Try to Perm Bot Abuse someone that's never had a Bot Abuse offense before.
                thisRecord = new BotAbuseRecord(getNextID(), targetDiscordID, teamMember, 1,
                        c.getTime(), null, getReason, imageURL);
            }
            if (this.mainConfig.testModeEnabled) {
                thisRecord = new BotAbuseRecord(getNextID(), targetDiscordID, teamMember, getLastRecord(targetDiscordID).getRepOffenses() + 1,
                        c.getTime(), null, getReason, imageURL);
            }
            fileHandler.saveDatabase();
            // Output to return from a perm Bot Abuse, we check to see if proofImages in the corresponding index is null, if so Violation image will say "None Provided"
            // If an image was provided then the else statement would run
            if (thisRecord.getProofImage() == null) {
                return ":white_check_mark: **Successfully Bot Abused <@!" + targetDiscordID + ">" +
                        "**\nBot Abuse ID: **" + thisRecord.getId() +
                        "**\nIssuing Team Member: <!@" + thisRecord.getIssuingTeamMember() + ">" +
                        "**\nOffense Number: **" + thisRecord.getRepOffenses() +
                        "**\nDate Issued: **" + sdf.format(thisRecord.getIssuedDate()) +
                        "**\nExpiry Date: **" + getNewExpiryDate(targetDiscordID) +
                        "**\nReason: **" + thisRecord.getReason() +
                        "**\nViolation Image: **None Provided**";
            }
            else {
                return ":white_check_mark: **Successfully Bot Abused <@!" + targetDiscordID + ">" +
                        "**\nBot Abuse ID: **" + thisRecord.getId() +
                        "**\nIssuing Team Member: <!@" + thisRecord.getIssuingTeamMember() + ">" +
                        "**\nOffense Number: **" + thisRecord.getRepOffenses() +
                        "**\nDate Issued: **" + sdf.format(thisRecord.getIssuedDate()) +
                        "**\nExpiry Date: **" + getNewExpiryDate(targetDiscordID) +
                        "**\nReason: **" + thisRecord.getReason() +
                        "**\nViolation Image: **" + thisRecord.getProofImage() + "**";
            }
        }
        else {
            // Checking to see if a moderator tried to bot abuse someone that is Permanently Bot Abused.
            // The Expiry Date will be null if that's the case.
            if (!botAbuseIsPermanent(targetDiscordID)) {
                thisRecord = getLastRecord(targetDiscordID);
                return ":x: **This Player is Already Bot Abused!**\nDiscord Account: <@!" + targetDiscordID + ">" +
                        "**\nBot Abuse ID: **" + thisRecord.getId() +
                        "\nOffense Number: **" + thisRecord.getRepOffenses() +
                        "**\nExpiry Date: **" + sdf.format(thisRecord.getExpiryDate()) + "**";
            }
            else {
                return ":x: **This Player is Already Bot Abused!**\nDiscord Account: <@!" + targetDiscordID + ">" +
                        "**\nBot Abuse ID: **" + thisRecord.getId() +
                        "\nOffense Number: **" + thisRecord.getRepOffenses() +
                        "**\nExpiry Date: **Never**";
            }
        }
    }
    private Date getExpiryDate(long targetDiscordID) {
        Calendar cExp = Calendar.getInstance();
        int prevOffenses = this.getHotOffenses(targetDiscordID);
        if (mainConfig.testModeEnabled) {
            try {
                cExp.add(Calendar.MINUTE, botConfig.getBotAbuseTimes().get(prevOffenses));
            }
            catch (IndexOutOfBoundsException ex) {
                if (!botConfig.isAutoPermanent()) cExp.add(Calendar.MINUTE,
                        botConfig.getBotAbuseTimes().get(botConfig.getBotAbuseTimes().size() - 1));
                else return null;
            }
        }
        // If Test Mode isn't enabled, use the configured times in days
        // prevOffenses would equal to the index value where the days are located
        else {
            try {
                cExp.add(Calendar.DAY_OF_MONTH, botConfig.botAbuseTimes.get(prevOffenses));
            }
            catch (IndexOutOfBoundsException ex) {
                if (!botConfig.isAutoPermanent()) cExp.add(Calendar.DAY_OF_MONTH,
                        botConfig.getBotAbuseTimes().get(botConfig.getBotAbuseTimes().size() - 1));
                else return null;
            }
        }
        return cExp.getTime();
    }
    private int getNextID() {
        int newID;
        List<Integer> IDs = new ArrayList<>();
        records.forEach(r -> {
            IDs.add(r.getId());
        });

        do {
            newID = (int) (Math.random() * 1000000);
        } while (IDs.contains(newID) || newID < 100000);
        return newID;
    }
    String undoBotAbuse(long teamMember, boolean isUndoingLast, long targetDiscordID) throws IOException {
        Calendar cTooLate = Calendar.getInstance();
        int id;
        BotAbuseRecord thisRecord = null;
        if (isUndoingLast) {
            // targetDiscordID would be 0 if this condition is true,
            // this gets the Discord ID of the player that they bot abused last
            thisRecord = getLastRecordByTeamMember(teamMember);
            targetDiscordID = thisRecord.getDiscordID();
        }
        else {
            thisRecord = getLastRecord(targetDiscordID);
            if (thisRecord.getIssuingTeamMember() != teamMember) {
                return ":x: **You Cannot Undo Someone Elses Bot Abuse**";
            }
        }
        cTooLate.setTime(thisRecord.getIssuedDate());
        if (this.mainConfig.testModeEnabled) {
            cTooLate.add(Calendar.HOUR_OF_DAY, botConfig.getMaxDaysAllowedForUndo());
        }
        else {
            cTooLate.add(Calendar.DAY_OF_MONTH, botConfig.getMaxDaysAllowedForUndo());
        }
        if (c.getTime().before(cTooLate.getTime()) && botAbuseIsCurrent(targetDiscordID)) {
            records.remove(thisRecord);
        }
        else if (!(c.getTime().before(cTooLate.getTime())) && botAbuseIsCurrent(targetDiscordID)) {
            log.error("Undo Failed for " + guild.getMemberById(targetDiscordID).getUser().getAsTag() +
                    " as this bot abuse is older than the configured "
                    + botConfig.getMaxDaysAllowedForUndo() + " days");
            return ":x: **Undo Failed for <@" + targetDiscordID + "> because Bot Abuses Older than " + botConfig.getMaxDaysAllowedForUndo()
                    + " Days Cannot Be Undone.**";
        }
        else {
            log.error("Undo Failed for " + guild.getMemberById(targetDiscordID).getUser().getAsTag() +
                    " as this player's bot abuse is no longer current");
            return ":x: **Undo Failed Because This Bot Abuse Is No Longer Current!**";
        }
        fileHandler.saveDatabase();
        log.info("Undo Successful for " + guild.getMemberById(targetDiscordID).getUser().getAsTag());
        return ":white_check_mark: **Successfully Undid Bot Abuse for <@" + thisRecord.getDiscordID() + ">**" +
                "\nID: " + thisRecord.getId() +
                "\n So... Whatever it was you were doing... Try Again...";
    }
    String getInfo(long targetDiscordID, double timeOffset, boolean isTeamMember) { // This method is for queries
        int prevOffenses = this.getLifetimeOffenses(targetDiscordID);

        SimpleDateFormat sdf = this.getDefaultSDF();
        BotAbuseRecord thisRecord = getLastRecord(targetDiscordID);

        if (botAbuseIsCurrent(targetDiscordID)) {
            Calendar dateIssued = Calendar.getInstance();
            Calendar dateToExpire = Calendar.getInstance();
            String result = ":information_source: <@!" + targetDiscordID + ">'s Bot Abuse Info: ";
            String trueOffset = this.getTimeZoneString(timeOffset);
            if (trueOffset != null) {
                sdf.setTimeZone(TimeZone.getTimeZone(trueOffset));
            }
            if (mainConfig.testModeEnabled) System.out.println(trueOffset);
            // Checking to see if the queried player is perm bot abused
            dateIssued.setTime(thisRecord.getIssuedDate());
            if (botAbuseIsPermanent(targetDiscordID)) {
                if (!isTeamMember && thisRecord.getProofImage() == null) {
                    return result.concat(
                            "\nBot Abuse ID: **" + thisRecord.getId() +
                            "**\nOffense Number: **" + thisRecord.getRepOffenses() +
                            "**\nDate Issued: **" + sdf.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **" + sdf.format(dateToExpire.getTime()) +
                            "**\nReason: **" + thisRecord.getReason() +
                            "**\nViolation Image: **None Provided**" +
                            "\n\nYou have had " + (prevOffenses - 1) + " Previous Offenses**");
                }
                else if (!isTeamMember) {
                    return result.concat(
                            "\nBot Abuse ID: **" + thisRecord.getId() +
                            "**\nOffense Number: **" + thisRecord.getRepOffenses() +
                            "**\nDate Issued: **" + sdf.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **Never" +
                            "**\nReason: **" + thisRecord.getReason() +
                            "**\nViolation Image: **" + thisRecord.getProofImage() +
                            "\n\n You have had " + (prevOffenses - 1) + " Previous Offenses**");
                }
                else {
                    return result.concat(
                            "\nBot Abuse ID: **" + thisRecord.getId() +
                            "**\nIssuing Team Member: <@!" + thisRecord.getIssuingTeamMember() + ">" +
                            "\nOffense Number: **" + thisRecord.getRepOffenses() +
                            "**\nDate Issued: **" + sdf.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **Never" +
                            "**\nReason: **" + thisRecord.getReason() +
                            "**\nViolation Image: **" + thisRecord.getProofImage() +
                            "\n\nYou have had " + (prevOffenses - 1) + " Previous Offenses**");
                }
            }
            else { // They Are Currently Bot Abused but not permanently
                dateToExpire.setTime(thisRecord.getExpiryDate());
                if (!isTeamMember) {
                    return result.concat(
                            "\nBot Abuse ID: **" + thisRecord.getId() +
                            "**\nOffense Number: **" + thisRecord.getRepOffenses() +
                            "**\nDate Issued: **" + sdf.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **" + sdf.format(dateToExpire.getTime()) +
                            "**\nReason: **" + thisRecord.getReason() +
                            "**\nViolation Image: **" + thisRecord.getProofImage() +
                            "\n\nYou have had " + (prevOffenses - 1) + " Previous Offenses" +
                            "\nYou also have " + this.getHotOffenses(targetDiscordID) + " Hot Offenses**");
                }
                else {
                    return result.concat(
                            "\nBot Abuse ID: **" + thisRecord.getId() +
                            "**\nIssuing Team Member: <@!" + thisRecord.getIssuingTeamMember() + ">" +
                            "\nOffense Number: **" + thisRecord.getRepOffenses() +
                            "**\nDate Issued: **" + sdf.format(dateIssued.getTime()) +
                            "**\nExpiry Date: **" + sdf.format(dateToExpire.getTime()) +
                            "**\nReason: **" + thisRecord.getReason() +
                            "**\nViolation Image: **" + thisRecord.getProofImage() +
                            "\n\nThey have had " + (prevOffenses - 1) + " Previous Offenses" +
                            "\nThey also have " + this.getHotOffenses(targetDiscordID) + " Hot Offenses**");
                }
            }
        }
        // They're Not Bot Abused
        else {
            return ":white_check_mark: Lucky for you... you're not Bot Abused Currently" +
                    "\n" +
                    "\nNumber of Lifetime Bot Abuses: **" + this.getLifetimeOffenses(targetDiscordID) + "**" +
                    "\nNumber of Hot Bot Abuses: **" + this.getHotOffenses(targetDiscordID) + "**" +
                    "\n\n*Hot Bot Abuses are offenses that took place less than **" + botConfig.getHotOffenseMonths() + "** months ago*" +
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
            return sdf.format(getLastRecord(targetDiscordID).getExpiryDate());
        }
    }
    int getHotOffenses(long targetDiscordID) {
        Calendar cOld = Calendar.getInstance();

        if (this.mainConfig.testModeEnabled) {
            cOld.add(Calendar.HOUR_OF_DAY, botConfig.getHotOffenseMonths() * -1);
        }
        else {
            // Take off the configured number of months
            cOld.add(Calendar.MONTH, botConfig.getHotOffenseMonths() * -1);
        }
        int index = 0;
        int prevOffenses = 0;
        // We check for discordID.size() - 1 & expiryDates.size() != 0 because the
        // discordID array has the ID already added to it and the expiryDates array hasn't been touched yet
        // so the size of discordID size would be 1 more than the size of the expiryDates array
        while (index < records.size() && records.size() != 0) {
            // Here we're checking to see if the discordID at the current index matches the targetDiscordID
            // We also check the expiryDate at that index and see if it is after the Date where the records would
            // otherwise be ignored by the bot, records whose expiryDates are before the cOld time would be ignored.
            if (records.get(index).getDiscordID() == targetDiscordID &&
                    (records.get(index).getExpiryDate() == null || 
                            records.get(index).getExpiryDate().after(cOld.getTime()))) {
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

        while (index < records.size()) {
            if (records.get(index++).getDiscordID() == targetDiscordID) {
                prevOffenses++;
            }
        }
        return prevOffenses;
    }
    boolean botAbuseIsCurrent(long targetDiscordID) { // Returns True if the targetDiscordID is Bot Abused
        // The ExpiryDates array will have a null value for the targetDiscordID if it's a Permanent Bot Abuse - Return true
        if (getLastRecord(targetDiscordID) == null) {
            return false;
        }
        else if (botAbuseIsPermanent(targetDiscordID)) {
            return true;
        }
        else {
            // Otherwise return true or false if the date in the expiryDates array is after current time,
            // return true if the Bot Abuse is still current
            // return false if the Bot Abuse is not current
            return c.getTime().before(getLastRecord(targetDiscordID).getExpiryDate());
        }
    }
    boolean botAbuseIsPermanent(long targetDiscordID) {
        if (getLastRecord(targetDiscordID) != null) {
            return getLastRecord(targetDiscordID).getExpiryDate() == null;
        }
        else return false;
    }
    long checkExpiredBotAbuse() throws IOException { // This is the method that gets run each second by the timer in Angel.DiscordBotMain
        // Because this method gets run every second, we advance the calendar object too.
        c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone(mainConfig.timeZone));
        int index = records.size() - 1;
        while (index >= 0) {
            long targetDiscordID = records.get(index).getDiscordID();
            Date targetDate = records.get(index).getExpiryDate();
            // This Catches the program from trying to remove a permanent Bot Abuse
            if (botAbuseIsPermanent(targetDiscordID)) {
                // Take No Action
            }
            // If the current time is after the target date then remove the bot abuse for that discord ID
            else if (c.getTime().after(targetDate) && records.get(index).isCurrentlyBotAbused()) {
                records.get(index).setBotAbuseAsExpired();
                if (this.mainConfig.testModeEnabled) {
                    System.out.println(records.get(index).getDiscordID() + "\n" + records.get(index).getRepOffenses() +
                            "\n" + records.get(index).getExpiryDate().toString() + "\n" + records.get(index).getReason());
                }
                fileHandler.saveDatabase();
                return targetDiscordID;
            }
            index--;
        }
        // If the while loop completes without removing any expired bot abuses,
        // then return 0 to indicate nothing got removed
        return 0;
    }
    String transferRecords(long oldDiscordID, long newDiscordID) throws IOException {
        // We scan over the entire discordID array and if the oldDiscordID matches we set the value to the newDiscordID
        boolean wasBotAbused = botAbuseIsCurrent(oldDiscordID);
        if (!getAllRecordsByID(oldDiscordID).isEmpty()) {
            getAllRecordsByID(oldDiscordID).forEach(r -> r.setDiscordID(newDiscordID));
        }
        // If we had records transferred and they weren't Bot Abused
        int numTransferred = getAllRecordsByID(newDiscordID).size();
        if (numTransferred > 0 && !wasBotAbused) {
            fileHandler.saveDatabase();
            return ":white_check_mark: **Successfully Transferred " + numTransferred + " Records from " +
                    "<@" + oldDiscordID + "> to <@" + newDiscordID + ">" +
                    "\nThe Old Discord ID Was Not Bot Abused**";
        }
        // If we had records transferred and they were Bot Abused
        else if (numTransferred > 0) {
            fileHandler.saveDatabase();
            return ":white_check_mark: **Successfully Transferred " + numTransferred + " Records from " +
                    "<@" + oldDiscordID + "> to <@" + newDiscordID + ">" +
                    "\nThe Old Discord ID Was Bot Abused at the Time and the Role was Transferred Over**";
        }
        // If we had No Records Transferred
        else {
            return ":warning: No Records Transferred";
        }
    }
    int clearRecords (long targetDiscordID) throws IOException { // For Handling Clearing all records of a Discord ID - Returns the Number of Records Cleared
        int clearedRecords = 0;
        // If we want to clear the records of a Discord ID then we go through the discordID array and remove the elements in all the corresponding arrays.
        while (!getAllRecordsByID(targetDiscordID).isEmpty()) {
            records.remove(getLastRecord(targetDiscordID));
            clearedRecords++;
        }
        log.info("Successfully Cleared Records for " + targetDiscordID);
        fileHandler.saveDatabase();
        return clearedRecords;
    }
    String seeHistory(long targetDiscordID, double timeOffset, boolean isTeamMember) {
        int index = 0;
        String output = "**" + mainConfig.commandPrefix + "checkhistory Results";
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
        String trueOffset = this.getTimeZoneString(timeOffset);
        if (trueOffset != null) {
            sdf.setTimeZone(TimeZone.getTimeZone(trueOffset));
        }
        List<BotAbuseRecord> records = getAllRecordsByID(targetDiscordID);
        int recordsCount = records.size();
        // We check the discordID array and then get all the elements in the corresponding index of the other arrays
        while (index < records.size()) {
            boolean recordIsPermanent = records.get(index).getExpiryDate() == null;
            dateIssued.setTime(records.get(index).getIssuedDate());
            if (!recordIsPermanent) {
                dateToExpire.setTime(records.get(index).getExpiryDate());
            }
            if (!isTeamMember && !recordIsPermanent) {
                output = output.concat(
                        "\n\nBot Abuse ID: **" + records.get(index).getId()
                                + "\n**Offense Number: **" + records.get(index).getRepOffenses()
                                + "\n**Date Issued: **" + sdf.format(dateIssued.getTime())
                                + "\n**Date Expired: **" + sdf.format(dateToExpire.getTime())
                                + "\n**Reason: **" + records.get(index).getReason()
                                + "\n**Proof Image: **" + records.get(index).getProofImage() + "**");
            }
            else if (isTeamMember && !recordIsPermanent) {
                output = output.concat(
                        "\n\nBot Abuse ID: **" + records.get(index).getId()
                                + "\n**Offense Number: **" + records.get(index).getRepOffenses()
                                + "\n**Issuing Team Member: <@" + records.get(index).getIssuingTeamMember() + ">"
                                + "\nDate Issued: **" + sdf.format(dateIssued.getTime())
                                + "\n**Date Expired: **" + sdf.format(dateToExpire.getTime())
                                + "\n**Reason: **" + records.get(index).getReason()
                                + "\n**Proof Image: **" + records.get(index).getProofImage() + "**");
            }
            else if (!isTeamMember) {
                output = output.concat(
                        "\n\nBot Abuse ID: **" + records.get(index).getId()
                                + "\n**Offense Number: **" + records.get(index).getRepOffenses()
                                + "\n**Date Issued: **" + sdf.format(dateIssued.getTime())
                                + "\n**Expiry Date: **Never"
                                + "\n**Reason: **" + records.get(index).getReason()
                                + "\n**Proof Image: **" + records.get(index).getProofImage() + "**");
            }
            else {
                output = output.concat(
                        "\n\nBot Abuse ID: **" + records.get(index).getId()
                                + "\n**Offense Number: **" + records.get(index).getRepOffenses()
                                + "\n**Issuing Team Member: <@" + records.get(index).getIssuingTeamMember() + ">"
                                + "\nDate Issued: **" + sdf.format(dateIssued.getTime())
                                + "\n**Expiry Date: **Never"
                                + "\n**Reason: **" + records.get(index).getReason()
                                + "\n**Proof Image: **" + records.get(index).getProofImage() + "**");
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
            output = output.concat("\n\nRecords Count: **" + recordsCount
            + "**\nHot Records Count: **" + getHotOffenses(targetDiscordID) + "**");
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
            returnValue = "**Successfully Mapped the reason *" + keyReasonWild + "* to the reason key *" + newKey + "***";
        }
        else {
            reasonsDictionary.put(newKey, reasonsDictionary.get(keyReasonWild));
            returnValue = "**Successfully Mapped the reason *" + reasonsDictionary.get(keyReasonWild) +
                    "* to the reason key *" + newKey + "***";
        }
        fileHandler.saveDatabase();
        return returnValue;
    }
    String deleteReason(String key) throws IOException {
        String returnValue;
        String removedReason = reasonsDictionary.remove(key);
        if (removedReason != null) {
            returnValue = "**:white_check_mark: Successfully Removed the reason *" + removedReason
                    + "* which was mapped to key *" + key + "***";
            log.info("Successfully Removed the Reason \"" + removedReason + "\" which was mapped to key \"" + key + "\"");
            fileHandler.saveDatabase();
        }
        else {
            returnValue = "**:x: No Reason Existed under key *" + key + "***";
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
    String getTimeZoneString(double timeOffset) {
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
    boolean timingsAreValid() {
        if (botConfig.getBotAbuseTimes().get(botConfig.getBotAbuseTimes().size() - 1) <= (botConfig.hotOffenseMonths * 30) / 2) {
            int index = 0;
            if (botConfig.getHotOffenseWarning() > botConfig.getBotAbuseTimes().size()) return false;
            while (index < botConfig.getBotAbuseTimes().size()) {
                if (botConfig.getMaxDaysAllowedForUndo() > botConfig.getBotAbuseTimes().get(index++)) return false;
            }
            index = 1;
            int dayTotal = 0;
            while (index < botConfig.getBotAbuseTimes().size()) {
                if (botConfig.getBotAbuseTimes().get(index) <= botConfig.getBotAbuseTimes().get(index - 1)) {
                    botConfig.getBotAbuseTimes().sort(Comparator.naturalOrder());
                    index = 1;
                    dayTotal = 0;
                }
                else if (dayTotal > botConfig.getHotOffenseMonths() * 30) return false;
                else {
                    if (index == 1) dayTotal = dayTotal + botConfig.getBotAbuseTimes().get(0);
                    dayTotal = dayTotal + botConfig.getBotAbuseTimes().get(index++);
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
    BotAbuseRecord getLastRecord(long targetDiscordID) {
        int index = records.size() - 1;

        while (index >= 0) {
            try {
                if (records.get(index).getDiscordID() == targetDiscordID) return records.get(index);
                index--;
            }
            catch (ArrayIndexOutOfBoundsException ex) {
                break;
            }
        }

        return null;
    }
    List<BotAbuseRecord> getAllRecordsByID(long targetDiscordID) {
        List<BotAbuseRecord> recordByID = new ArrayList<>();

        records.forEach(r -> {
            if (r.getDiscordID() == targetDiscordID) recordByID.add(r);
        });

        return recordByID;
    }
    BotAbuseRecord getLastRecordByTeamMember(long teamMemberID) {
        int index = records.size() - 1;

        do {
            if (records.get(index).getIssuingTeamMember() == teamMemberID) return records.get(index);
            index--;
        } while (index >= 0);

        return null;
    }
    List<BotAbuseRecord> getAllRecordsIssuedByTeamMember(long teamMemberId) {
        List<BotAbuseRecord> recordByTeamMember = new ArrayList<>();

        records.forEach(r -> {
            if (r.getIssuingTeamMember() == teamMemberId) recordByTeamMember.add(r);
        });

        return recordByTeamMember;
    }
    void setRecords(List<BotAbuseRecord> records) {
        this.records = records;
    }
    List<BotAbuseRecord> getRecords() {
        return records;
    }
}