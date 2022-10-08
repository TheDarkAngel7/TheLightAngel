package Angel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class FileCreator {
    private final Logger log = LogManager.getLogger(FileCreator.class);
    private final String jarDirectory = new File(FileCreator.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParentFile().getPath().replace('\\', '/');
    private final Path configFolder = Paths.get(jarDirectory + "/configs");
    private final Path dataFolder = Paths.get(jarDirectory + "/data");
    private final Path dbBackupsFolder = Paths.get(jarDirectory + "/db-backups");
    private final Path logFolder = Paths.get(jarDirectory + "/logs");

    FileCreator() throws URISyntaxException {
    }

    boolean startup() {
        boolean everythingExisted = true;
        try {
            if (!Files.exists(dataFolder)) {
                log.warn("Data Folder Did Not Exist! Creating New...");
                Files.createDirectory(dataFolder);
                everythingExisted = false;

            }
            if (!Files.exists(configFolder)) {
                log.warn("Config Folder Did Not Exist! Creating New...");
                Files.createDirectory(configFolder);
                exportResource(configFolder.getFileName().toString(), "/config.json");
                exportResource(configFolder.getFileName().toString(),"/botabuseconfig.json");
                exportResource(configFolder.getFileName().toString(),"/nickconfig.json");
                exportResource(configFolder.getFileName().toString(), "/checkinconfig.json");
                everythingExisted = false;
            }
            if (!Files.exists(dbBackupsFolder)) {
                log.warn("Database Backups Folder Did Not Exist! Creating New...");
                Files.createDirectory(dbBackupsFolder);

                everythingExisted = false;
            }
            if (!Files.exists(Paths.get(dbBackupsFolder + "/Nicknames"))) {
                log.warn("Nicknames Folder in Database Backups Folder Did Not Exist! Creating New...");
                Files.createDirectory(Paths.get(dbBackupsFolder + "/Nicknames"));
                everythingExisted = false;
            }
            if (!Files.exists(Paths.get(dbBackupsFolder + "/BotAbuse"))) {
                log.warn("Bot Abuse Folder in Database Backups Folder Did Not Exist! Creating New...");
                Files.createDirectory(Paths.get(dbBackupsFolder + "/BotAbuse"));
                everythingExisted = false;
            }
            if (!Files.exists(Paths.get(dbBackupsFolder + "/CheckIn"))) {
                log.warn("Check-In Folder in Database Backups Folder Did Not Exist! Creating New...");
                Files.createDirectory(Paths.get(dbBackupsFolder + "/CheckIn"));
                everythingExisted = false;
            }
            if (!Files.exists(logFolder)) {
                log.warn("Log Folder Did Not Exist! Creating New...");
                Files.createDirectory(logFolder);
                everythingExisted = false;
            }
            if (!Files.exists(Paths.get(jarDirectory + "/log4j2.properties"))) {
                log.warn("Log4J2 Config Not Found, Exporting Default...");
                exportResource(null, "/log4j2.properties");
                everythingExisted = false;
            }
            return everythingExisted;
        }
        catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private void exportResource(String subFolder, String resourceName) throws IOException {
        InputStream stream;
        OutputStream resStreamOut;
        String jarFolder = jarDirectory;
        try {
            stream = FileCreator.class.getResourceAsStream(resourceName);//note that each / is a directory down in the "jar tree" been the jar the root of the tree
            if (stream == null) {
                throw new IOException("Cannot get resource \"" + resourceName + "\" from Jar file.");
            }

            int readBytes;
            byte[] buffer = new byte[4096];
            if (subFolder == null) {
                resStreamOut = new FileOutputStream(jarFolder + resourceName);
            }
            else {
                resStreamOut = new FileOutputStream(jarFolder + "/" + subFolder + "/" + resourceName);
            }
            while ((readBytes = stream.read(buffer)) > 0) {
                resStreamOut.write(buffer, 0, readBytes);
            }
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
