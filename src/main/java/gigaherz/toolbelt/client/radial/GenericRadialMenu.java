package gigaherz.toolbelt.client.radial;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import gigaherz.toolbelt.ConfigData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import org.apache.logging.log4j.util.TriConsumer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import javax.vecmath.Vector4f;
import java.util.Collection;
import java.util.List;

public class GenericRadialMenu
{
    public static final float OPEN_ANIMATION_LENGTH = 2.5f;

    public final IRadialMenuHost host;
    private final List<RadialMenuItem> items = Lists.newArrayList();
    private final List<RadialMenuItem> visibleItems = Lists.newArrayList();
    private final Minecraft minecraft;
    public Vector4f backgroundColor = new Vector4f(0, 0, 0, .25f);
    public Vector4f backgroundColorHover = new Vector4f(1, 1, 1, .25f);

    public enum State
    {
        INITIALIZING,
        OPENING,
        NORMAL,
        CLOSING,
        CLOSED
    }

    private State state = State.INITIALIZING;
    public double startAnimation;
    public float animProgress;
    public float radiusIn;
    public float radiusOut;
    public float itemRadius;
    public float animTop;

    private ITextComponent centralText;

    public GenericRadialMenu(Minecraft minecraft, IRadialMenuHost host)
    {
        this.minecraft = minecraft;
        this.host = host;
    }

    public void setCentralText(@Nullable ITextComponent centralText)
    {
        this.centralText = centralText;
    }

    public ITextComponent getCentralText()
    {
        return centralText;
    }

    public int getHovered()
    {
        for (int i = 0; i < visibleItems.size(); i++)
        {
            if (visibleItems.get(i).isHovered())
                return i;
        }
        return -1;
    }

    @Nullable
    public RadialMenuItem getHoveredItem()
    {
        for (RadialMenuItem item : visibleItems)
        {
            if (item.isHovered())
                return item;
        }
        return null;
    }

    public void setHovered(int which)
    {
        for (int i = 0; i < visibleItems.size(); i++)
        {
            visibleItems.get(i).setHovered(i == which);
        }
    }

    public int getVisibleItemCount()
    {
        return visibleItems.size();
    }

    public void clickItem()
    {
        switch (state)
        {
            case NORMAL:
                RadialMenuItem item = getHoveredItem();
                if (item != null)
                {
                    item.onClick();
                    return;
                }
                break;
            default:
                break;
        }
        onClickOutside();
    }

    public void onClickOutside()
    {
        // to be implemented by users
    }

    public boolean isClosed()
    {
        return state == State.CLOSED;
    }

    public boolean isReady()
    {
        return state == State.NORMAL;
    }

    public void visibilityChanged(RadialMenuItem item)
    {
        visibleItems.clear();
        for (RadialMenuItem radialMenuItem : items)
        {
            if (radialMenuItem.isVisible())
            {
                visibleItems.add(radialMenuItem);
            }
        }
    }

    public void add(RadialMenuItem item)
    {
        items.add(item);
        if (item.isVisible())
        {
            visibleItems.add(item);
        }
    }

    public void addAll(Collection<? extends RadialMenuItem> cachedMenuItems)
    {
        items.addAll(cachedMenuItems);
        for (RadialMenuItem cachedMenuItem : cachedMenuItems)
        {
            if (cachedMenuItem.isVisible())
            {
                visibleItems.add(cachedMenuItem);
            }
        }
    }

    public void clear()
    {
        items.clear();
        visibleItems.clear();
    }

    public void close()
    {
        Screen owner = host.getScreen();
        state = State.CLOSING;
        startAnimation = minecraft.world.getGameTime() + (double) minecraft.getRenderPartialTicks();
        animProgress = 1.0f;
        setHovered(-1);
    }

    public void tick()
    {
        Screen owner = host.getScreen();

        if (state == State.INITIALIZING)
        {
            startAnimation = minecraft.world.getGameTime() + (double) minecraft.getRenderPartialTicks();
            state = State.OPENING;
            animProgress = 0;
        }

        //updateAnimationState(minecraft.getRenderPartialTicks());
    }

