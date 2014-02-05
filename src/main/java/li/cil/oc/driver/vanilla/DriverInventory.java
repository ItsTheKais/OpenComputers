package li.cil.oc.driver.vanilla;

import li.cil.oc.api.network.Arguments;
import li.cil.oc.api.network.Callback;
import li.cil.oc.api.network.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.driver.ManagedTileEntityEnvironment;
import li.cil.oc.util.TileEntityLookup;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class DriverInventory implements li.cil.oc.api.driver.Block {
    @Override
    public boolean worksWith(final World world, final ItemStack stack) {
        final Class clazz = TileEntityLookup.get(world, stack);
        return clazz != null && IInventory.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean worksWith(final World world, final int x, final int y, final int z) {
        final TileEntity tileEntity = world.getBlockTileEntity(x, y, z);
        return tileEntity != null && tileEntity instanceof IInventory;
    }

    @Override
    public ManagedEnvironment createEnvironment(final World world, final int x, final int y, final int z) {
        return new Environment((IInventory) world.getBlockTileEntity(x, y, z));
    }

    public static final class Environment extends ManagedTileEntityEnvironment<IInventory> {
        public Environment(final IInventory tileEntity) {
            super(tileEntity, "inventory");
        }

        @Callback
        public Object[] getCapacity(final Context context, final Arguments args) {
            return new Object[]{tileEntity.getSizeInventory()};
        }

        @Callback
        public Object[] getSlotCount(final Context context, final Arguments args) {
            final int slot = checkSlot(args, 0);
            final ItemStack stack = tileEntity.getStackInSlot(slot);
            if (stack != null) {
                return new Object[]{stack.stackSize};
            } else {
                return new Object[]{0};
            }
        }

        @Callback
        public Object[] getSlotSpace(final Context context, final Arguments args) {
            final int slot = checkSlot(args, 0);
            final ItemStack stack = tileEntity.getStackInSlot(slot);
            if (stack != null) {
                final int maxStack = Math.min(tileEntity.getInventoryStackLimit(), stack.getMaxStackSize());
                return new Object[]{maxStack - stack.stackSize};
            } else {
                return new Object[]{tileEntity.getInventoryStackLimit()};
            }
        }

        @Callback
        public Object[] compare(final Context context, final Arguments args) {
            final int slotA = checkSlot(args, 0);
            final int slotB = checkSlot(args, 1);
            if (slotA == slotB) {
                return new Object[]{true};
            }
            final ItemStack stackA = tileEntity.getStackInSlot(slotA);
            final ItemStack stackB = tileEntity.getStackInSlot(slotB);
            if (stackA == null && stackB == null) {
                return new Object[]{true};
            } else if (stackA != null && stackB != null) {
                return new Object[]{itemEquals(stackA, stackB)};
            } else {
                return new Object[]{false};
            }
        }

        @Callback
        public Object[] transfer(final Context context, final Arguments args) {
            final int slotA = checkSlot(args, 0);
            final int slotB = checkSlot(args, 1);
            final int count = Math.max(0, Math.min(args.count() > 2 && args.checkAny(2) != null ? args.checkInteger(2) : 64, tileEntity.getInventoryStackLimit()));
            if (slotA == slotB || count == 0) {
                return new Object[]{true};
            }
            final ItemStack stackA = tileEntity.getStackInSlot(slotA);
            final ItemStack stackB = tileEntity.getStackInSlot(slotB);
            if (stackA == null) {
                // Empty.
                return new Object[]{false};
            } else if (stackB == null) {
                // Move.
                tileEntity.setInventorySlotContents(slotB, tileEntity.decrStackSize(slotA, count));
                return new Object[]{true};
            } else if (itemEquals(stackA, stackB)) {
                // Pile.
                final int space = Math.min(tileEntity.getInventoryStackLimit(), stackB.getMaxStackSize()) - stackB.stackSize;
                final int amount = Math.min(count, Math.min(space, stackA.stackSize));
                if (amount > 0) {
                    // Some.
                    stackA.stackSize -= amount;
                    stackB.stackSize += amount;
                    if (stackA.stackSize == 0) {
                        tileEntity.setInventorySlotContents(slotA, null);
                    }
                    tileEntity.onInventoryChanged();
                    return new Object[]{true};
                }
            } else if (count >= stackA.stackSize) {
                // Swap.
                tileEntity.setInventorySlotContents(slotB, stackA);
                tileEntity.setInventorySlotContents(slotA, stackB);
                return new Object[]{true};
            }
            // Fail.
            return new Object[]{false};
        }

        private int checkSlot(final Arguments args, final int number) {
            final int slot = args.checkInteger(number) - 1;
            if (slot < 0 || slot >= tileEntity.getSizeInventory()) {
                throw new IllegalArgumentException("slot index out of bounds");
            }
            return slot;
        }

        private boolean itemEquals(final ItemStack stackA, final ItemStack stackB) {
            return stackA.itemID == stackB.itemID && !stackA.getHasSubtypes() || stackA.getItemDamage() == stackB.getItemDamage();
        }
    }
}
