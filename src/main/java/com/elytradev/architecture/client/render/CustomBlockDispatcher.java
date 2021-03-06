package com.elytradev.architecture.client.render;

import com.elytradev.architecture.client.proxy.ClientProxy;
import com.elytradev.architecture.client.render.target.RenderTargetBaked;
import com.elytradev.architecture.client.render.target.RenderTargetWorld;
import com.elytradev.architecture.common.ArchitectureLog;
import com.elytradev.architecture.common.helpers.Trans3;
import com.elytradev.concrete.reflect.accessor.Accessor;
import com.elytradev.concrete.reflect.accessor.Accessors;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.MinecraftForgeClient;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;

public class CustomBlockDispatcher extends BlockRendererDispatcher {

    protected BlockRendererDispatcher base;
    private static boolean hasFailedRender = false;

    public CustomBlockDispatcher(BlockRendererDispatcher base) {
        super(base.getBlockModelShapes(), Minecraft.getMinecraft().getBlockColors());

        this.base = base;
    }

    public static void inject() {
        BlockRendererDispatcher baseDispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
        CustomBlockDispatcher customBlockDispatcher
                = new CustomBlockDispatcher(Minecraft.getMinecraft().getBlockRendererDispatcher());

        for (Field field : FieldUtils.getAllFields(BlockRendererDispatcher.class)) {
            try {
                field.setAccessible(true);
                FieldUtils.writeField(field, customBlockDispatcher, FieldUtils.readField(field, baseDispatcher));
            } catch (Exception e) {
                ArchitectureLog.error("Failed to transfer field from original render dispatcher to normal. {}", field);
            }
        }

        Accessor mcBlockRenderDispatcher = Accessors.findField(Minecraft.class,
                "blockRenderDispatcher", "field_175618_aM");

        mcBlockRenderDispatcher.set(Minecraft.getMinecraft(), customBlockDispatcher);
    }

    @Override
    public BlockModelShapes getBlockModelShapes() {
        return this.base.getBlockModelShapes();
    }

    @Override
    public BlockModelRenderer getBlockModelRenderer() {
        return this.base.getBlockModelRenderer();
    }

    @Override
    public IBakedModel getModelForState(IBlockState state) {
        return this.base.getModelForState(state);
    }

    @Override
    public void renderBlockBrightness(IBlockState state, float brightness) {
        this.base.renderBlockBrightness(state, brightness);
    }

    @Override
    public void renderBlockDamage(IBlockState state, BlockPos pos, TextureAtlasSprite icon, IBlockAccess world) {
        ICustomRenderer rend = ClientProxy.RENDERING_MANAGER.getCustomRenderer(world, pos, state);
        if (rend != null) {
            RenderTargetBaked target = new RenderTargetBaked(pos, icon);
            Trans3 t = Trans3.blockCenter;
            Block block = state.getBlock();
            for (BlockRenderLayer layer : BlockRenderLayer.values())
                if (block.canRenderInLayer(state, layer))
                    rend.renderBlock(world, pos, state, target, layer, t);
            IBakedModel model = target.getBakedModel();
            BufferBuilder tess = Tessellator.getInstance().getBuffer();
            this.getBlockModelRenderer().renderModel(world, model, state, pos, tess, false); //TODO chould checkSides be false?
        } else
            this.base.renderBlockDamage(state, pos, icon, world);
    }

    @Override
    public boolean renderBlock(IBlockState state, BlockPos pos, IBlockAccess world, BufferBuilder tess) {
        ICustomRenderer rend = ClientProxy.RENDERING_MANAGER.getCustomRenderer(world, pos, state);
        if (rend != null)
            return this.customRenderBlockToWorld(world, pos, state, tess, null, rend);
        else
            return this.base.renderBlock(state, pos, world, tess);
    }

    private boolean customRenderBlockToWorld(IBlockAccess world, BlockPos pos, IBlockState state, BufferBuilder tess,
                                             TextureAtlasSprite icon, ICustomRenderer rend) {
        try {
            RenderTargetWorld target = new RenderTargetWorld(world, pos, tess, icon);
            BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
            rend.renderBlock(world, pos, state, target, layer, Trans3.blockCenter(pos));
            return target.end();
        } catch (Exception e) {
            if (!hasFailedRender) {
                ArchitectureLog.error("ArchitectureCraft failed to render a custom block into the world, you will only see this message once.");
                ArchitectureLog.error("Please report the following information to the ArchitectureCraft github issues page.");
                ArchitectureLog.error("World={}\nPos={}\nState={}\nException={}", world, pos, state, e.getMessage());
                e.printStackTrace();

                hasFailedRender = true;
            }
            return false;
        }
    }

}
