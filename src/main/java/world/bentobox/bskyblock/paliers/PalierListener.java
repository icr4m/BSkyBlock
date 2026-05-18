package world.bentobox.bskyblock.paliers;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bskyblock.BSkyBlock;

/**
 * Hooks into the Level addon's IslandLevelCalculatedEvent via reflection,
 * so no compile-time dependency on the Level addon jar is required.
 */
public class PalierListener {

    private PalierListener() {}

    @SuppressWarnings("unchecked")
    public static void register(BSkyBlock addon, PalierManager palierManager, PalierSettings settings) {
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>)
                    Class.forName("world.bentobox.level.events.IslandLevelCalculatedEvent");

            Listener dummy = new Listener() {};

            Bukkit.getPluginManager().registerEvent(
                    eventClass, dummy, EventPriority.NORMAL,
                    (l, event) -> {
                        if (!eventClass.isInstance(event)) return;
                        try {
                            Island island = (Island) event.getClass().getMethod("getIsland").invoke(event);
                            long newLevel = (long) event.getClass().getMethod("getLevel").invoke(event);
                            checkNewClaimable(island, newLevel, palierManager, settings);
                        } catch (Exception e) {
                            addon.logError("PalierListener error: " + e.getMessage());
                        }
                    },
                    addon.getPlugin(),
                    false
            );
            addon.log("Level addon detected - palier auto-notification enabled.");
        } catch (ClassNotFoundException e) {
            addon.log("Level addon not found - palier auto-notification disabled.");
        }
    }

    private static void checkNewClaimable(Island island, long newLevel,
            PalierManager palierManager, PalierSettings settings) {
        // Use the event's level directly — Level addon may not have persisted yet when the event fires
        boolean anyNew = settings.getPaliers().stream()
                .filter(p -> !palierManager.isClaimed(island, p.getId()))
                .anyMatch(p -> newLevel >= p.getRequiredLevel());
        if (anyNew) palierManager.notifyClaimable(island);
    }
}
