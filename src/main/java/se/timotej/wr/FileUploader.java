package se.timotej.wr;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class FileUploader {
    public void upload(File file, String sektion, String datum) throws IOException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://timotej.se/wr/live/upload.php"))
                    .header("X-Version", "1.2.0")
                    .header("Content-Type", "text/html")
                    .header("X-Sektion", sektion)
                    .header("X-Datum", datum)
                    .POST(HttpRequest.BodyPublishers.ofFile(file.toPath()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("response.statusCode() = " + response.statusCode());
            System.out.println("response.body() = " + response.body());
            if (response.statusCode() != 200 || response.body().toLowerCase().contains("error")) {
                throw new IOException("Upload failed with status code: " + response.statusCode() + "\n" + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Upload interrupted", e);
        }
    }
}
