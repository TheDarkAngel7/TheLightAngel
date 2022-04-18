package Angel;

import javax.annotation.Nullable;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class FileCreator {
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
                Files.createDirectory(dataFolder);
                everythingExisted = false;
            }
            if (!Files.exists(configFolder)) {
                Files.createDirectory(configFolder);
                exportResource(configFolder.getFileName().toString(), "/config.json");
                exportResource(configFolder.getFileName().toString(),"/botabuseconfig.json");
                exportResource(configFolder.getFileName().toString(),"/nickconfig.json");
                exportResource(configFolder.getFileName().toString(), "/checkinconfig.json");
                everythingExisted = false;
            }
            if (!Files.exists(dbBackupsFolder)) {
                Files.createDirectory(dbBackupsFolder);

                everythingExisted = false;
            }
            if (!Files.exists(Paths.get(dbBackupsFolder + "/Nicknames"))) {
                Files.createDirectory(Paths.get(dbBackupsFolder + "/Nicknames"));
                everythingExisted = false;
            }
            if (!Files.exists(Paths.get(dbBackupsFolder + "/BotAbuse"))) {
                Files.createDirectory(Paths.get(dbBackupsFolder + "/BotAbuse"));
                everythingExisted = false;
            }
            if (!Files.exists(Paths.get(jarDirectory + "/CheckIn"))) {
                Files.createDirectory(Paths.get(dbBackupsFolder + "/CheckIn"));
                everythingExisted = false;
            }
            if (!Files.exists(logFolder)) {
                Files.createDirectory(logFolder);
                everythingExisted = false;
            }
            if (!Files.exists(Paths.get(jarDirectory + "/log4j2.properties"))) {
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

    private void exportResource(@Nullable String subFolder, String resourceName) throws IOException {
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

        System.out.println(jarFolder + resourceName);
    }
}
