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

    private final String sectionName;
    private final String backupLocation;
    private final int beginningSubstring;

    public FileGarbageTruck(String sectionName, String backupLocation, int beginningSubstring) {
        this.sectionName = sectionName;
        this.backupLocation = backupLocation;
        this.beginningSubstring = beginningSubstring;
    }

    public void dumpOldFiles() {
        ZonedDateTime currentTime = ZonedDateTime.now(ZoneId.systemDefault());
        if (fileDumpLastRan.getDayOfYear() < currentTime.getDayOfYear() || fileDumpLastRan.getYear() < currentTime.getYear()) {
            // Cast Directory To Array
            File[] files = new File(backupLocation).listFiles();
            List<File> fileArray = Arrays.asList(files);


            // Read off the names and delete those older than 30 days
            int index = 0;
            int filesDumped = 0;
            int dumpExceptions = 0;

            do {
                File file = fileArray.get(index++);

                char[] charArray = file.getName().toCharArray();
                int charIndex = 0;
                while (charIndex < charArray.length) {
                    if (charArray[charIndex] == '.') {
                        break;
                    }
                    else charIndex++;
                }
                // Translate the File Names into Date and Time
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd H m s z yyyy");
                ZonedDateTime fileWriteTime = ZonedDateTime.parse(file.getName().substring(beginningSubstring, charIndex), formatter);

                if (fileWriteTime.isBefore(currentTime.minusDays(30))) {
                    try {
                        Files.delete(Paths.get(file.getAbsolutePath()));
                        filesDumped++;
                    }
                    catch (IOException e) {
                        aue.logCaughtException(Thread.currentThread(), e);
                        dumpExceptions++;
                    }
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

            fileDumpLastRan = currentTime;
        }
    }
}
