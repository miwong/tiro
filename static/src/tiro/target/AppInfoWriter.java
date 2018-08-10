package tiro.target;

import tiro.*;
import tiro.target.event.Event;
import tiro.target.event.EventChain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

class AppInfoWriter {
    private final ManifestAnalysis _manifestAnalysis;
    private JsonObject _eventChainsJson = new JsonObject();
    private Object _eventChainJsonLock = new Object();
    private Timer _writerTimer;

    private class WriterTask extends TimerTask {
        @Override
        public void run() {
            AppInfoWriter.this.writeIntermediateFile();
        }
    }

    public AppInfoWriter(ManifestAnalysis manifestAnalysis) {
        _manifestAnalysis = manifestAnalysis;

        _writerTimer = new Timer("AppInfoWriterThread");
        _writerTimer.schedule(new WriterTask(), 600000, 600000);
    }

    public void writeFinalFile() {
        // Cancel the timer task
        _writerTimer.cancel();
        _writerTimer.purge();

        // Write final file to the temp file first, in case we get timed out while writing
        writeIntermediateFile();
    }

    public void addEventChain(EventChain eventChain) {
        JsonObject eventChainJson = eventChain.toJson();

        synchronized (_eventChainJsonLock) {
            _eventChainsJson.add(Integer.toString(eventChain.getId()), eventChainJson);
        }
    }

    public void writeIntermediateFile() {
        String tmpFilePath = TIROStaticAnalysis.Config.OutputDirectory
                + "/appInfo.json.tmp";
        writeToFile(tmpFilePath);

        String filePath = TIROStaticAnalysis.Config.OutputDirectory + "/appInfo.json";

        try {
            Files.move(Paths.get(tmpFilePath), Paths.get(filePath),
                       StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            e.printStackTrace();
            writeToFile(filePath);
        }
    }

    //public void writeToFile() {
    //    writeToFile(TIROStaticAnalysis.Config.OutputDirectory + "/appInfo.json");
    //}

    public void writeToFile(String filePath) {
        synchronized (_eventChainJsonLock) {
            JsonObject appInfoJson = new JsonObject();

            appInfoJson.addProperty("Version", TIROStaticAnalysis.Config.Version);
            appInfoJson.addProperty("Generated", (new Date()).toString());

            appInfoJson.addProperty("Package", _manifestAnalysis.getPackageName());
            appInfoJson.addProperty("MainActivity", _manifestAnalysis.getMainActivity());

            appInfoJson.add("EventChains", _eventChainsJson);

            try {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                PrintWriter appInfoWriter = new PrintWriter(filePath, "UTF-8");
                appInfoWriter.print(gson.toJson(appInfoJson));
                appInfoWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
