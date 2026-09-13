package com.wayward.waywardcommon;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;

public class WaywardService {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    public static URI url = URI.create("http://localhost:8080");
    public static String token = "";

    public static void auth() {
        MinecraftClient client = MinecraftClient.getInstance();
        Session session = client.getSession();

        String serverId = UUID.randomUUID().toString();
        try {
            client.getSessionService().joinServer(session.getProfile(), session.getAccessToken(), serverId);
            HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(String.format("%s/auth?username=%s&serverId=%s", url, session.getUsername(), serverId)))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) return;
            token = JsonParser.parseString(response.body()).getAsJsonObject().get("token").getAsString();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void pull(Path path, String uuid) {
        try {
            for (String rel : getDiff(path, uuid)) {
                HttpResponse<byte[]> fileResponse = HTTP.send(
                    HttpRequest.newBuilder()
                        .uri(
                            new URI(url.getScheme(), url.getAuthority(), String.format("/files/%s/%s/%s", uuid, path.getFileName(), rel), null, null)
                        )
                        .header("Authorization", token)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
                );
                if (fileResponse.statusCode() != 200) continue;
                Path target = path.resolve(rel);
                Files.createDirectories(target.getParent());
                Files.write(target, fileResponse.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void push(Path path, String uuid) {
        try {
            for (String rel : getDiff(path, uuid)) {
                Path file = path.resolve(rel);
                if (!Files.exists(file)) continue;
                HTTP.send(
                    HttpRequest.newBuilder()
                        .uri(
                            new URI(url.getScheme(), url.getAuthority(), String.format("/files/%s/%s/%s", uuid, path.getFileName(), rel), null, null)
                        )
                        .header("Authorization", token)
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(file)))
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, String> buildManifest(Path worldPath) throws Exception {
        Map<String, String> manifest = new HashMap<>();
        Files.walk(worldPath)
            .filter(Files::isRegularFile)
            .forEach(file -> {
                try {
                    String rel = worldPath.relativize(file).toString().replace("\\", "/");
                    manifest.put(rel, hashFile(file));
                } catch (Exception e) {}
            });
        return manifest;
    }

    private static String hashFile(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(file));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static List<String> getDiff(Path path, String uuid) throws Exception {
        Map<String, String> manifest = buildManifest(path);

        HttpResponse<String> response = HTTP.send(
            HttpRequest.newBuilder()
                .uri(new URI(url.getScheme(), url.getAuthority(), String.format("/manifest/%s/%s", uuid, path.getFileName()), null, null))
                .header("Authorization", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(manifest)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) return List.of();

        return GSON.fromJson(response.body(), new TypeToken<List<String>>() {}.getType());
    }
}