    public void draw(float partialTicks, int mouseX, int mouseY)
    {
        updateAnimationState(partialTicks);

        if (isClosed())
            return;

        if (isReady())
            processMouse(mouseX, mouseY);

        Screen owner = host.getScreen();
        FontRenderer fontRenderer = host.getFontRenderer();
        ItemRenderer itemRenderer = host.getItemRenderer();

        boolean animated = state == State.OPENING || state == State.CLOSING;
        radiusIn = animated ? Math.max(0.1f, 30 * animProgress) : 30;
        radiusOut = radiusIn * 2;
        itemRadius = (radiusIn + radiusOut) * 0.5f;
        animTop = animated ? (1 - animProgress) * owner.height / 2.0f : 0;

        int x = owner.width / 2;
        int y = owner.height / 2;
        float z = 0;

        GlStateManager.pushMatrix();
        GlStateManager.translatef(0, animTop, 0);

        drawBackground(x, y, z, radiusIn, radiusOut);

        GlStateManager.popMatrix();

        if (isReady())
        {
            drawItems(x, y, z, owner.width, owner.height, fontRenderer, itemRenderer);

            ITextComponent currentCentralText = centralText;
            for (int i = 0; i < visibleItems.size(); i++)
            {
                RadialMenuItem item = visibleItems.get(i);
                if (item.isHovered())
                {
                    if (item.getCentralText() != null)
                        currentCentralText = item.getCentralText();
                    break;
                }
            }

            if (currentCentralText != null)
            {
                String text = currentCentralText.getFormattedText();
                fontRenderer.drawStringWithShadow(text, (owner.width - fontRenderer.getStringWidth(text)) / 2.0f, (owner.height - fontRenderer.FONT_HEIGHT) / 2.0f, 0xFFFFFFFF);
            }

            drawTooltips(mouseX, mouseY);
        }
    }

    private void updateAnimationState(float partialTicks)
    {
        float openAnimation = 0;
        Screen owner = host.getScreen();
        switch (state)
        {
            case OPENING:
                openAnimation = (float) ((minecraft.world.getGameTime() + partialTicks - startAnimation) / OPEN_ANIMATION_LENGTH);
                if (openAnimation >= 1.0 || getVisibleItemCount() == 0)
                {
                    openAnimation = 1;
                    state = State.NORMAL;
                }
                break;
            case CLOSING:
                openAnimation = 1 - (float) ((minecraft.world.getGameTime() + partialTicks - startAnimation) / OPEN_ANIMATION_LENGTH);
                if (openAnimation <= 0 || getVisibleItemCount() == 0)
                {
                    openAnimation = 0;
                    state = State.CLOSED;
                }
                break;
        }
        animProgress = openAnimation; // MathHelper.clamp(openAnimation, 0, 1);
    }

    private void drawTooltips(int mouseX, int mouseY)
    {
        Screen owner = host.getScreen();
        FontRenderer fontRenderer = host.getFontRenderer();
        ItemRenderer itemRenderer = host.getItemRenderer();
        for (int i = 0; i < visibleItems.size(); i++)
        {
            RadialMenuItem item = visibleItems.get(i);
            if (item.isHovered())
            {
                DrawingContext context = new DrawingContext(owner.width, owner.height, mouseX, mouseY, 0, fontRenderer, itemRenderer);
                item.drawTooltips(context);
            }
        }
    }

    private void drawItems(int x, int y, float z, int width, int height, FontRenderer font, ItemRenderer itemRenderer)
    {
        iterateVisible((item, s, e) -> {
            float middle = (s + e) * 0.5f;
            float posX = x + itemRadius * (float) Math.cos(middle);
            float posY = y + itemRadius * (float) Math.sin(middle);

            DrawingContext context = new DrawingContext(width, height, posX, posY, z, font, itemRenderer);
            item.draw(context);
        });
    }

    private void iterateVisible(TriConsumer<RadialMenuItem, Float, Float> consumer)
    {
        int numItems = visibleItems.size();
        for (int i = 0; i < numItems; i++)
        {
            float s = (float) getAngleFor(i - 0.5, numItems);
            float e = (float) getAngleFor(i + 0.5, numItems);

            RadialMenuItem item = visibleItems.get(i);
            consumer.accept(item, s, e);
        }
    }

    private void drawBackground(float x, float y, float z, float radiusIn, float radiusOut)
    {
        GlStateManager.disableAlphaTest();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        iterateVisible((item, s, e) -> {
            Vector4f color = item.isHovered() ? backgroundColorHover : backgroundColor;
            drawPieArc(buffer, x, y, z, radiusIn, radiusOut, s, e, (int) (color.x * 255), (int) (color.y * 255), (int) (color.z * 255), (int) (color.w * 255));
        });

        tessellator.draw();

        GlStateManager.enableTexture();
    }

    private static final float PRECISION = 2.5f / 360.0f;

