package world.bentobox.bskyblock.commands.island;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bskyblock.BSkyBlock;
import world.bentobox.bskyblock.paliers.gui.PaliersGUI;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class IslandPaliersCommand extends CompositeCommand {

    public IslandPaliersCommand(CompositeCommand parent) {
        super(parent, "paliers");
    }

    @Override
    public void setup() {
        setDescription("Ouvrir le menu des paliers de l'île");
        setOnlyPlayer(true);
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        Island island = getIslands().getIsland(getWorld(), user.getUniqueId());
        if (island == null) {
            user.sendRawMessage("§cVous n'avez pas d'île.");
            return false;
        }
        BSkyBlock addon = (BSkyBlock) getAddon();
        new PaliersGUI(addon, island, user.getPlayer()).open();
        return true;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        BSkyBlock addon = (BSkyBlock) getAddon();
        String prefix = args.isEmpty() ? "" : args.get(args.size() - 1).toLowerCase();
        List<String> suggestions = addon.getPalierSettings().getPaliers().stream()
                .map(p -> String.valueOf(p.getId()))
                .filter(id -> id.startsWith(prefix))
                .collect(Collectors.toList());
        return Optional.of(suggestions);
    }
}
