package gigaherz.toolbelt.client;

import gigaherz.toolbelt.BeltFinder;
import gigaherz.toolbelt.network.BeltContentsChange;
import gigaherz.toolbelt.network.SyncBeltSlotContents;
import gigaherz.toolbelt.slot.BeltExtensionSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

public class ClientPacketHandlers
{
    public static void handleBeltContentsChange(final BeltContentsChange message)
    {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            Entity entity = minecraft.world.getEntityByID(message.player);
            if (!(entity instanceof PlayerEntity))
                return;
            PlayerEntity player = (PlayerEntity) entity;
            switch (message.where)
            {
                case MAIN:
                    player.inventory.setInventorySlotContents(message.slot, message.stack);
                    break;
                case BELT_SLOT:
                    BeltFinder.setToAnyBeltSlot(player, message.slot, message.stack);
                    break;
                case BAUBLES:
                    BeltFinder.setToAnyBaubles(player, message.slot, message.stack);
                    break;
            }
        });
    }

    public static void handleBeltSlotContents(SyncBeltSlotContents message)
    {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            Entity entity = minecraft.world.getEntityByID(message.entityId);
            if (entity instanceof PlayerEntity)
            {
                BeltExtensionSlot.get((LivingEntity) entity).ifPresent((slot) -> slot.setAll(message.stacks));
            }
        });
    }
}
