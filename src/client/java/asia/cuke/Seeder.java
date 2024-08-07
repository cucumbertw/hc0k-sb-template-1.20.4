package asia.cuke;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.registry.Registries;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Seeder implements ClientModInitializer {
	static int lastSlot = -1;
	static int lastSelect = -1;
	private static final int TARGET_RANGE = 4;
	private boolean isTaming = false;
	private LlamaEntity currentTarget = null;
	private final Set<ChickenEntity> fedChickens = new HashSet<>();
	public final  MinecraftClient client = MinecraftClient.getInstance();

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null && !isTaming && client.player.getMainHandStack().getItem() == Items.HAY_BLOCK) {
				feedNearbyLlamas(client);
//				autoTameLlama(client);
			} else if (client.player != null && client.player.getMainHandStack().getItem() == Items.WHEAT_SEEDS) {
				feedNearbyChickens(client);
			}
		});
	}

	private void feedNearbyLlamas(MinecraftClient client) {
		Box area = new Box(client.player.getBlockPos()).expand(TARGET_RANGE);
		client.world.getEntitiesByClass(LlamaEntity.class, area, LlamaEntity::isBaby)
				.forEach(llama -> {
					PlayerInteractEntityC2SPacket packet = PlayerInteractEntityC2SPacket.interact(llama, false, Hand.MAIN_HAND);
					client.getNetworkHandler().sendPacket(packet);
				});
		autoTameLlama(client);

	}

	public void autoTameLlama(MinecraftClient client) {
		if (isTaming) return;

		Box area = new Box(client.player.getBlockPos()).expand(TARGET_RANGE);
		LlamaEntity targetLlama = client.world.getEntitiesByClass(LlamaEntity.class, area, llama -> !llama.isTame() && !llama.isBaby())
				.stream().findFirst().orElse(null);

		if (targetLlama != null && targetLlama != currentTarget && !targetLlama.isBaby()) {
			currentTarget = targetLlama;
			isTaming = true;

			main_swap("empty_hand");

				client.execute(() -> {


					PlayerInteractEntityC2SPacket interactPacket = PlayerInteractEntityC2SPacket.interact(targetLlama, false, Hand.MAIN_HAND);
					Objects.requireNonNull(client.getNetworkHandler()).sendPacket(interactPacket);
					client.inGameHud.getChatHud().addMessage(Text.of("交互"));


					new Thread(() -> {
						client.inGameHud.getChatHud().addMessage(Text.of("开启现成"));

						try {
							while (true) {
								client.inGameHud.getChatHud().addMessage(Text.of("检查"));

								if (targetLlama.isTame()) {
									client.execute(() -> {
										client.inGameHud.getChatHud().addMessage(Text.of("驯服"));
										client.player.stopRiding();
										main_swap("minecraft:chest");
										PlayerInteractEntityC2SPacket newInteractPacket = PlayerInteractEntityC2SPacket.interact(targetLlama, false, Hand.MAIN_HAND);
										client.getNetworkHandler().sendPacket(newInteractPacket);
										client.inGameHud.getChatHud().addMessage(Text.of("发包" + targetLlama));
										client.inGameHud.getChatHud().addMessage(Text.of("具体" + newInteractPacket));


										client.getNetworkHandler().sendPacket(interactPacket);

										isTaming = false;
										currentTarget = null;
									}

									);
									break;
								}
							}
						} finally {
							isTaming = false;
							currentTarget = null;
						}
					}).start();
				});

		}
	}
	private void feedNearbyChickens(MinecraftClient client) {
		Box area = new Box(client.player.getBlockPos()).expand(TARGET_RANGE);
		client.world.getEntitiesByClass(ChickenEntity.class, area, chicken -> !fedChickens.contains(chicken))
				.stream()
				.filter(ChickenEntity::isBaby)
				.findFirst()
				.ifPresent(chicken -> {
					for (int i = 0; i < 64; i++) {
						feedChicken(client, chicken);
					}
					fedChickens.add(chicken);
				});
	}
	private void feedChicken(MinecraftClient client, ChickenEntity chicken) {
		PlayerInteractEntityC2SPacket packet = PlayerInteractEntityC2SPacket.interact(chicken, false, Hand.MAIN_HAND);
		client.getNetworkHandler().sendPacket(packet);
	}
	public static void doSwap(int slot) {
		MinecraftClient client = MinecraftClient.getInstance();
		inventorySwap(slot, client.player.getInventory().selectedSlot);
		switchToSlot(slot);
	}
	public static void switchToSlot(int slot) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.player.getInventory().selectedSlot = slot;
		client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
	}
	public static void inventorySwap(int slot, int selectedSlot) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (slot == lastSlot) {
			switchToSlot(lastSelect);
			lastSlot = -1;
			lastSelect = -1;
			return;
		}
		if (slot - 36 == selectedSlot) return;
//		if (CombatSetting.INSTANCE.invSwapBypass.getValue()) {
//			if (slot - 36 >= 0) {
//				lastSlot = slot;
//				lastSelect = selectedSlot;
//				switchToSlot(slot - 36);
//				return;
//			}
//
//			client.getNetworkHandler().sendPacket(new PickFromInventoryC2SPacket(slot));
//			return;
//		}

		client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot, selectedSlot, SlotActionType.SWAP, client.player);
	}
	private static void main_swap(String itemName) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;

		if (player == null) {
			return;
		}

		if (itemName.equalsIgnoreCase("empty_hand")) {
			int emptySlot = -1;
			for (int i = 0; i < 9 ; i++) {
				client.inGameHud.getChatHud().addMessage(Text.of("size" + player.getInventory().size()));
				ItemStack stack = player.getInventory().getStack(i);
				if (stack.isEmpty()) {
					emptySlot = i;
					break;
				}
			}

			if (emptySlot != -1) {
				player.getInventory().selectedSlot = emptySlot;

				UpdateSelectedSlotC2SPacket packet = new UpdateSelectedSlotC2SPacket(emptySlot);
				client.getNetworkHandler().sendPacket(packet);
				client.inGameHud.getChatHud().addMessage(Text.of("空手"));
			} else {
				client.inGameHud.getChatHud().addMessage(Text.of("没有空位"));
			}
			return;
		}

		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getStack(i);
			String registryName = Registries.ITEM.getId(stack.getItem()).toString();

			if (registryName != null && registryName.equalsIgnoreCase(itemName)) {
				player.getInventory().selectedSlot = i;
				client.inGameHud.getChatHud().addMessage(Text.of("i的值" + i));
//				doSwap(i);
				switchToSlot(i);
//				UpdateSelectedSlotC2SPacket packet = new UpdateSelectedSlotC2SPacket(i);
//				client.inGameHud.getChatHud().addMessage(Text.of("发" + packet));
//				client.getNetworkHandler().sendPacket(packet);
//				client.inGameHud.getChatHud().addMessage(Text.of("切换到:" + registryName));
				return;
			}
		}
		client.inGameHud.getChatHud().addMessage(Text.of("未找到" + itemName));


	}

}
