package world.bentobox.bskyblock;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.admin.DefaultAdminCommand;
import world.bentobox.bentobox.api.commands.island.DefaultPlayerCommand;
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.bentobox.api.configuration.WorldSettings;
import world.bentobox.bskyblock.commands.IslandAboutCommand;
import world.bentobox.bskyblock.commands.island.IslandDonateCommand;
import world.bentobox.bskyblock.commands.island.IslandHonourCommand;
import world.bentobox.bskyblock.commands.island.IslandPaliersCommand;
import world.bentobox.bskyblock.donate.DonateManager;
import world.bentobox.bskyblock.donate.DonateSettings;
import world.bentobox.bskyblock.donate.HonourDataManager;
import world.bentobox.bskyblock.donate.HonourPlaceholder;
import world.bentobox.bskyblock.generator.GeneratorCostListener;
import world.bentobox.bskyblock.generator.GeneratorCostSettings;
import world.bentobox.bskyblock.generators.ChunkGeneratorWorld;
import world.bentobox.bskyblock.generators.CobblestoneGeneratorListener;
import world.bentobox.bskyblock.paliers.PalierDataManager;
import world.bentobox.bskyblock.paliers.PalierListener;
import world.bentobox.bskyblock.paliers.PalierManager;
import world.bentobox.bskyblock.paliers.PalierSettings;

/**
 * Main BSkyBlock class - provides an island minigame in the sky
 * @author tastybento
 * @author Poslovitch
 */
public class BSkyBlock extends GameModeAddon implements Listener {

    private static final String NETHER = "_nether";
    private static final String THE_END = "_the_end";

    // Settings
    private Settings settings;
    private ChunkGeneratorWorld chunkGenerator;
    private final Config<Settings> configObject = new Config<>(this, Settings.class);

    // Custom features
    private PalierDataManager palierDataManager;
    private PalierSettings palierSettings;
    private PalierManager palierManager;

    // Donate / Honour system
    private HonourDataManager honourDataManager;
    private DonateSettings donateSettings;
    private DonateManager donateManager;

    // Generator item costs
    private GeneratorCostSettings generatorCostSettings;

    @Override
    public void onLoad() {
        // Save the default config from config.yml
        saveDefaultConfig();
        // Load settings from config.yml. This will check if there are any issues with it too.
        loadSettings();
        // Chunk generator
        chunkGenerator = settings.isUseOwnGenerator() ? null : new ChunkGeneratorWorld(this);
        // Register commands
        playerCommand = new DefaultPlayerCommand(this)

        {
            @Override
            public void setup()
            {
                super.setup();
                new IslandAboutCommand(this);
                new IslandPaliersCommand(this);
                new IslandDonateCommand(this);
                new IslandHonourCommand(this);
            }
        };
        adminCommand = new DefaultAdminCommand(this) {};
    }

    private boolean loadSettings() {
        // Load settings again to get worlds
        settings = configObject.loadConfigObject();
        if (settings == null) {
            // Disable
            logError("BSkyBlock settings could not load! Addon disabled.");
            setState(State.DISABLED);
            return false;
        }
        return true;
    }

    @Override
    public void onEnable(){
        // Register this
        registerListener(this);

        // Paliers
        palierDataManager = new PalierDataManager(this);
        palierSettings = new PalierSettings(this);
        palierManager = new PalierManager(this, palierDataManager, palierSettings);
        PalierListener.register(this, palierManager, palierSettings);

        // Donate / Honour
        honourDataManager = new HonourDataManager(this);
        donateSettings = new DonateSettings(this);
        donateManager = new DonateManager(this, honourDataManager, donateSettings);

        // Generator item costs
        generatorCostSettings = new GeneratorCostSettings(this);
        GeneratorCostListener.register(this, generatorCostSettings);

        // PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new HonourPlaceholder(this).register();
            log("PlaceholderAPI found - honour placeholders registered.");
        }

