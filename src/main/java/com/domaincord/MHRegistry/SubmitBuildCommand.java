package com.domaincord.MHRegistry;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class SubmitBuildCommand implements CommandExecutor {
    private final MHRegistry plugin;

    public SubmitBuildCommand(MHRegistry plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        if (!sender.hasPermission("mhregistry.submit")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage:-Yearly /submitbuild <buildName> <regionName>");
            return true;
        }

        String buildName = args[0];
        String regionName = args[1];
        Player player = (Player) sender;
        FileConfiguration config = plugin.getPluginConfig();
        String apiUrl = config.getString("api.url");
        String apiKey = config.getString("api.key");
        String serverName = config.getString("server.name", "UnknownServer");

        if (apiUrl == null || apiKey == null) {
            sender.sendMessage("§cPlugin configuration is invalid! Please contact an administrator.");
            return true;
        }

        // Validate API URL
        try {
            URI.create(apiUrl).toURL();
        } catch (Exception e) {
            sender.sendMessage("§cInvalid API URL in configuration!");
            return true;
        }

        sender.sendMessage("§eProcessing build '" + buildName + "' for region '" + regionName + "'...");

        // Run async to prevent blocking
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Initialize block data map
                    Map<String, Integer> blockData = new HashMap<>();
                    boolean regionValid = false;
                    int[] minCorner = new int[3];
                    int[] maxCorner = new int[3];

                    // Check for WorldGuard and process region
                    if (plugin.isWorldGuardEnabled()) {
                        World world = player.getWorld();

                        // Convert Bukkit World to WorldEdit World
                        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

                        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
                        if (regionManager != null) {
                            ProtectedRegion region = regionManager.getRegion(regionName);
                            if (region != null) {
                                regionValid = true;

                                // Get region bounds
                                BlockVector3 minVector = region.getMinimumPoint();
                                Location min = new Location(world, minVector.x(), minVector.y(), minVector.z());

                                BlockVector3 maxVector = region.getMaximumPoint();
                                Location max = new Location(world, maxVector.x(), maxVector.y(), maxVector.z());

                                // Store corners as integers
                                minCorner = new int[]{min.getBlockX(), min.getBlockY(), min.getBlockZ()};
                                maxCorner = new int[]{max.getBlockX(), max.getBlockY(), max.getBlockZ()};

                                // Count block types (skip air blocks)
                                for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                                    for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                                        for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                                            Block block = world.getBlockAt(x, y, z);
                                            String blockType = block.getType().name();
                                            if (!blockType.equals("AIR")) { // Skip air blocks
                                                blockData.put(blockType, blockData.getOrDefault(blockType, 0) + 1);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!regionValid && plugin.isWorldGuardEnabled()) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                sender.sendMessage("§cRegion '" + regionName + "' not found!");
                            }
                        }.runTask(plugin);
                        return;
                    }

                    // Prepare block data JSON
                    StringBuilder blockDataJson = new StringBuilder("{");
                    for (Map.Entry<String, Integer> entry : blockData.entrySet()) {
                        if (blockDataJson.length() > 1) blockDataJson.append(",");
                        blockDataJson.append(String.format("\"%s\":%d", entry.getKey(), entry.getValue()));
                    }
                    blockDataJson.append("}");

                    // Get server IP, port, and version
                    String serverIp = plugin.getServer().getIp().isEmpty() ? "localhost" : plugin.getServer().getIp();
                    String serverPort = String.valueOf(plugin.getServer().getPort() != 0 ? plugin.getServer().getPort() : 25565);
                    String serverVersion = org.bukkit.Bukkit.getMinecraftVersion(); // e.g., "1.20.4"

                    // Get player data
                    String playerUsername = player.getName();
                    String playerUuid = player.getUniqueId().toString();

                    // Get player location (integers)
                    Location playerLoc = player.getLocation();
                    int locX = playerLoc.getBlockX();
                    int locY = playerLoc.getBlockY();
                    int locZ = playerLoc.getBlockZ();

                    URL url = URI.create(apiUrl).toURL();
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    // conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                    conn.setDoOutput(true);

                    String jsonInputString = String.format(
                            "{\"buildName\":\"%s\",\"player_username\":\"%s\",\"player_uuid\":\"%s\",\"server\":{\"ip\":\"%s\",\"port\":\"%s\",\"name\":\"%s\",\"version\":\"%s\"},\"region\":{\"name\":\"%s\",\"corners\":[[%d,%d,%d],[%d,%d,%d]]},\"tp_location\":{\"x\":%d,\"y\":%d,\"z\":%d},\"blocks\":%s}",
                            buildName, playerUsername, playerUuid,
                            serverIp, serverPort, serverName, serverVersion,
                            regionName, minCorner[0], minCorner[1], minCorner[2], maxCorner[0], maxCorner[1], maxCorner[2],
                            locX, locY, locZ,
                            blockDataJson
                    );

                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    int responseCode = conn.getResponseCode();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (responseCode == 200) {
                                sender.sendMessage("§aBuild submitted successfully with block data!");
                            } else {
                                sender.sendMessage("§cFailed to submit build. Server returned status: " + responseCode);
                            }
                        }
                    }.runTask(plugin);

                    conn.disconnect();

                } catch (Exception e) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage("§cError submitting build: " + e.getMessage());
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }
}