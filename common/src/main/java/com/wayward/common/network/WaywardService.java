package com.wayward.common.network;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.wayward.common.Config;
import com.wayward.common.Wayward;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public class WaywardService {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();
    private static final List<String> ALLOWED_FILES = List.of(".dat", ".dat_old", ".mca", ".json", ".png");

    public static void auth() {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();

        String serverId = UUID.randomUUID().toString();
        try {
            mc.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
            HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(String.format("%s/auth?username=%s&serverId=%s", URI.create(Config.url), user.getName(), serverId)))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) return;
            Config.token = JsonParser.parseString(response.body()).getAsJsonObject().get("token").getAsString();
            Config.save();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean pull(Path path, String uuid) {
        boolean b = true;
        try {
            for (String rel : getDiff(path, uuid)) {
                HttpResponse<byte[]> fileResponse = HTTP.send(
                    HttpRequest.newBuilder()
                        .uri(
                            new URI(
                                URI.create(Config.url).getScheme(),
                                URI.create(Config.url).getAuthority(),
                                String.format("/files/%s/%s/%s", uuid, path.getFileName(), rel),
                                null,
                                null
                            )
                        )
                        .header("Authorization", Config.token)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
                );
                if (fileResponse.statusCode() != 200) {
                    b = false;
                    continue;
                }

                Path target = path.resolve(rel);
                Files.createDirectories(target.getParent());
                Files.write(target, fileResponse.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return b;
    }

    private static boolean push(Path path, String uuid) {
                boolean b = true;
        try {
            for (String rel : getDiff(path, uuid)) {
                Path file = path.resolve(rel);
                if (!Files.exists(file)) continue;
                HttpResponse<String> fileResponse = HTTP.send(
                    HttpRequest.newBuilder()
                        .uri(
                            new URI(
                                URI.create(Config.url).getScheme(),
                                URI.create(Config.url).getAuthority(),
                                String.format("/files/%s/%s/%s", uuid, path.getFileName(), rel),
                                null,
                                null
                            )
                        )
                        .header("Authorization", Config.token)
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(file)))
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                );
                if (fileResponse.statusCode() != 204) {
                    b = false;
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return b;
    }

    private static Map<String, String> buildManifest(Path worldPath) throws Exception {
        Map<String, String> manifest = new HashMap<>();
        Files.walk(worldPath)
            .filter(Files::isRegularFile)
            .filter(file -> {
                String fn = file.getFileName().toString();
                int dot = fn.lastIndexOf(".");
                return dot != -1 && ALLOWED_FILES.contains(fn.substring(dot));
            })
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
                .uri(
                    new URI(
                        URI.create(Config.url).getScheme(),
                        URI.create(Config.url).getAuthority(),
                        String.format("/manifest/%s/%s", uuid, path.getFileName()),
                        null,
                        null
                    )
                )
                .header("Authorization", Config.token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(manifest)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) return List.of();

        return GSON.fromJson(response.body(), new TypeToken<List<String>>() {}.getType());
    }

    public static void onServerStart(MinecraftServer server) {
        Path p = server.getWorldPath(LevelResource.ROOT).getParent();
        if (Config.worlds == null) return;
        String uuid = Config.worlds.get(p.getFileName().toString());
        if (uuid == null) return;
        pull(p, uuid);
    }

    public static void onServerStop(MinecraftServer server) {
        Path p = server.getWorldPath(LevelResource.ROOT).getParent();
        if (Config.worlds == null) return;
        String uuid = Config.worlds.get(p.getFileName().toString());
        if (uuid == null) return;
        push(p, uuid);
    }

    public static void joinWorld(String uuid, String world) {
        try {
            if (uuid.isEmpty() || world.isEmpty()) return; // TODO: handle this properly
            Path worldPath = Wayward.getSavesDir().resolve(world);
            Files.createDirectories(worldPath);
            if (pull(worldPath, uuid)) {
                Config.worlds.put(world, uuid);
                Config.save();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addWorld(String world) {
        Path worldPath = Wayward.getSavesDir().resolve(world);
        if (Files.exists(worldPath)) {
            String uuid = Minecraft.getInstance().getUser().getProfileId().toString();
            if (push(worldPath, uuid)) {
                Config.worlds.put(world, uuid);
                Config.save();
            }
        }
    }

    public static void invite(String uuid, String world) {
        try {
            URI base = URI.create(Config.url);
            HTTP.send(
                HttpRequest.newBuilder()
                    .uri(new URI(base.getScheme(), base.getAuthority(), "/invite", String.format("uuid=%s&world=%s", uuid, world), null))
                    .header("Authorization", Config.token)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
