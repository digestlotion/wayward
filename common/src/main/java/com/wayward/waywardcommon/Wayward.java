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
import net.minecraft.server.MinecraftServer;

public final class Wayward {

    public static final String MOD_ID = "wayward";

    public static String url = "http://localhost:8080";
    public static String ownerUUID = "";
    public static String token = "";

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    public static void init() {
        auth();
    }

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
            if (ownerUUID.isEmpty()) {
                ownerUUID = session.getUuid();
            }
        } catch (Exception e) {}
    }

    public static void onServerStart(MinecraftServer server) {
        new Thread(() -> pull(server)).start();
    }

    public static void onServerStop(MinecraftServer server) {
        new Thread(() -> push(server)).start();
    }

    private static Path getWorldPath(MinecraftServer server) {
        return server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).getParent();
    }

    private static String getWorldName(MinecraftServer server) {
        return getWorldPath(server).getFileName().toString().replace(" ", "%20");
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

    private static void pull(MinecraftServer server) {
        try {
            Path worldPath = getWorldPath(server);
            String worldName = getWorldName(server);
            Map<String, String> manifest = buildManifest(worldPath);

            HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(String.format("%s/manifest/%s/%s", url, ownerUUID, worldName)))
                    .header("Authorization", token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(manifest)))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) return;

            List<String> diff = GSON.fromJson(response.body(), new TypeToken<List<String>>() {}.getType());
            for (String rel : diff) {
                HttpResponse<byte[]> fileResponse = HTTP.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(String.format("%s/files/%s/%s/%s", url, ownerUUID, worldName, rel)))
                        .header("Authorization", token)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
                );
                if (fileResponse.statusCode() != 200) continue;
                Path target = worldPath.resolve(rel);
                Files.createDirectories(target.getParent());
                Files.write(target, fileResponse.body());
            }
        } catch (Exception e) {}
    }

    private static void push(MinecraftServer server) {
        try {
            Path worldPath = getWorldPath(server);
            String worldName = getWorldName(server);
            Map<String, String> manifest = buildManifest(worldPath);

            HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(String.format("%s/manifest/%s/%s", url, ownerUUID, worldName)))
                    .header("Authorization", token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(manifest)))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) return;

            List<String> diff = GSON.fromJson(response.body(), new TypeToken<List<String>>() {}.getType());
            for (String rel : diff) {
                Path file = worldPath.resolve(rel);
                if (!Files.exists(file)) continue;
                HTTP.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(String.format("%s/files/%s/%s/%s", url, ownerUUID, worldName, rel)))
                        .header("Authorization", token)
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(file)))
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                );
            }
        } catch (Exception e) {}
    }
}
