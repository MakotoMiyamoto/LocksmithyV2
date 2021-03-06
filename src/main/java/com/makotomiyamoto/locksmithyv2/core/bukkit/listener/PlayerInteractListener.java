package com.makotomiyamoto.locksmithyv2.core.bukkit.listener;

import com.makotomiyamoto.locksmithyv2.lib.lock.Lockable;
import com.makotomiyamoto.locksmithyv2.lib.lock.event.LockAssignEvent;
import com.makotomiyamoto.locksmithyv2.lib.lock.insecure.LockableContainer;
import com.makotomiyamoto.locksmithyv2.lib.lock.insecure.LockablePairContainer;
import com.makotomiyamoto.locksmithyv2.lib.util.BlockPairUtils;
import com.makotomiyamoto.locksmithyv2.lib.util.CustomItemRecipeManager;
import com.makotomiyamoto.locksmithyv2.lib.util.KeyDataManager;
import com.makotomiyamoto.locksmithyv2.lib.util.Locksmithy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerInteractListener implements Listener {
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (! event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        // stop listener from firing twice
        if (Objects.equals(event.getHand(), EquipmentSlot.OFF_HAND)) return;
        Block block = event.getClickedBlock();
        assert block != null;

        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType().equals(Material.STICK)) {
            Location location = block.getLocation();
            player.sendMessage("" + location.getX() + " " + location.getY() + " " + location.getZ());
            player.sendMessage("" + Locksmithy.locationIsLockable(block.getLocation()));
            return;
        } else if (player.getInventory().getItemInMainHand().getType().equals(Material.PAPER)) {
            player.sendMessage(Locksmithy.getLockableContainers().toString());
        }

        Lockable targetLockable;
        if (player.getInventory().getItemInMainHand().isSimilar(CustomItemRecipeManager.insecureKeyItem)) {
            // check if player has inventory space for new key
            // for now, just handle creating the lock
            player.sendMessage("Pass");

            LockAssignEvent lockAssignEvent = new LockAssignEvent(player, block);
            BlockState state = event.getClickedBlock().getState();
            if (state instanceof InventoryHolder && ((InventoryHolder) state).getInventory().getSize() == 54) {
                //player.sendMessage(ChatColor.GRAY + "instanceof DoubleChest");
                Block chest1 = event.getClickedBlock();
                Block chest2 = BlockPairUtils.getConnectedChest((Chest) chest1.getState());
                var lockable = new LockablePairContainer(player, chest1.getLocation(), chest2.getLocation());
                var otherLockable = lockable.getPairLockable();
                lockAssignEvent.getLockableList().add(lockable);
                lockAssignEvent.getLockableList().add(otherLockable);
            } else if (event.getClickedBlock().getBlockData() instanceof Door) {
                //player.sendMessage(ChatColor.GRAY + "instanceof Door");
                Block door1 = event.getClickedBlock();
                Block door2 = BlockPairUtils.getConnectedDoorHalf(door1);
                var lockable = new LockablePairContainer(player, door1.getLocation(), door2.getLocation());
                var otherLockable = lockable.getPairLockable();
                lockAssignEvent.getLockableList().add(lockable);
                lockAssignEvent.getLockableList().add(otherLockable);
            } else {
                //player.sendMessage(ChatColor.GRAY + "instanceof neither DoubleChest nor Door");
                LockableContainer lockable = new LockableContainer(player, event.getClickedBlock().getLocation());
                lockAssignEvent.getLockableList().add(lockable);
            }

            Bukkit.getPluginManager().callEvent(lockAssignEvent);

            if (lockAssignEvent.isCancelled() || lockAssignEvent.getLockableList().isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "Lock could not be assigned.");
            } else {
                // TODO this properly when the proper implementation is necessary, please
                UUID lockableUid = lockAssignEvent.getLockableList().get(0).getLockUUID();
                ItemMeta keyMeta = lockAssignEvent.getPlayer().getInventory().getItemInMainHand().getItemMeta(); assert keyMeta != null;
                keyMeta.getPersistentDataContainer().set(KeyDataManager.keyBoundId, KeyDataManager.keyBoundIdTagType, lockableUid);

                ArrayList<String> keyLore = new ArrayList<>(Objects.requireNonNull(keyMeta.getLore()));
                keyLore.addAll( List.of("", ChatColor.GRAY + "Owner: " + event.getPlayer().getName()) );
                keyMeta.setLore(keyLore);

                var lockableLocation = lockAssignEvent.getLockableList().get(0).getLockLocation();
                keyMeta.setDisplayName(String.format("%s%s's Key (%d, %d, %d)",
                        ChatColor.YELLOW, lockAssignEvent.getPlayer().getName(),
                        lockableLocation.getBlockX(), lockableLocation.getBlockY(), lockableLocation.getBlockZ()));

                lockAssignEvent.getPlayer().getInventory().getItemInMainHand().setItemMeta(keyMeta);
                player.sendMessage(ChatColor.GREEN + "Lockable created! (not really, but it was successful)");

                for (Lockable lockable : lockAssignEvent.getLockableList()) {
                    Locksmithy.set(lockable.getLockLocation(), lockable);
                    Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(0, 127, 255), 1.0F);
                    event.getPlayer().getWorld().spawnParticle(Particle.REDSTONE, lockable.getLockLocation().clone().add(0.5, 1, 0.5), 3, dustOptions);
                }
            }
        } else if ((targetLockable = Locksmithy.get(block.getLocation())) != null) {
            UUID lockUniqueId = targetLockable.getLockUUID();
            @SuppressWarnings("ConstantConditions")
            List<ItemStack> keysOfTargetLockable = Arrays.stream(player.getInventory().getContents())
                    .filter(KeyDataManager::itemIsKey)
                    .filter(itemStack -> itemStack
                            .getItemMeta()
                            .getPersistentDataContainer()
                            .get(KeyDataManager.keyBoundId, KeyDataManager.keyBoundIdTagType)
                            .equals(lockUniqueId))
                    .collect(Collectors.toList());

        }
    }
}
