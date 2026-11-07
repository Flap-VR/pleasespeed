package dev.troll;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sends, removes and tracks the troll resource pack (TrollPack.zip). */
public final class PackService {

    private final TrollPlugin plugin;
    private final UUID packId = UUID.nameUUIDFromBytes("troll-resource-pack".getBytes(StandardCharsets.UTF_8));
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    private volatile String url = "";
    private volatile String hashHex = null;

    public PackService(TrollPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public UUID id() {
        return packId;
    }

    public boolean configured() {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /** True when the pack has a URL and a known SHA-1, so it can actually be sent. */
    public boolean ready() {
        return configured() && hashHex != null;
    }

    public boolean isLoaded(Player p) {
        return loaded.contains(p.getUniqueId());
    }

    public void markLoaded(UUID id) {
        loaded.add(id);
    }

    public void markUnloaded(UUID id) {
        loaded.remove(id);
    }

    /** (Re)reads the config. If no SHA-1 is configured, downloads the pack once to work it out. */
    public void init() {
        url = cfg().getString("resource-pack.url", "").trim();
        String sha = cfg().getString("resource-pack.sha1", "").trim().toLowerCase(Locale.ROOT);

        if (!configured()) {
            hashHex = null;
            plugin.getLogger().info("Resource pack URL isn't set yet, so the jumpscare image and sounds won't work until it is (see README).");
            return;
        }
        if (sha.matches("[0-9a-f]{40}")) {
            hashHex = sha;
            return;
        }

        hashHex = null;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();
                HttpResponse<InputStream> response = client.send(
                        HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    plugin.getLogger().warning("Couldn't download the resource pack to work out its SHA-1 (HTTP " + response.statusCode() + ").");
                    return;
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                try (InputStream in = response.body()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
                }
                hashHex = HexFormat.of().formatHex(digest.digest());
                plugin.getLogger().info("Resource pack SHA-1 worked out: " + hashHex);
            } catch (Exception e) {
                plugin.getLogger().warning("Couldn't work out the resource pack SHA-1: " + e.getMessage());
            }
        });
    }

    public boolean send(Player player, boolean force) {
        if (!ready()) return false;
        Component prompt = MiniMessage.miniMessage().deserialize(cfg().getString("resource-pack.prompt", ""));
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId, URI.create(url), hashHex);
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .prompt(prompt)
                .required(force)
                .replace(false)
                .build());
        return true;
    }

    public void remove(Player player) {
        player.removeResourcePacks(packId);
        loaded.remove(player.getUniqueId());
    }
}
