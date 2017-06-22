/*******************************************************************************
 * This file is part of Minebot.
 *
 * Minebot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Minebot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Minebot.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package net.famzangl.minecraft.minebot.ai.task.inventory;

import net.famzangl.minecraft.minebot.ai.AIHelper;
import net.famzangl.minecraft.minebot.ai.task.AITask;
import net.famzangl.minecraft.minebot.ai.task.TaskOperations;
import net.famzangl.minecraft.minebot.ai.task.error.StringTaskError;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * Move a given amount from one slot to an other, empty slot in the current open
 * container/inventory.
 * 
 * @author michael
 *
 */
public abstract class MoveInInventoryTask extends AITask {

	private static final Marker MARKER_MOVE = MarkerManager.getMarker("move");
	private boolean moveDone;

	private int delay;

	public static final int DELAY = 5;

	/**
	 * All 3 methods should return constant values.
	 * 
	 * @return
	 */
	protected abstract int getFromStack(AIHelper aiHelper);

	protected abstract int getToStack(AIHelper aiHelper);

	/**
	 * How many items should be moved. Mind that items may be put back (might be
	 * a problem on get-only containers)
	 * 
	 * @param currentCount
	 * 
	 * @return
	 */
	protected abstract int getMissingAmount(AIHelper aiHelper, int currentCount);

	/**
	 * If not all items could be moved, this is called.
	 * 
	 * @param missing
	 *            Missing item count.
	 */
	protected void missingItems(int missing) {

	}

	@Override
	public boolean isFinished(AIHelper aiHelper) {
		return moveDone;
	}

	@Override
	public void runTick(AIHelper aiHelper, TaskOperations taskOperations) {
		final GuiContainer screen = (GuiContainer) aiHelper.getMinecraft().currentScreen;
		if (screen == null) {
			taskOperations.desync(new StringTaskError("Expected container to be open"));
			return;
		}
		if (delay > 0) {
			delay--;
		} else {
			int fromStack = getFromStack(aiHelper);
			int toStack = getToStack(aiHelper);
			if (fromStack < 0
					|| toStack < 0
					|| fromStack >= screen.inventorySlots.inventoryItemStacks
							.size()
					|| toStack >= screen.inventorySlots.inventoryItemStacks
							.size()) {
				LOGGER.error("Attempet to move : " + fromStack + " -> "
						+ toStack);
				taskOperations.desync(new StringTaskError("Invalid item move specification."));
				return;
			}
			Slot from = screen.inventorySlots.getSlot(fromStack);
			if (getSlotContentCount(from) <= 0) {
				taskOperations.desync(new StringTaskError("Nothing in source slot."));
				LOGGER.error(MARKER_MOVE, "Attempted to move from slot "
						+ fromStack + " but it was empty (" + from.slotNumber
						+ ", " + from.getStack() + ")");
				return;
			}

			Slot to = screen.inventorySlots.getSlot(toStack);
			int amount = getMissingAmount(aiHelper, getSlotContentCount(to));
			LOGGER.debug(MARKER_MOVE, "Move " + amount + " from " + fromStack
					+ " to " + toStack);

			int limit = Math.min(to.getSlotStackLimit(), from.getStack()
					.getMaxStackSize());
			int missing = Math.min(amount, limit - getSlotContentCount(to));

			while (getSlotContentCount(from) <= missing
					&& getSlotContentCount(from) > 0) {
				missing -= moveAll(aiHelper, from, to);
			}

			LOGGER.debug("Still missing (1): " + missing);
			if (getSlotContentCount(from) - getSlotContentCount(from) / 2 <= missing
					&& getSlotContentCount(from) > 0) {
				missing -= moveHalf(aiHelper, from, to);
			} else if (missing > 0 && getSlotContentCount(from) > 0) {
				missing -= moveStackPart(aiHelper, from, to, missing);
			} else if (missing > 0) {
				missingItems(missing);
			} else {
				moveDone = true;
			}
			delay = DELAY;
		}
	}

	private int moveAll(AIHelper aiHelper, Slot from, Slot to) {
		return moveStack(aiHelper, from, to, false);
	}

	private int moveHalf(AIHelper aiHelper, Slot from, Slot to) {
		return moveStack(aiHelper, from, to, true);
	}

	private int moveStack(AIHelper aiHelper, Slot from, Slot to,
			boolean rightclickOnStart) {
		int oldCount = getSlotContentCount(to);

		click(aiHelper, from.slotNumber, rightclickOnStart ? 1 : 0);

		click(aiHelper, to.slotNumber, 0);
		return getSlotContentCount(to) - oldCount;
	}

	private void click(AIHelper aiHelper, int slotNumber, int i) {
		System.out.println("Click on " + slotNumber + " using " + i);
		final GuiContainer screen = (GuiContainer) aiHelper.getMinecraft().currentScreen;
		aiHelper.getMinecraft().playerController.windowClick(
				screen.inventorySlots.windowId, slotNumber, i, 0,
				aiHelper.getMinecraft().player);
	}

	private int moveStackPart(AIHelper aiHelper, Slot from, Slot to, int count) {
		int oldCount = getSlotContentCount(to);

		click(aiHelper, from.slotNumber, 0);
		for (int i = 0; i < count; i++) {
			click(aiHelper, to.slotNumber, 1);
		}
		click(aiHelper, from.slotNumber, 0);
		return getSlotContentCount(to) - oldCount;
	}

	protected int getSlotContentCount(Slot slot) {
		return slot.getHasStack() ? slot.getStack().stackSize : 0;
	}

	protected static int convertPlayerInventorySlot(int inventorySlot) {
		// Offset: 10 blocks.
		if (inventorySlot < 9) {
			return inventorySlot + 9 * 3;
		} else {
			return inventorySlot - 9;
		}
	}
}
