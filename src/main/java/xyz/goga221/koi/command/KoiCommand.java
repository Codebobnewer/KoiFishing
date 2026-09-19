package xyz.goga221.koi.command;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.data.PlayerFishStats;
import xyz.goga221.koi.fishing.FishItems;
import xyz.goga221.koi.fishing.FishRarity;
import xyz.goga221.koi.item.BaitItems;
import xyz.goga221.koi.item.RodItems;
import xyz.goga221.koi.menu.KoiConfigMenu;
import xyz.goga221.koi.menu.KoidexMenu;
import xyz.goga221.koi.tournament.RecurringTournamentSchedule;
import xyz.goga221.koi.tournament.ScheduledTournament;
import xyz.goga221.koi.tournament.Tournament;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;

/**
 * Registers the {@code /koi} command tree: {@code give}, {@code reload}, {@code world},
 * {@code config} (admin hub GUI - fish species and rod tier editors), {@code stats}, and
 * {@code tournament} (setprize/start/stop/status/schedule/cancelschedule/repeat/stoprepeat - the
 * last four need Chronos installed). {@code /koidex} (collection book) is a standalone top-level
 * command, registered separately in {@link #register()}.
 */
public class KoiCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String ADMIN_PERMISSION = "koi.admin";

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
                            "<gold>Koi</gold> <gray>- use /koi give, /koi reload, /koi world, /koi config, "
                                    + "/koi test, /koi stats, /koi tournament, /koidex</gray>"));
                })
                .register();

        new CommandAPICommand("koidex")
                .executesPlayer((player, args) -> {
                    new KoidexMenu().open(player);
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
                        // type is constrained to rod/bait/fish by MultiLiteralArgument (safe to
                        // inline); id is free text, so it goes through a placeholder.
                        player.sendMessage(MM.deserialize("<red>Unknown " + type + " id: <id></red>",
                                Placeholder.unparsed("id", id)));
                        return;
                    }

                    item.setAmount(Math.max(1, Math.min(64, amount)));
                    target.getInventory().addItem(item);
                    player.sendMessage(MM.deserialize("<green>Gave <target> " + amount + "x <id></green>",
                            Placeholder.unparsed("target", target.getName()),
                            Placeholder.unparsed("id", id)));
                });
    }

    private CommandAPICommand reloadCommand() {
        return new CommandAPICommand("reload")
                .withPermission(ADMIN_PERMISSION)
                .executesPlayer((player, args) -> {
                    // Five independent YAML reads - off the calling thread so this admin command
                    // can't hitch a region for it, then hop back to the player's own thread to
                    // confirm (mirrors menu.KoidexMenu#open's async-read/sync-respond pattern).
                    KoiPlugin.getScheduler().runTaskAsynchronously(() -> {
                        KoiPlugin.getConfigManager().reload();
                        KoiPlugin.getRodTierPool().reload();
                        KoiPlugin.getBaitTypePool().reload();
                        KoiPlugin.getSeaCreaturePool().reload();
                        KoiPlugin.getFishManager().reload();
                        KoiPlugin.getScheduler().runTask(player, () -> player.sendMessage(
                                MM.deserialize("<green>Koi configuration, rod tiers, bait types, sea creatures, and fish pool reloaded.</green>")));
                    });
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

                    World world = KoiPlugin.getInstance().getServer().getWorld(worldName);
                    if (world == null) {
                        player.sendMessage(MM.deserialize("<red>Unknown world: <world></red>",
                                Placeholder.unparsed("world", worldName)));
                        return;
                    }

                    boolean enabled = state.equals("enable");
                    KoiPlugin.getConfigManager().setWorldEnabled(world.getName(), enabled);
                    player.sendMessage(MM.deserialize("<green>Koi fishing " + (enabled ? "enabled" : "disabled")
                            + " in " + world.getName() + "</green>"));
                });
    }

    private CommandAPICommand configCommand() {
        return new CommandAPICommand("config")
                .withPermission(ADMIN_PERMISSION)
                .executesPlayer((player, args) -> {
                    new KoiConfigMenu().open(player);
                });
    }

    private CommandAPICommand testCommand() {
        return new CommandAPICommand("test")
                .withPermission(ADMIN_PERMISSION)
                .withSubcommand(testTierCommand())
                .withSubcommand(testSeaCreatureCommand())
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

                    if (!KoiPlugin.getFishManager().getPool().hasSpecies(rarity)) {
                        player.sendMessage(MM.deserialize("<red>No fish species are configured for rarity " + rarity.name() + ".</red>"));
                        return;
                    }

                    KoiPlugin.getFishManager().forceNextTier(target.getUniqueId(), rarity);
                    player.sendMessage(MM.deserialize("<green><target>'s next catch will be <yellow>" + rarity.name() + "</yellow>.</green>",
                            Placeholder.unparsed("target", target.getName())));
                });
    }

    private CommandAPICommand testSeaCreatureCommand() {
        return new CommandAPICommand("seacreature")
                .withPermission(ADMIN_PERMISSION)
                .withOptionalArguments(new EntitySelectorArgument.OnePlayer("player"))
                .executesPlayer((player, args) -> {
                    Player target = args.getOrDefaultUnchecked("player", player);

                    if (KoiPlugin.getSeaCreaturePool().getCreatures().isEmpty()) {
                        player.sendMessage(MM.deserialize("<red>No sea creatures are configured yet - add one under /koi config first.</red>"));
                        return;
                    }

                    KoiPlugin.getFishManager().forceNextSeaCreature(target.getUniqueId());
                    player.sendMessage(MM.deserialize("<green><target>'s next bite will be a sea creature encounter.</green>",
                            Placeholder.unparsed("target", target.getName())));
                });
    }

    private CommandAPICommand testClearCommand() {
        return new CommandAPICommand("clear")
                .withPermission(ADMIN_PERMISSION)
                .withOptionalArguments(new EntitySelectorArgument.OnePlayer("player"))
                .executesPlayer((player, args) -> {
                    Player target = args.getOrDefaultUnchecked("player", player);
                    KoiPlugin.getFishManager().clearForcedTier(target.getUniqueId());
                    KoiPlugin.getFishManager().clearForcedSeaCreature(target.getUniqueId());
                    player.sendMessage(MM.deserialize("<green>Cleared forced tier/sea-creature overrides for <target>.</green>",
                            Placeholder.unparsed("target", target.getName())));
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
                    KoiPlugin.getScheduler().runTaskAsynchronously(() -> {
                        Optional<PlayerFishStats> stats = KoiPlugin.getDatabaseManager().getCatchRepository().findStats(target.getUniqueId());
                        KoiPlugin.getScheduler().runTask(player, () -> {
                            if (stats.isEmpty()) {
                                player.sendMessage(MM.deserialize("<yellow><target> hasn't caught anything yet.</yellow>",
                                        Placeholder.unparsed("target", target.getName())));
                                return;
                            }

                            PlayerFishStats playerStats = stats.get();
                            player.sendMessage(MM.deserialize("<gold><target>'s Koi stats</gold>",
                                    Placeholder.unparsed("target", target.getName())));
                            player.sendMessage(MM.deserialize("<gray>Total catches: <white>" + playerStats.getTotalCatches() + "</white></gray>"));
                            player.sendMessage(MM.deserialize("<gray>Rarest catch: <white>" + playerStats.getBestRarity().name() + "</white></gray>"));
                            player.sendMessage(MM.deserialize("<gray>Sea creatures caught: <white>" + playerStats.getSeaCreaturesCaught() + "</white></gray>"));
                            player.sendMessage(MM.deserialize("<gray>Sea creatures killed: <white>" + playerStats.getSeaCreaturesKilled() + "</white></gray>"));
                        });
                    });
                });
    }

    private CommandAPICommand tournamentCommand() {
        return new CommandAPICommand("tournament")
                .withSubcommand(tournamentSetPrizeCommand())
                .withSubcommand(tournamentStartCommand())
                .withSubcommand(tournamentStopCommand())
                .withSubcommand(tournamentStatusCommand())
                .withSubcommand(tournamentScheduleCommand())
                .withSubcommand(tournamentCancelScheduleCommand())
                .withSubcommand(tournamentRepeatCommand())
                .withSubcommand(tournamentStopRepeatCommand());
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
                    KoiPlugin.getTournamentManager().setPendingPrize(hand);
                    player.sendMessage(MM.deserialize("<green>Tournament prize set.</green>"));
                });
    }

    private CommandAPICommand tournamentStartCommand() {
        return new CommandAPICommand("start")
                .withPermission(ADMIN_PERMISSION)
                .withArguments(new IntegerArgument("minutes", 1))
                .executesPlayer((player, args) -> {
                    int minutes = args.getUnchecked("minutes");
                    if (!KoiPlugin.getTournamentManager().hasPendingPrize()) {
                        player.sendMessage(MM.deserialize("<red>Set a prize first with /koi tournament setprize.</red>"));
                        return;
                    }
                    if (!KoiPlugin.getTournamentManager().start(minutes)) {
                        player.sendMessage(MM.deserialize("<red>A tournament is already running.</red>"));
                    }
                });
    }

    private CommandAPICommand tournamentStopCommand() {
        return new CommandAPICommand("stop")
                .withPermission(ADMIN_PERMISSION)
                .executesPlayer((player, args) -> {
                    boolean stopped = KoiPlugin.getTournamentManager().stop();
                    player.sendMessage(MM.deserialize(stopped
                            ? "<green>Tournament stopped.</green>"
                            : "<red>No tournament is running.</red>"));
                });
    }

    private CommandAPICommand tournamentStatusCommand() {
        return new CommandAPICommand("status")
                .executesPlayer((player, args) -> {
                    Tournament active = KoiPlugin.getTournamentManager().getActive();
                    if (active == null) {
                        player.sendMessage(MM.deserialize("<gray>No tournament is currently running.</gray>"));
                        reportScheduled(player);
                        return;
                    }

                    String leader = active.getLeaderId() == null
                            ? "nobody yet"
                            : KoiPlugin.getInstance().getServer().getOfflinePlayer(active.getLeaderId()).getName()
                                    + " (" + active.getLeaderRarity().name() + ")";
                    player.sendMessage(MM.deserialize("<gold>Tournament: <yellow>" + active.secondsRemaining()
                            + "s</yellow> remaining. Leader: <yellow><leader></yellow></gold>",
                            Placeholder.unparsed("leader", leader)));
                    reportScheduled(player);
                    reportRecurring(player);
                });
    }

    private void reportScheduled(Player player) {
        ScheduledTournament scheduled = KoiPlugin.getTournamentManager().getScheduled();
        if (scheduled == null) {
            return;
        }
        String when = String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d", scheduled.year(), scheduled.month(),
                scheduled.day(), scheduled.hour(), scheduled.minute());
        player.sendMessage(MM.deserialize("<gray>Next scheduled: <white><when></white> (<white>"
                        + scheduled.durationMinutes() + "</white> min, on Chronos's calendar)</gray>",
                Placeholder.unparsed("when", when)));
    }

    private void reportRecurring(Player player) {
        RecurringTournamentSchedule recurring = KoiPlugin.getTournamentManager().getRecurring();
        if (recurring == null) {
            return;
        }
        player.sendMessage(MM.deserialize("<gray>Repeating every <white>" + recurring.intervalSeconds()
                + "</white> game-seconds (<white>" + recurring.durationMinutes() + "</white> min each time).</gray>"));
    }

    private CommandAPICommand tournamentScheduleCommand() {
        return new CommandAPICommand("schedule")
                .withPermission(ADMIN_PERMISSION)
                .withArguments(
                        new IntegerArgument("year", 1),
                        new IntegerArgument("month", 1),
                        new IntegerArgument("day", 1),
                        new IntegerArgument("hour", 0, 23),
                        new IntegerArgument("minute", 0, 59),
                        new IntegerArgument("durationMinutes", 1)
                )
                .executesPlayer((player, args) -> {
                    int year = args.getUnchecked("year");
                    int month = args.getUnchecked("month");
                    int day = args.getUnchecked("day");
                    int hour = args.getUnchecked("hour");
                    int minute = args.getUnchecked("minute");
                    int durationMinutes = args.getUnchecked("durationMinutes");

                    try {
                        KoiPlugin.getTournamentManager().scheduleAt(year, month, day, hour, minute, durationMinutes);
                        player.sendMessage(MM.deserialize("<green>Tournament scheduled - it'll appear on Chronos's calendar "
                                + "and start automatically when that in-game time arrives.</green>"));
                    } catch (RuntimeException e) {
                        player.sendMessage(MM.deserialize("<red><reason></red>", Placeholder.unparsed("reason", e.getMessage())));
                    }
                });
    }

    private CommandAPICommand tournamentCancelScheduleCommand() {
        return new CommandAPICommand("cancelschedule")
                .withPermission(ADMIN_PERMISSION)
                .executesPlayer((player, args) -> {
                    boolean cancelled = KoiPlugin.getTournamentManager().cancelScheduled();
                    player.sendMessage(MM.deserialize(cancelled
                            ? "<green>Scheduled tournament cancelled.</green>"
                            : "<red>No tournament is scheduled.</red>"));
                });
    }

    private CommandAPICommand tournamentRepeatCommand() {
        return new CommandAPICommand("repeat")
                .withPermission(ADMIN_PERMISSION)
                .withArguments(
                        new MultiLiteralArgument("interval", "hourly", "daily", "weekly"),
                        new IntegerArgument("durationMinutes", 1)
                )
                .executesPlayer((player, args) -> {
                    String interval = args.getUnchecked("interval");
                    int durationMinutes = args.getUnchecked("durationMinutes");

                    try {
                        KoiPlugin.getTournamentManager().startRepeating(interval, durationMinutes);
                        player.sendMessage(MM.deserialize("<green>Tournament will now run <interval>, "
                                        + durationMinutes + " minutes each time - it's on Chronos's calendar too.</green>",
                                Placeholder.unparsed("interval", interval)));
                    } catch (RuntimeException e) {
                        player.sendMessage(MM.deserialize("<red><reason></red>", Placeholder.unparsed("reason", e.getMessage())));
                    }
                });
    }

    private CommandAPICommand tournamentStopRepeatCommand() {
        return new CommandAPICommand("stoprepeat")
                .withPermission(ADMIN_PERMISSION)
                .executesPlayer((player, args) -> {
                    boolean stopped = KoiPlugin.getTournamentManager().stopRepeating();
                    player.sendMessage(MM.deserialize(stopped
                            ? "<green>Recurring tournament schedule cancelled.</green>"
                            : "<red>No recurring tournament schedule is set.</red>"));
                });
    }

    private ItemStack buildGiveItem(String type, String id) {
        return switch (type) {
            case "rod" -> KoiPlugin.getRodTierPool().findTier(id).map(RodItems::create).orElse(null);
            case "bait" -> KoiPlugin.getBaitTypePool().findType(id).map(BaitItems::create).orElse(null);
            case "fish" -> KoiPlugin.getFishManager().getPool().findSpecies(id)
                    .map(FishItems::create)
                    .orElse(null);
            default -> null;
        };
    }
}
