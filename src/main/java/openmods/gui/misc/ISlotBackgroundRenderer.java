package openmods.gui.misc;

import net.minecraft.inventory.Slot;
import openmods.gui.component.BaseComponent;

public interface ISlotBackgroundRenderer {
	public void render(BaseComponent gui, Slot slot);
}
