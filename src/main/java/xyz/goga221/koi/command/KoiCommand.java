package xyz.goga221.koi.command;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.data.PlayerFishStats;
import xyz.goga221.koi.fishing.FishItems;
import xyz.goga221.koi.fishing.FishRarity;
import xyz.goga221.koi.item.BaitItems;
import xyz.goga221.koi.item.BaitType;
import xyz.goga221.koi.item.RodItems;
import xyz.goga221.koi.item.RodTier;
import xyz.goga221.koi.menu.FishEditorMenu;
import xyz.goga221.koi.menu.KoidexMenu;
import xyz.goga221.koi.tournament.Tournament;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;

/**
 * Registers the {@code /koi} command tree: {@code give}, {@code reload}, {@code world},
 * {@code config} (fish editor GUI), {@code stats}, and {@code tournament}
 * (setprize/start/stop/status). {@code /koidex} (collection book) is a standalone top-level
 * command, registered separately in {@link #register()}.
 */
public class KoiCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String ADMIN_PERMISSION = "koi.admin";

    private final KoiPlugin plugin;

    public KoiCommand(KoiPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("koi")
                .withSubcommand(giveCommand())
                .withSubcommand(reloadCommand())
                .withSubcommand(worldCommand())
                .withSubcommand(configCommand())
                .withSubcommand(testCommand())
                .withSubcommand(statsCommand())
                .withSubcommand(tournamentCommand())
                .executesPlayer((player, args) -> {
                    player.sendMessage(MM.deserialize(
                            "<gold>Koi</gold> <gray>- use /koi give, /koi reload, /koi world, /koi config, /koi test, "
                                    + "/koi stats, /koi tournament, /koidex</gray>"));
                })
                .register();

        new CommandAPICommand("koidex")
                .executesPlayer((player, args) -> {
                    new KoidexMenu(plugin).open(player);
                })
                .register();
    }

    private CommandAPICommand giveCommand() {
        return new CommandAPICommand("give")
                .withPermission(ADMIN_PERMISSION)
                .withArguments(
                        new EntitySelectorArgument.OnePlayer("target"),
                        new MultiLiteralArgument("type", "rod", "bait", "fish"),
                        new StringArgument("id")
                )
                .withOptionalArguments(new IntegerArgument("amount", 1))
                .executesPlayer((player, args) -> {
                    Player target = args.getUnchecked("target");
                    String type = args.getUnchecked("type");
                    String id = args.getUnchecked("id");
                    int amount = args.getOrDefaultUnchecked("amount", 1);

                    ItemStack item = buildGiveItem(type, id);
                    if (item == null) {
                        player.sendMessage(MM.deserialize("<red>Unknown " + type + " id: " + id + "</red>"));
                        return;
                    }

                    item.setAmount(Math.max(1, Math.min(64, amount)));
                    target.getInventory().addItem(item);
                    player.sendMessage(MM.deserialize("<green>Gave " + target.getName() + " " + amount + "x " + id + "</green>"));
                });
    }

    private CommandAPICommand reloadCommand() {
        return new CommandAPICommand("reload")
                .withPermission(ADMIN_PERMISSION)
                .executesPlayer((player, args) -> {
                    plugin.getConfigManager().reload();
                    plugin.getFishManager().reload();
                    player.sendMessage(MM.deserialize("<green>Koi configuration and fish pool reloaded.</green>"));
                });
    }

    private CommandAPICommand worldCommand() {
        return new CommandAPICommand("world")
                .withPermission(ADMIN_PERMISSION)
                .withArguments(new MultiLiteralArgument("state", "enable", "disable"))
                .withOptionalArguments(new StringArgument("world"))
                .executesPlayer((player, args) -> {
                    String state = args.getUnchecked("state");
                    String worldName = args.getOrDefaultUnchecked("world", player.getWorld().getName());

                    World world = plugin.getServer().getWorld(worldName);
                    if (world == null) {
                        player.sendMessage(MM.deserialize("<red>Unknown world: " + worldName + "</red>"));
                        return;
                    }

                    boolean enabled = state.equals("enable");
                    plugin.getConfigManager().setWorldEnabled(world.getName(), enabled);
                    player.sendMessage(MM.deserialize("<green>Koi fishing " + (enabled ? "enabled" : "disabled")
                            + " in " + world.getName() + "</green>"));
                });
    }

    private CommandAPICommand configCommand() {
        return new CommandAPICommand("config")
                .withPermission(ADMIN_PERMISSION)
                .executesPlayer((player, args) -> {
                    new FishEditorMenu(plugin).open(player);
                });
    }

    private CommandAPICommand testCommand() {
        return new CommandAPICommand("test")
                .withPermission(ADMIN_PERMISSION)
                .withSubcommand(testTierCommand())
                .withSubcommand(testClearCommand());
    }

    private CommandAPICommand testTierCommand() {
        return new CommandAPICommand("tier")
                .withPermission(ADMIN_PERMISSION)
                .withArguments(new MultiLiteralArgument("rarity", "common", "uncommon", "rare", "epic", "legendary", "mythic"))
                .withOptionalArguments(new EntitySelectorArgument.OnePlayer("player"))
                .executesPlayer((player, args) -> {
                    String rarityName = args.getUnchecked("rarity");
                    Player target = args.getOrDefaultUnchecked("player", player);
                    FishRarity rarity = FishRarity.valueOf(rarityName.toUpperCase(Locale.ROOT));

                    if (!plugin.getFishManager().getPool().hasSpecies(rarity)) {
                        player.sendMessage(MM.deserialize("<red>No fish species are configured for rarity " + rarity.name() + ".</red>"));
                        return;
                    }

                    plugin.getFishManager().forceNextTier(target.getUniqueId(), rarity);
                    player.sendMessage(MM.deserialize("<green>" + target.getName() + "'s next catch will be <yellow>"
                            + rarity.name() + "</yellow>.</green>"));
                });
    }

    private CommandAPICommand testClearCommand() {
        return new CommandAPICommand("clear")
                .withPermission(ADMIN_PERMISSION)
                .withOptionalArguments(new EntitySelectorArgument.OnePlayer("player"))
                .executesPlayer((player, args) -> {
                    Player target = args.getOrDefaultUnchecked("player", player);
                    plugin.getFishManager().clearForcedTier(target.getUniqueId());
                    player.sendMessage(MM.deserialize("<green>Cleared the forced tier override for " + target.getName() + ".</green>"));
                });
    }

    private CommandAPICommand statsCommand() {
        return new CommandAPICommand("stats")
                .withOptionalArguments(new EntitySelectorArgument.OnePlayer("player"))
                .executesPlayer((player, args) -> {
                    Player target = args.getOrDefaultUnchecked("player", player);
                    if (!target.getUniqueId().equals(player.getUniqueId()) && !player.hasPermission(ADMIN_PERMISSION)) {
                        player.sendMessage(MM.deserialize("<red>You don't have permission to check other players' stats.</red>"));
                        return;
                    }

                    // SQLite read off this thread, reply once it's back on the player's own thread.
                    plugin.getScheduler().runTaskAsynchronously(() -> {
                        Optional<PlayerFishStats> stats = plugin.getDatabaseManager().getCatchRepository().findStats(target.getUniqueId());
                        plugin.getScheduler().runTask(player, () -> {
                            if (stats.isEmpty()) {
                                player.sendMessage(MM.deserialize("<yellow>" + target.getName() + " hasn't caught anything yet.</yellow>"));
                                return;
                            }

                            PlayerFishStats playerStats = stats.get();
                            player.sendMessage(MM.deserialize("<gold>" + target.getName() + "'s Koi stats</gold>"));
                            player.sendMessage(MM.deserialize("<gray>Total catches: <white>" + playerStats.getTotalCatches() + "</white></gray>"));
                            player.sendMessage(MM.deserialize("<gray>Rarest catch: <white>" + playerStats.getBestRarity().name() + "</white></gray>"));
                        });
                    });
                });
    }

    private CommandAPICommand tournamentCommand() {
        return new CommandAPICommand("tournament")
                .withSubcommand(tournamentSetPrizeCommand())
                .withSubcommand(tournamentStartCommand())
                .withSubcommand(tournamentStopCommand())
                .withSubcommand(tournamentStatusCommand());
    }

    private CommandAPICommand tournamentSetPrizeCommand() {
        return new CommandAPICommand("setprize")
                .withPermission(ADMIN_PERMISSION)
                .executesPlayer((player, args) -> {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand.getType().isAir()) {
                        player.sendMessage(MM.deserialize("<red>Hold the prize item in your main hand first.</red>"));
                        return;
                    }
                    plugin.getTournamentManager().setPendingPrize(hand);
                    player.sendMessage(MM.deserialize("<green>Tournament prize set.</green>"));
                });
    }

    private CommandAPICommand tournamentStartCommand() {
        return new CommandAPICommand("start")
                .withPermission(ADMIN_PERMISSION)
                .withArguments(new IntegerArgument("minutes", 1))
                .executesPlayer((player, args) -> {
                    int minutes = args.getUnchecked("minutes");
                    if (!plugin.getTournamentManager().hasPendingPrize()) {
                        player.sendMessage(MM.deserialize("<red>Set a prize first with /koi tournament setprize.</red>"));
                        return;
                    }
                    if (!plugin.getTournamentManager().start(minutes)) {
                        player.sendMessage(MM.deserialize("<red>A tournament is already running.</red>"));
                    }
                });
    }

    private CommandAPICommand tournamentStopCommand() {
        return new CommandAPICommand("stop")
                .withPermission(ADMIN_PERMISSION)
                .executesPlayer((player, args) -> {
                    boolean stopped = plugin.getTournamentManager().stop();
                    player.sendMessage(MM.deserialize(stopped
                            ? "<green>Tournament stopped.</green>"
                            : "<red>No tournament is running.</red>"));
                });
    }

    private CommandAPICommand tournamentStatusCommand() {
        return new CommandAPICommand("status")
                .executesPlayer((player, args) -> {
                    Tournament active = plugin.getTournamentManager().getActive();
                    if (active == null) {
                        player.sendMessage(MM.deserialize("<gray>No tournament is currently running.</gray>"));
                        return;
                    }

                    String leader = active.getLeaderId() == null
                            ? "nobody yet"
                            : plugin.getServer().getOfflinePlayer(active.getLeaderId()).getName()
                                    + " (" + active.getLeaderRarity().name() + ")";
                    player.sendMessage(MM.deserialize("<gold>Tournament: <yellow>" + active.secondsRemaining()
                            + "s</yellow> remaining. Leader: <yellow>" + leader + "</yellow></gold>"));
                });
    }

    private ItemStack buildGiveItem(String type, String id) {
        return switch (type) {
            case "rod" -> {
                RodTier tier = parseEnum(RodTier.class, id);
                yield tier != null ? RodItems.create(tier) : null;
            }
            case "bait" -> {
                BaitType bait = parseEnum(BaitType.class, id);
                yield bait != null ? BaitItems.create(bait) : null;
            }
            case "fish" -> plugin.getFishManager().getPool().findSpecies(id)
                    .map(FishItems::create)
                    .orElse(null);
            default -> null;
        };
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
