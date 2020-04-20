import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.util.ArrayList;
import java.util.Date;

class FileHandler {
    private File dataFile = new File("data/data.dat");
    private File tempDataFile = new File("data/datatemp.dat");
    private FileInputStream inputFile = new FileInputStream(dataFile);
    private ObjectInputStream inputStream = new ObjectInputStream(inputFile);

    FileHandler() throws IOException {
    }

    JsonObject getConfig() throws FileNotFoundException {
        JsonElement element = JsonParser.parseReader(new FileReader("config.json"));
        return element.getAsJsonObject();
    }
    ArrayList<Long> getDiscordIDs() throws Exception {
        return (ArrayList<Long>) inputStream.readObject();
    }
    ArrayList<String> getTeamMembers() throws Exception {
        return (ArrayList<String>) inputStream.readObject();
    }
    ArrayList<Integer> getRepOffenses() throws Exception {
        return (ArrayList<Integer>) inputStream.readObject();
    }
    ArrayList<Date> getIssuedDates() throws Exception {
        return (ArrayList<Date>) inputStream.readObject();
    }
    ArrayList<Date> getExpiryDates() throws Exception {
        return (ArrayList<Date>) inputStream.readObject();
    }
    ArrayList<String> getReasons() throws Exception {
        return (ArrayList<String>) inputStream.readObject();
    }
    ArrayList<String> getProofImages() throws Exception {
        return (ArrayList<String>) inputStream.readObject();
    }
    ArrayList<Long> getCurrentBotAbusers() throws Exception {
        return (ArrayList<Long>) inputStream.readObject();
    }

    void writeArrayData(ArrayList<Long> discordID, ArrayList<String> teamMembers, ArrayList<Integer> repOffenses, ArrayList<Date> issuedDates,
                        ArrayList<Date> expiryDates, ArrayList<String> reasons, ArrayList<String> proofImages,
                        ArrayList<Long> currentBotAbusers)
            throws Exception {
        this.inputStream.close();
        this.inputFile.close();
        // Open Output Stream
        ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(tempDataFile));
        // Write the Objects
        outputStream.writeObject(discordID);
        outputStream.writeObject(teamMembers);
        outputStream.writeObject(repOffenses);
        outputStream.writeObject(issuedDates);
        outputStream.writeObject(expiryDates);
        outputStream.writeObject(reasons);
        outputStream.writeObject(proofImages);
        outputStream.writeObject(currentBotAbusers);

        // Close
        outputStream.flush();
        outputStream.close();
        if (dataFile.delete()) {
            System.out.println("[System] Successfully Deleted Original File");
        }
        else {
            while (dataFile.exists()) {
                System.out.println("Result of Deletion: " + dataFile.delete());
            }
        }

        // Rename the file
        if (tempDataFile.renameTo(dataFile)) {
            System.out.println("[System] Successfully Renamed Temp File to Original File");
        }
        else {
            System.out.println("[System] Couldn't Rename Temp File");
        }

    }
}