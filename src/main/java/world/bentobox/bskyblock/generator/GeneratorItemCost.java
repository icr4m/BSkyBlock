package world.bentobox.bskyblock.generator;

public record GeneratorItemCost(String type, int amount) {
    public boolean isCustomItem() { return type.contains(":"); }
}
