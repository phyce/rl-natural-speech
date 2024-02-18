package dev.phyce.naturalspeech.tts;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class DownloadManager {
    private static DownloadManager instance;
    private final String fileURL = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/libritts/high/en_US-libritts-high.onnx?download=true";
    private final String settingsFileURL = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/libritts/high/en_US-libritts-high.onnx.json?download=true";
    private final Path downloadPath;
    private static volatile float fileProgress = 0.0F; // Initialize the download progress to 0

    public static float getFileProgress() {
        return fileProgress;
    }
    public Path getDownloadPath() {return downloadPath;}

    // Method to initiate file download
    public void downloadFile(String url, Path path) throws IOException, InterruptedException {

        // Configure HttpClient to follow redirects
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET() // HTTP GET method
                .build();

        downloadWithProgress(client, request, path);
    }

    // Shared method to download a file and update progress
    private void downloadWithProgress(HttpClient client, HttpRequest request, Path directoryPath) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = client.send(request, BodyHandlers.ofInputStream());
        if (response.statusCode() == 200) {
            // Extract filename
            String filename = getFilenameFromContentDisposition(response.headers().firstValue("content-disposition").orElse(""));
            if (filename.isEmpty()) {
                // Fallback to extracting filename from URL if not found in the header
                filename = request.uri().getPath();
                filename = filename.substring(filename.lastIndexOf('/') + 1);
            }

            // Ensure filename is not empty or invalid
            if (filename.isEmpty()) {
                throw new IOException("Failed to determine filename for download");
            }

            // Append the filename to the directory path
            Path filePath = directoryPath.resolve(filename);

            // Rest of your download code...
            long totalBytes = response.headers().firstValueAsLong("content-length").orElse(-1L);
            //System.out.println("Starting download... Total file size: " + totalBytes + " bytes");

            try (InputStream is = response.body()) {
                Files.createDirectories(directoryPath); // Ensure the directory path exists
                try (OutputStream os = Files.newOutputStream(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                    copyStream(is, os, totalBytes); // Method to copy stream with progress
                }
            }
            //System.out.println("Download completed: " + filePath);
        } else {
            System.err.println("Download failed with HTTP status code: " + response.statusCode());
            // Reset progress if download fails
        }
    }

    private String getFilenameFromContentDisposition(String contentDisposition) {
        String filename = "";
        if (contentDisposition != null && !contentDisposition.isEmpty()) {
            for (String part : contentDisposition.split(";")) {
                if (part.trim().startsWith("filename")) {
                    filename = part.substring(part.indexOf('=') + 1).trim().replace("\"", "");
                    break; // Found the filename, exit the loop
                }
            }
        }
        // Remove any potential UTF-8'' prefix
        if (filename.startsWith("UTF-8''")) {
            filename = filename.substring(7);
        }
        // URL decode the filename
        try {
            filename = URLDecoder.decode(filename, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            System.err.println("Error decoding filename: " + e.getMessage());
            return ""; // Return an empty filename in case of error
        }
        // Further processing to extract the actual filename if necessary
        // For example, if the filename contains path information that you want to strip
        String[] parts = filename.split("/");
        if (parts.length > 0) {
            filename = parts[parts.length - 1]; // Take the last part as the filename
        }
        return filename;
    }
    // Method to copy stream with progress update
    private void copyStream(InputStream source, OutputStream target, long totalBytes) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        long totalRead = 0;

        while ((len = source.read(buf)) > 0) {
            target.write(buf, 0, len);
            totalRead += len;
            fileProgress = totalBytes > 0 ? (float)totalRead / totalBytes * 100 : -1;

        }
        target.flush();
        fileProgress = 100.0F;
    }













    private DownloadManager(String directoryPath) {
        this.downloadPath = Paths.get(directoryPath);
        if (Files.exists(downloadPath.resolve("en_US-libritts-high.onnx"))) fileProgress = 100f;
    }

    // Synchronized method to ensure thread safety
    public static synchronized DownloadManager getInstance(String directoryPath) {
        if (instance == null) {
            // Check if directoryPath is provided; if not, return null or throw an exception
            if (directoryPath == null || directoryPath.isEmpty()) {
                System.out.println("Directory path must be provided for the first initialization.");
                return null; // or throw new IllegalArgumentException("Directory path must be provided for the first initialization.");
            }
            instance = new DownloadManager(directoryPath);
        }
        return instance;
    }

    // Overload getInstance without parameters for subsequent calls
    public static DownloadManager getInstance() {
        return getInstance(""); // Passing an empty string to reuse the existing method logic
    }

    public void checkAndDownload() {
        //System.out.println("Checking file at path: " + downloadPath.toAbsolutePath().resolve("en_US-libritts-high.onnx").toString()); // Print the full absolute path
        if (!Files.exists(downloadPath.toAbsolutePath().resolve("en_US-libritts-high.onnx"))) {
            try {
                //System.out.println("File not found. Starting download...");
                downloadFile(settingsFileURL, downloadPath);
                downloadFile(fileURL, downloadPath);
            } catch (IOException | InterruptedException e) {
                System.err.println("Error downloading the file: " + e.getMessage());
                fileProgress = 0.0F; // Reset progress if download fails
            }
        } else {
            //System.out.println("File already exists. No need to download.");
            fileProgress = 100.0F; // File is fully downloaded
        }
    }


    public static float getFileReadyPercentage() {
        return fileProgress;
    }

//    public static void main(String[] args) {
//        String directoryPath = "./"; // Use the current working directory
//        DownloadManager manager = DownloadManager.getInstance(directoryPath);
//        manager.checkAndDownload();
//        if (DownloadManager.getFileReadyPercentage() == 100.0F) {
//            System.out.println("The file is fully downloaded and ready for use.");
//        } else {
//            System.out.println("The file download progress is at " + DownloadManager.getFileReadyPercentage() + "%.");
//        }
//    }
}