        // Cobblestone generator
        registerListener(new CobblestoneGeneratorListener(this));
    }

    @Override
    public void onDisable() {
        // Nothing to do here
    }

    @Override
    public void onReload() {
        if (loadSettings()) {
            log("Reloaded BSkyBlock settings");
        }
    }

    /**
     * @return the settings
     */
    public Settings getSettings() {
        return settings;
    }

    public PalierDataManager getPalierDataManager() { return palierDataManager; }
    public PalierSettings getPalierSettings() { return palierSettings; }
    public PalierManager getPalierManager() { return palierManager; }

    public HonourDataManager getHonourDataManager() { return honourDataManager; }
    public DonateSettings getDonateSettings() { return donateSettings; }
    public DonateManager getDonateManager() { return donateManager; }

    @Override
    public void createWorlds() {
        String worldName = settings.getWorldName().toLowerCase();
        if (getServer().getWorld(worldName) == null) {
            log("Creating BSkyBlock world ...");
        }

        // Create the world if it does not exist
        islandWorld = getWorld(worldName, World.Environment.NORMAL, chunkGenerator);
        // Make the nether if it does not exist
        if (settings.isNetherGenerate()) {
            if (getServer().getWorld(worldName + NETHER) == null) {
                log("Creating BSkyBlock's Nether...");
            }
            netherWorld = settings.isNetherIslands() ? getWorld(worldName, World.Environment.NETHER, chunkGenerator) : getWorld(worldName, World.Environment.NETHER, null);
        }
        // Make the end if it does not exist
        if (settings.isEndGenerate()) {
            if (getServer().getWorld(worldName + THE_END) == null) {
                log("Creating BSkyBlock's End World...");
            }
            endWorld = settings.isEndIslands() ? getWorld(worldName, World.Environment.THE_END, chunkGenerator) : getWorld(worldName, World.Environment.THE_END, null);
        }
    }

    /**
     * Gets a world or generates a new world if it does not exist
     * @param worldName2 - the overworld name
     * @param env - the environment
     * @param chunkGenerator2 - the chunk generator. If <tt>null</tt> then the generator will not be specified
     * @return world loaded or generated
     */
    private World getWorld(String worldName2, Environment env, ChunkGeneratorWorld chunkGenerator2) {
        // Set world name
        worldName2 = env.equals(World.Environment.NETHER) ? worldName2 + NETHER : worldName2;
        worldName2 = env.equals(World.Environment.THE_END) ? worldName2 + THE_END : worldName2;
        WorldCreator wc = WorldCreator.name(worldName2).environment(env);
        World w = settings.isUseOwnGenerator() ? wc.createWorld() : wc.generator(chunkGenerator2).createWorld();
        applySpawnLimits(w);
        return w;
    }

    private void applySpawnLimits(World w) {
        if (w == null || getSettings() == null) {
            return;
        }
        Settings s = getSettings();
        if (s.getSpawnLimitMonsters() > 0) {
            w.setSpawnLimit(SpawnCategory.MONSTER, s.getSpawnLimitMonsters());
        }
        if (s.getSpawnLimitAmbient() > 0) {
            w.setSpawnLimit(SpawnCategory.AMBIENT, s.getSpawnLimitAmbient());
        }
        if (s.getSpawnLimitAnimals() > 0) {
            w.setSpawnLimit(SpawnCategory.ANIMAL, s.getSpawnLimitAnimals());
        }
        if (s.getSpawnLimitWaterAnimals() > 0) {
            w.setSpawnLimit(SpawnCategory.WATER_ANIMAL, s.getSpawnLimitWaterAnimals());
        }
        if (s.getTicksPerAnimalSpawns() > 0) {
            w.setTicksPerSpawns(SpawnCategory.ANIMAL, s.getTicksPerAnimalSpawns());
        }
        if (s.getTicksPerMonsterSpawns() > 0) {
            w.setTicksPerSpawns(SpawnCategory.MONSTER, s.getTicksPerMonsterSpawns());
        }
    }

    @Override
    public WorldSettings getWorldSettings() {
        return getSettings();
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return chunkGenerator;
    }

    @Override
    public void saveWorldSettings() {
        if (settings != null) {
            configObject.saveConfigObject(settings);
        }
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.api.addons.Addon#allLoaded()
     */
    @Override
    public void allLoaded() {
        // Save settings. This will occur after all addons have loaded
        this.saveWorldSettings();
        // Re-register donate/honour commands last so they override any same-named command
        // registered by other addons (e.g. Level addon registers its own /is donate).
        getPlayerCommand().ifPresent(cmd -> {
            new IslandDonateCommand(cmd);
            new IslandHonourCommand(cmd);
        });
    }

}
