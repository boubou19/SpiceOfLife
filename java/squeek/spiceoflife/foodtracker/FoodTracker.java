package squeek.spiceoflife.foodtracker;

import ibxm.Player;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.EntityEvent.EntityConstructing;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import squeek.spiceoflife.ModConfig;
import squeek.spiceoflife.ModSpiceOfLife;
import squeek.spiceoflife.compat.CompatHelper;
import squeek.spiceoflife.compat.PacketDispatcher;
import squeek.spiceoflife.foodtracker.foodgroups.FoodGroupRegistry;
import squeek.spiceoflife.network.PacketFoodEatenAllTime;
import squeek.spiceoflife.network.PacketFoodHistory;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;

public class FoodTracker
{
	/**
	 * Add relevant extended entity data whenever an entity comes into existence
	 */
	@SubscribeEvent
	public void onEntityConstructing(EntityConstructing event)
	{
		if (event.entity instanceof EntityPlayer && FoodHistory.get((EntityPlayer) event.entity) == null)
		{
			EntityPlayer player = (EntityPlayer) event.entity;
			new FoodHistory(player);
		}
	}

	/**
	 * Sync savedata/config whenever a player joins the server
	 */
	@SubscribeEvent
	public void onPlayerLogin(PlayerLoggedInEvent event)
	{
		// server needs to send config settings to the client
		ModConfig.sync(event.player);

		// server needs to send food groups to the client
		FoodGroupRegistry.sync(event.player);

		// server needs to send any loaded data to the client
		FoodHistory foodHistory = FoodHistory.get(event.player);
		syncFoodHistory(foodHistory);
	}

	/**
	 * Resync food history whenever a player changes dimensions
	 */
	@SubscribeEvent
	public void onPlayerChangedDimension(PlayerChangedDimensionEvent event)
	{
		FoodHistory foodHistory = FoodHistory.get(event.player);
		syncFoodHistory(foodHistory);
	}

	/**
	 * Save death-persistent data to avoid any rollbacks on respawn
	 */
	@SubscribeEvent
	public void onLivingDeathEvent(LivingDeathEvent event)
	{
		if (FMLCommonHandler.instance().getEffectiveSide().isClient() || !(event.entity instanceof EntityPlayer))
			return;

		EntityPlayer player = (EntityPlayer) event.entity;

		FoodHistory foodHistory = FoodHistory.get(player);
		foodHistory.saveNBTData(null);
	}

	/**
	 * Load any death-persistent savedata on respawn and sync it
	 */
	@SubscribeEvent
	public void onPlayerRespawn(PlayerRespawnEvent event)
	{
		if (FMLCommonHandler.instance().getEffectiveSide().isClient())
			return;

		// load any persistent food history data
		FoodHistory foodHistory = FoodHistory.get(event.player);
		foodHistory.loadNBTData(null);

		// server needs to send any loaded data to the client
		syncFoodHistory(foodHistory);
	}

	
	/**
	 * Sync saturation whenever it changes (vanilla MC only syncs when it hits 0)
	 */
	private float lastSaturationLevel = 0;
	
	@SubscribeEvent
	public void onLivingUpdateEvent(LivingUpdateEvent event)
	{
		if (FMLCommonHandler.instance().getEffectiveSide().isClient() || !(event.entity instanceof EntityPlayer))
			return;
		
		EntityPlayerMP player = (EntityPlayerMP) event.entity;
		
		if (this.lastSaturationLevel != player.getFoodStats().getSaturationLevel())
		{
			CompatHelper.sendPlayerHealthUpdatePacket(player);
		}
	}

	public void syncFoodHistory(FoodHistory foodHistory)
	{
		PacketDispatcher.get().sendTo(new PacketFoodEatenAllTime(foodHistory.totalFoodsEatenAllTime), (EntityPlayerMP) foodHistory.player);
		PacketDispatcher.get().sendTo(new PacketFoodHistory(foodHistory, true), (EntityPlayerMP) foodHistory.player);
	}

	public static boolean addFoodEatenByPlayer(FoodEaten foodEaten, EntityPlayer player)
	{
		// client needs to be told by the server otherwise the client can get out of sync easily
		if (!player.worldObj.isRemote)
			PacketDispatcher.get().sendTo(new PacketFoodHistory(foodEaten), (EntityPlayerMP) player);

		return FoodHistory.get(player).addFood(foodEaten);
	}

	public static int getFoodHistoryCountOf(ItemStack food, EntityPlayer player)
	{
		return FoodHistory.get(player).getFoodCount(food);
	}

	public static int getFoodHistoryCountOfLastEatenBy(EntityPlayer player)
	{
		return FoodHistory.get(player).getFoodCount(getFoodLastEatenBy(player));
	}

	public static int getFoodHistoryLengthInRelevantUnits(EntityPlayer player)
	{
		return FoodHistory.get(player).getHistoryLengthInRelevantUnits();
	}

	public static ItemStack getFoodLastEatenBy(EntityPlayer player)
	{
		return FoodHistory.get(player).getLastEatenFood().itemStack;
	}

	@Override
	public void connectionOpened(NetHandler netClientHandler, String server, int port, INetworkManager manager)
	{
		ModConfig.assumeClientOnly();
	}

	@Override
	public void connectionClosed(INetworkManager manager)
	{
		ModConfig.assumeClientOnly();
	}

	@Override
	public void playerLoggedIn(Player player, NetHandler netHandler, INetworkManager manager)
	{
	}

	@Override
	public String connectionReceived(NetLoginHandler netHandler, INetworkManager manager)
	{
		return null;
	}

	@Override
	public void connectionOpened(NetHandler netClientHandler, MinecraftServer server, INetworkManager manager)
	{
	}

	@Override
	public void clientLoggedIn(NetHandler clientHandler, INetworkManager manager, Packet1Login login)
	{
	}
}
