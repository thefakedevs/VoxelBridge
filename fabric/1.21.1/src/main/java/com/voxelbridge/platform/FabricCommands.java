package com.voxelbridge.platform;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.voxelbridge.config.ExportRuntimeConfig;
import com.voxelbridge.core.util.color.ColorMode;
import com.voxelbridge.export.CoordinateMode;
import com.voxelbridge.export.ExportControl;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.util.client.RayCastUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Fabric-specific client command registration using FabricClientCommandSource.
 */
public final class FabricCommands {

    private FabricCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var root = ClientCommandManager.literal("voxelbridge");

        root.then(ClientCommandManager.literal("pos1")
                .executes(ctx -> {
                    var mc = ClientAccessHolder.get().getMinecraft();
                    BlockPos hit = RayCastUtil.getLookingAt(mc, 20.0);
                    if (hit == null) {
                        ctx.getSource().sendFeedback(Component.literal("§c[VoxelBridge] No block targeted."));
                        return 0;
                    }
                    ExportControl.setPos1(hit);
                    ctx.getSource()
                            .sendFeedback(Component.literal("§a[VoxelBridge] pos1 set to " + ExportControl.getPos1()));
                    return 1;
                })
                .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                        .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            int x = IntegerArgumentType.getInteger(ctx, "x");
                                            int y = IntegerArgumentType.getInteger(ctx, "y");
                                            int z = IntegerArgumentType.getInteger(ctx, "z");
                                            ExportControl.setPos1(new BlockPos(x, y, z));
                                            ctx.getSource().sendFeedback(Component
                                                    .literal("§a[VoxelBridge] pos1 set to " + ExportControl.getPos1()));
                                            return 1;
                                        })))));

        root.then(ClientCommandManager.literal("pos2")
                .executes(ctx -> {
                    var mc = ClientAccessHolder.get().getMinecraft();
                    BlockPos hit = RayCastUtil.getLookingAt(mc, 20.0);
                    if (hit == null) {
                        ctx.getSource().sendFeedback(Component.literal("§c[VoxelBridge] No block targeted."));
                        return 0;
                    }
                    ExportControl.setPos2(hit);
                    ctx.getSource()
                            .sendFeedback(Component.literal("§a[VoxelBridge] pos2 set to " + ExportControl.getPos2()));
                    return 1;
                })
                .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                        .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            int x = IntegerArgumentType.getInteger(ctx, "x");
                                            int y = IntegerArgumentType.getInteger(ctx, "y");
                                            int z = IntegerArgumentType.getInteger(ctx, "z");
                                            ExportControl.setPos2(new BlockPos(x, y, z));
                                            ctx.getSource().sendFeedback(Component
                                                    .literal("§a[VoxelBridge] pos2 set to " + ExportControl.getPos2()));
                                            return 1;
                                        })))));

        root.then(ClientCommandManager.literal("info").executes(ctx -> {
            ctx.getSource().sendFeedback(Component.literal("§6[VoxelBridge] Selection info:"));
            ctx.getSource().sendFeedback(Component
                    .literal("§e  pos1: §f" + (ExportControl.getPos1() != null ? ExportControl.getPos1() : "unset")));
            ctx.getSource().sendFeedback(Component
                    .literal("§e  pos2: §f" + (ExportControl.getPos2() != null ? ExportControl.getPos2() : "unset")));
            ctx.getSource().sendFeedback(
                    Component.literal("§e  Atlas mode: §f" + ExportRuntimeConfig.getAtlasMode().getDescription()));
            ctx.getSource().sendFeedback(Component.literal("§e  Coordinate mode: §f" +
                    (ExportRuntimeConfig.getCoordinateMode() == CoordinateMode.CENTERED ? "centered" : "world")));
            ctx.getSource().sendFeedback(
                    Component.literal("§e  Color mode: §f" + ExportRuntimeConfig.getColorMode().getDescription()));
            return 1;
        }));

        root.then(ClientCommandManager.literal("clear").executes(ctx -> {
            ExportControl.clearSelection();
            ctx.getSource().sendFeedback(Component.literal("§e[VoxelBridge] Selection cleared."));
            return 1;
        }));

        root.then(ClientCommandManager.literal("export").executes(ctx -> {
            var mc = ClientAccessHolder.get().getMinecraft();
            ExportControl.ExportResult result = ExportControl.startExport(mc.level);
            ctx.getSource().sendFeedback(Component.literal("§a[VoxelBridge] " + result.message()));
            return result.started() ? 1 : 0;
        }));

        root.then(ClientCommandManager.literal("atlas")
                .then(ClientCommandManager.literal("individual").executes(ctx -> {
                    ExportRuntimeConfig.setAtlasMode(ExportRuntimeConfig.AtlasMode.INDIVIDUAL);
                    ctx.getSource()
                            .sendFeedback(Component.literal("§a[VoxelBridge] Atlas mode -> Individual textures"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("atlas").executes(ctx -> {
                    ExportRuntimeConfig.setAtlasMode(ExportRuntimeConfig.AtlasMode.ATLAS);
                    ctx.getSource().sendFeedback(Component.literal("§a[VoxelBridge] Atlas mode -> Packed atlas"));
                    return 1;
                })));

        root.then(ClientCommandManager.literal("coords")
                .then(ClientCommandManager.literal("centered").executes(ctx -> {
                    ExportRuntimeConfig.setCoordinateMode(CoordinateMode.CENTERED);
                    ctx.getSource().sendFeedback(Component.literal("§a[VoxelBridge] Coordinate mode -> Centered"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("world").executes(ctx -> {
                    ExportRuntimeConfig.setCoordinateMode(CoordinateMode.WORLD_ORIGIN);
                    ctx.getSource().sendFeedback(Component.literal("§a[VoxelBridge] Coordinate mode -> World"));
                    return 1;
                })));

        root.then(ClientCommandManager.literal("colormode")
                .then(ClientCommandManager.literal("colormap").executes(ctx -> {
                    ExportRuntimeConfig.setColorMode(ColorMode.COLORMAP);
                    ctx.getSource().sendFeedback(Component.literal("§a[VoxelBridge] Color mode -> ColorMap"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("vertexcolor").executes(ctx -> {
                    ExportRuntimeConfig.setColorMode(ColorMode.VERTEX_COLOR);
                    ctx.getSource().sendFeedback(Component.literal("§a[VoxelBridge] Color mode -> Vertex Color"));
                    return 1;
                })));

        // Register the literal and create shortcut
        CommandNode<FabricClientCommandSource> rootNode = dispatcher.register(root);
        dispatcher.register(ClientCommandManager.literal("vb").redirect(rootNode));
    }
}
