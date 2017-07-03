package money;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import money.generator.SLandGenerator;
import money.sland.SLand;
import money.utils.LevelPermissionType;
import money.utils.SLandPermissionType;
import money.utils.SLandUtils;
import money.utils.StringAligner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Him188 @ MoneySLand Project
 * @since MoneySLand 1.0.0
 */
public final class MoneySLandEventListener implements Listener {
	private final MoneySLand plugin;
	private final List<String> temp = new ArrayList<>();

	MoneySLandEventListener(MoneySLand plugin) {
		this.plugin = plugin;
	}

	private static boolean isShopBlock(Block block) {
		try {
			return MoneySLand.getInstance().getLandPool().values().stream().filter((land) -> land.getShopBlock().equals(block)).count() != 0;
		} catch (Exception e) {
			return false;
		}
	}

	private static Method METHOD;

	static {
		try {
			METHOD = PlayerInteractEvent.class.getMethod("getAction");
			METHOD.setAccessible(true);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void permissionChecker(PlayerInteractEvent event) {
		if (event.getItem().getId() == Item.STICK) {
			event.getPlayer().sendMessage(event.getBlock().getLocation().toString());
		}
		try {
			if (isAction(METHOD.invoke(event))
			    && !this.testPermission(event.getPlayer(), event.getBlock(), SLandPermissionType.TOUCH)) {
				event.setCancelled();
				event.getPlayer().sendMessage(this.plugin.translateMessage("event.no-permission"));
			}
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
			return;
		}
	}

	private static boolean isAction(Object object) { //for old api and new api.
		try {
			Class.forName("cn.nukkit.event.player.PlayerInteractEvent$Action");
			//object is must Action
			return object == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK || object == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK;
		} catch (ClassNotFoundException e) {
			//object is must int
			return (int) object == 0 || (int) object == 1;
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void permissionChecker(BlockBreakEvent event) {
		if (!this.testPermission(event.getPlayer(), event.getBlock(), SLandPermissionType.BREAK)) {
			event.setCancelled();
			event.getPlayer().sendMessage(this.plugin.translateMessage("event.no-permission"));
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void permissionChecker(BlockPlaceEvent event) {
		if (!this.testPermission(event.getPlayer(), event.getBlock(), SLandPermissionType.PLACE)) {
			event.setCancelled();
			event.getPlayer().sendMessage(this.plugin.translateMessage("event.no-permission"));
		}
	}

	private boolean testPermission(Player player, Position position, SLandPermissionType type) {
		return ! /* land level */ SLandUtils.arrayContains(SLandGenerator.GENERATOR_NAMES, position.level.getProvider().getGenerator())
		       || /* money.permission.sland.modify.* base permission*/ player.hasPermission(type.getPermission())
		       || /* shop block */ Optional.ofNullable(this.plugin.getLand(position)).map(land -> land.getShopBlock().equals(position.floor()) && LevelPermissionType.BREAK_SHOP.testPermission(player, land.getLevelInstance())).orElse(false)
		       || /* frame block */ Optional.ofNullable(this.plugin.getLand(position)).map(land -> land.isFrame(position) && LevelPermissionType.BREAK_FRAME.testPermission(player, land.getLevelInstance())).orElse(false)
		       || /* aisle block */ Optional.of(LevelPermissionType.BREAK_AISLE.testPermission(player, position.getLevel())).orElse(false)
		       || /* owner and invitees */ Optional.ofNullable(this.plugin.getLand(position)).map(land -> land.testPermission(player, type)).orElse(false)
				;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void shopActionListener(PlayerInteractEvent event) {
		Block block = event.getBlock();
		if (!isShopBlock(block)) {
			return;
		}

		event.setCancelled();

		Item item = event.getItem();
		Player player = event.getPlayer();


		SLand land;
		land = this.plugin.getLand(block);
		if (land == null) {
			return;
		}


		String hash = player.getUniqueId().toString() + block.hashCode();

		int id = item.getId();
		switch (id) {
			case Item.AIR:
				if (!player.hasPermission("money.permission.sland.buy")) {
					player.sendMessage(this.plugin.translateMessage("event.no-permission"));
					return;
				}

				//buy land
				if (land.isOwned()) {
					player.sendMessage(this.plugin.translateMessage("already.bought"));
					return;
				}

				if (temp.remove(hash)) {
					if (this.plugin.buyLand(land, player)) {
						player.sendMessage(this.plugin.translateMessage("buy.success"));
					} else {
						player.sendMessage(this.plugin.translateMessage("buy.failed"));
					}
				} else {
					temp.add(hash);
					Server.getInstance().getScheduler().scheduleDelayedTask(this.plugin, () -> temp.remove(hash), 20 * 30);
					StringAligner aligner = new StringAligner(this.plugin.calculatePrice(player, land), Money.getInstance().getMoney(player));

					player.sendMessage(this.plugin.translateMessage("event.buy.confirm",
							"price", aligner.string(),
							"currency", Money.getInstance().getCurrency1(),
							"money", aligner.another())
					);
				}
				break;
			default:
				if (land.isOwned()) {
					player.sendMessage(this.plugin.translateMessage("already.bought"));
					return;
				}
				StringAligner aligner = new StringAligner(this.plugin.calculatePrice(player, land), Money.getInstance().getMoney(player));

				player.sendMessage(this.plugin.translateMessage("land.info",
						"id", land.getTime(),
						"size", land.getX().getRealLength() * land.getZ().getRealLength(),
						"price", aligner.string(),
						"currency", Money.getInstance().getCurrency1(),
						"money", aligner.another())
				);
		}

	}
}
