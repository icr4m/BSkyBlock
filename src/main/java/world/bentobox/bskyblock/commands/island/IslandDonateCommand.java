package world.bentobox.bskyblock.commands.island;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bskyblock.BSkyBlock;
import world.bentobox.bskyblock.donate.gui.DonateGUI;

import java.util.List;

public class IslandDonateCommand extends CompositeCommand {

    public IslandDonateCommand(CompositeCommand parent) {
        super(parent, "donate");
    }

    @Override
    public void setup() {
        setDescription("Ouvrir le menu de donation d'honneur");
        setOnlyPlayer(true);
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        Island island = getIslands().getIsland(getWorld(), user.getUniqueId());
        if (island == null) {
            BSkyBlock addon = (BSkyBlock) getAddon();
            user.sendRawMessage(addon.getDonateSettings().getMsgNoIsland());
            return false;
        }
        BSkyBlock addon = (BSkyBlock) getAddon();
        new DonateGUI(addon, island, user.getPlayer()).open();
        return true;
    }
}