    private void drawPieArc(BufferBuilder buffer, float x, float y, float z, float radiusIn, float radiusOut, float startAngle, float endAngle, int r, int g, int b, int a)
    {
        float angle = endAngle - startAngle;
        int sections = Math.max(1, MathHelper.ceil(angle / PRECISION));

        angle = endAngle - startAngle;

        for (int i = 0; i < sections; i++)
        {
            float angle1 = startAngle + (i / (float) sections) * angle;
            float angle2 = startAngle + ((i + 1) / (float) sections) * angle;

            float pos1InX = x + radiusIn * (float) Math.cos(angle1);
            float pos1InY = y + radiusIn * (float) Math.sin(angle1);
            float pos1OutX = x + radiusOut * (float) Math.cos(angle1);
            float pos1OutY = y + radiusOut * (float) Math.sin(angle1);
            float pos2OutX = x + radiusOut * (float) Math.cos(angle2);
            float pos2OutY = y + radiusOut * (float) Math.sin(angle2);
            float pos2InX = x + radiusIn * (float) Math.cos(angle2);
            float pos2InY = y + radiusIn * (float) Math.sin(angle2);

            buffer.pos(pos1OutX, pos1OutY, z).color(r, g, b, a).endVertex();
            buffer.pos(pos1InX, pos1InY, z).color(r, g, b, a).endVertex();
            buffer.pos(pos2InX, pos2InY, z).color(r, g, b, a).endVertex();
            buffer.pos(pos2OutX, pos2OutY, z).color(r, g, b, a).endVertex();
        }
    }

    public void cyclePrevious()
    {
        int numItems = getVisibleItemCount();
        int which = getHovered();
        which--;
        if (which < 0)
            which = numItems - 1;
        setHovered(which);

        moveMouseToItem(which, numItems);
    }

    public void cycleNext()
    {
        int numItems = getVisibleItemCount();
        int which = getHovered();
        if (which < 0)
            which = 0;
        else
        {
            which++;
            if (which >= numItems)
                which = 0;
        }
        moveMouseToItem(which, numItems);
        setHovered(which);
    }

    private void moveMouseToItem(int which, int numItems)
    {
        Screen owner = host.getScreen();
        int x = owner.width / 2;
        int y = owner.height / 2;
        float angle = (float) getAngleFor(which, numItems);
        setMousePosition(
                x + itemRadius * Math.cos(angle),
                y + itemRadius * Math.sin(angle)
        );
    }

    private void setMousePosition(double x, double y)
    {
        Screen owner = host.getScreen();
        GLFW.glfwSetCursorPos(minecraft.mainWindow.getHandle(), (int) (x * minecraft.mainWindow.getWidth() / owner.width), (int) (y * minecraft.mainWindow.getHeight() / owner.height));
    }

    private static final double TWO_PI = 2.0 * Math.PI;

    private void processMouse(int mouseX, int mouseY)
    {
        if (!isReady())
            return;

        int numItems = getVisibleItemCount();

        Screen owner = host.getScreen();
        int x = owner.width / 2;
        int y = owner.height / 2;
        double a = Math.atan2(mouseY - y, mouseX - x);
        double d = Math.sqrt(Math.pow(mouseX - x, 2) + Math.pow(mouseY - y, 2));
        if(numItems > 0)
        {
            double s0 = getAngleFor(0 - 0.5, numItems);
            double s1 = getAngleFor(numItems - 0.5, numItems);
            while (a < s0)
            {
                a += TWO_PI;
            }
            while (a >= s1)
            {
                a -= TWO_PI;
            }
        }

        int hovered = -1;
        for (int i = 0; i < numItems; i++)
        {
            float s = (float) getAngleFor(i - 0.5, numItems);
            float e = (float) getAngleFor(i + 0.5, numItems);

            if (a >= s && a < e && d >= radiusIn && (d < radiusOut || ConfigData.clipMouseToCircle || ConfigData.allowClickOutsideBounds))
            {
                hovered = i;
                break;
            }
        }
        setHovered(hovered);


        if (ConfigData.clipMouseToCircle)
        {
            int windowWidth = minecraft.mainWindow.getWidth();
            int windowHeight = minecraft.mainWindow.getHeight();

            double[] xPos = new double[1];
            double[] yPos = new double[1];
            GLFW.glfwGetCursorPos(minecraft.mainWindow.getHandle(), xPos, yPos);

            double scaledX = xPos[0] - (windowWidth / 2.0f);
            double scaledY = yPos[0] - (windowHeight / 2.0f);

            double distance = Math.sqrt(scaledX * scaledX + scaledY * scaledY);
            double radius = radiusOut * (windowWidth / (float) owner.width) * 0.975;

            if (distance > radius)
            {
                double fixedX = scaledX * radius / distance;
                double fixedY = scaledY * radius / distance;

                GLFW.glfwSetCursorPos(minecraft.mainWindow.getHandle(), (int) (windowWidth / 2 + fixedX), (int) (windowHeight / 2 + fixedY));
            }
        }
    }

    private double getAngleFor(double i, int numItems)
    {
        if(numItems == 0)
            return 0;
        double angle = ((i / numItems) + 0.25) * TWO_PI + Math.PI;
        return angle;
    }
}
