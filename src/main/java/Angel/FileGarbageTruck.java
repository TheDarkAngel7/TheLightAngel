package Angel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class FileGarbageTruck implements CommonLogic {
    private final Logger log = LogManager.getLogger(FileGarbageTruck.class);

    private ZonedDateTime fileDumpLastRan = ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());

    // Use Default Date Time Pattern Unless Changed by setFileNamingPattern(String) method
    private String dateTimePattern = "EEE MMM dd H m s z yyyy";

    // Default Storage Days Before Deletion
    private int daysToStoreBeforeDeletion = 30;

    // What if file names do not include time zones?

    private boolean fileNamesIncludeTimeZones = true;

    // Initiatal Variables
    private final String sectionName;
    private final String backupLocation;
    // Beginning Substring should be where the substring index where the date begins.
    private final int beginningSubstring;

    public FileGarbageTruck(String sectionName, String backupLocation, int beginningSubstring) {
        this.sectionName = sectionName;
        this.backupLocation = backupLocation;
        this.beginningSubstring = beginningSubstring;
        log.info(sectionName + " File Garbage Truck Instance Constructed with the default values!");
    }

    public void dumpFiles() {
        ZonedDateTime currentTime = ZonedDateTime.now(ZoneId.systemDefault());
        if (fileDumpLastRan.getDayOfYear() < currentTime.getDayOfYear() || fileDumpLastRan.getYear() < currentTime.getYear()) {
            fileDumpLastRan = currentTime;


            // Cast Directory To Array
            File[] files = new File(backupLocation).listFiles();
            List<File> fileArray = Arrays.asList(files);
            log.debug(sectionName + " Files Successfully Accessed - There are " + fileArray.size() + " files");


            // Read off the names and delete those older than 30 days
            int index = 0;
            int filesDumped = 0;
            int dumpExceptions = 0;

            long epochTimeBegin = currentTime.toEpochSecond();

            do {
                File file = fileArray.get(index++);
                currentTime = ZonedDateTime.now(ZoneId.systemDefault());

                char[] charArray = file.getName().toCharArray();
                int charIndex = 0;
                if (file.isDirectory()) {
                    continue;
                }
                while (charIndex < charArray.length) {
                    if (charArray[charIndex] == '.') {
                        break;
                    }
                    else charIndex++;
                }
                // Translate the File Names into Date and Time
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateTimePattern);

                // If the files do not include a time zone... then use the system default time zone
                if (!fileNamesIncludeTimeZones) {
                    formatter = formatter.withZone(ZoneId.systemDefault());
                }

                ZonedDateTime fileWriteTime = ZonedDateTime.parse(file.getName().substring(beginningSubstring, charIndex), formatter);

                if (fileWriteTime.isBefore(currentTime.minusDays(daysToStoreBeforeDeletion))) {
                    try {
                        Files.delete(Paths.get(file.getAbsolutePath()));
                        filesDumped++;
                    }
                    catch (IOException e) {
                        aue.logCaughtException(Thread.currentThread(), e);
                        dumpExceptions++;
                    }
                }

                // If the process is taking longer than expected then we take start logging debug messages
                if (currentTime.toEpochSecond() >= epochTimeBegin + 5) {
                    log.debug(sectionName + " Files are Being Processed... Currently Processing File " + (index + 1) + " of " + fileArray.size());
                    epochTimeBegin = currentTime.toEpochSecond();
                }
            } while (index < fileArray.size());

            // Logging
            if (filesDumped > 0) {
                String result = sectionName + " File Dump Successfully Completed with " + filesDumped + " files deleted";

                if (dumpExceptions > 0) {
                    result = result.concat(" with " + dumpExceptions + " exception(s)");
                    log.warn(result);
                }
                else {
                    log.info(result);
                }
            }
            else {
                log.info( sectionName + " File Dump Successfully Completed but No Files were deleted");
            }
        }
    }

    public FileGarbageTruck setFileNamingPattern(String pattern) {
        dateTimePattern = pattern;
        log.debug(sectionName + " File Naming Pattern set to " + pattern);
        return this;
    }

    public FileGarbageTruck setDaysToStoreFilesBeforeDeletion(int days) {
        daysToStoreBeforeDeletion = days;
        log.debug(sectionName + " Files Will Not Be Thrown in the Garbage Truck unless they are more than " + days + " days old!") ;
        return this;
    }

    public FileGarbageTruck filesDoNotIncludeTimeZones() {
        fileNamesIncludeTimeZones = false;
        return this;
    }
}
