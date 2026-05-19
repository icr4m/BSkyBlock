package world.bentobox.bskyblock.commands.island;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bskyblock.BSkyBlock;

import java.util.List;

public class IslandHonourCommand extends CompositeCommand {

    public IslandHonourCommand(CompositeCommand parent) {
        super(parent, "honour", "honneur");
    }

    @Override
    public void setup() {
        setDescription("Afficher l'honneur de votre île");
        setOnlyPlayer(true);
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        BSkyBlock addon = (BSkyBlock) getAddon();
        Island island = getIslands().getIsland(getWorld(), user.getUniqueId());
        if (island == null) {
            user.sendRawMessage(addon.getDonateSettings().getMsgNoIsland());
            return false;
        }
        long honour = addon.getDonateManager().getHonour(island);
        String msg = addon.getDonateSettings().getMsgHonourDisplay()
                .replace("%honour%", String.valueOf(honour));
        user.sendRawMessage(msg);
        return true;
    }
}
