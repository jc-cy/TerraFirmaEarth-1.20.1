package com.newterraearth.tfe.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

import com.newterraearth.tfe.common.NTELogPileHelpers;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS;

/**
 * Log pile renderer that picks one of sixteen baked sub-models from the block state at draw time, instead of
 * pointing every {@code count} value at a separate top-level model or block state variant.
 *
 * <p>This model is bound to {@code tfc:block/log_pile} and {@code tfc:block/burning_log_pile}, i.e. the block models
 * TFC's own block state points at. It deliberately does <em>not</em> live behind a block state override: a mod
 * resource pack's block state override is resolved from the opposite end of the resource stack by ModernFix's
 * {@code dynamic_resources}, so it cannot be relied on (see the log pile task notes). One {@link BakedModel} instance
 * serves every block state, so model caches (e.g. ModernFix's {@code dynamic_resources}) cannot collapse the variants
 * into a single frozen model. The {@code count} property stays in the block state so that changing it still dirties
 * the chunk mesh and keeps the collision shape in sync.</p>
 */
public class NTELogPileBlockModel implements IDynamicBakedModel, IUnbakedGeometry<NTELogPileBlockModel>
{
    private final List<BlockModel> unbaked;
    private final List<BakedModel> alongZ;
    private final List<BakedModel> alongX;

    public NTELogPileBlockModel(List<BlockModel> unbaked)
    {
        this.unbaked = unbaked;
        this.alongZ = new ArrayList<>(unbaked.size());
        this.alongX = new ArrayList<>(unbaked.size());
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> atlas, ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation)
    {
        alongZ.clear();
        alongX.clear();
        for (BlockModel model : unbaked)
        {
            // Bake our own canonical orientations and ignore the incoming ModelState. Which blockstate definition ends
            // up in effect is not stable: TFE's file (axis=z -> no rotation, axis=x -> y 90) and TFC's file (axis=z ->
            // x 90, axis=x -> y 90 + x 90) disagree, and ModernFix's dynamic_resources picks the opposite end of the
            // resource stack, so the block's rotation differs between hosts. Selecting the orientation from the
            // BlockState itself keeps the appearance identical everywhere.
            alongZ.add(model.bake(baker, model, atlas, BlockModelRotation.X0_Y0, modelLocation, false));
            alongX.add(model.bake(baker, model, atlas, BlockModelRotation.X0_Y90, modelLocation, false));
        }
        return this;
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context)
    {
        unbaked.forEach(model -> model.resolveParents(modelGetter));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random, ModelData modelData, @Nullable RenderType renderType)
    {
        final BakedModel model = select(state);
        return model.getQuads(state, direction, random, modelData, renderType);
    }

    private BakedModel select(@Nullable BlockState state)
    {
        int count = NTELogPileHelpers.MAX_LOGS;
        boolean alongX = false;
        if (state != null)
        {
            count = NTELogPileHelpers.getVisibleLogCount(state);
            alongX = state.hasProperty(HORIZONTAL_AXIS) && state.getValue(HORIZONTAL_AXIS) == Direction.Axis.X;
        }
        final List<BakedModel> models = alongX ? this.alongX : this.alongZ;
        if (models.isEmpty())
        {
            throw new IllegalStateException("Log pile model was requested before it finished baking");
        }
        return models.get(Mth.clamp(count, 1, NTELogPileHelpers.MAX_LOGS) - 1);
    }

    @Override
    public boolean useAmbientOcclusion()
    {
        return true;
    }

    @Override
    public boolean isGui3d()
    {
        return true;
    }

    @Override
    public boolean usesBlockLight()
    {
        return true;
    }

    @Override
    public boolean isCustomRenderer()
    {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public TextureAtlasSprite getParticleIcon()
    {
        return select(null).getParticleIcon();
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data)
    {
        return select(null).getParticleIcon(data);
    }

    @Override
    public ItemOverrides getOverrides()
    {
        return ItemOverrides.EMPTY;
    }

    public static class Loader implements IGeometryLoader<NTELogPileBlockModel>
    {
        public static final Loader INSTANCE = new Loader();

        private Loader() {}

        @Override
        public NTELogPileBlockModel read(JsonObject json, JsonDeserializationContext context) throws JsonParseException
        {
            final JsonArray array = GsonHelper.getAsJsonArray(json, "models");
            final List<BlockModel> models = new ArrayList<>(array.size());
            for (JsonElement element : array)
            {
                models.add(context.deserialize(element, BlockModel.class));
            }
            if (models.isEmpty())
            {
                throw new JsonParseException("Log pile model requires at least one sub-model");
            }
            return new NTELogPileBlockModel(models);
        }
    }
}
