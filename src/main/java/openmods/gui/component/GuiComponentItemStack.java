package openmods.gui.component;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import openmods.gui.IComponentParent;

import org.lwjgl.opengl.GL11;

import com.google.common.collect.ImmutableList;

public class GuiComponentItemStack extends BaseComponent {

	private final ItemStack stack;

	private boolean drawTooltip;

	private final float scale;

	private final int size;

	private final List<String> displayName;

	public GuiComponentItemStack(IComponentParent parent, int x, int y, ItemStack stack, boolean drawTooltip, float scale) {
		super(parent, x, y);
		this.stack = stack;
		this.drawTooltip = drawTooltip;
		this.scale = scale;

		this.size = MathHelper.floor_float(16 * scale);
		this.displayName = ImmutableList.of(stack.getDisplayName());
	}

	@Override
	public int getWidth() {
		return size;
	}

	@Override
	public int getHeight() {
		return size;
	}

	@Override
	public void render(int offsetX, int offsetY, int mouseX, int mouseY) {
		if (scale != 1) {
			GL11.glPushMatrix();
			GL11.glScalef(scale, scale, 1);
		}
		drawItemStack(stack, (int)((x + offsetX) / scale), (int)((y + offsetY) / scale), "");
		if (scale != 1) GL11.glPopMatrix();
	}

	@Override
	public void renderOverlay(int offsetX, int offsetY, int mouseX, int mouseY) {
		if (drawTooltip && isMouseOver(mouseX, mouseY)) drawHoveringText(displayName, offsetX + mouseX, offsetY + mouseY);
	}

}
