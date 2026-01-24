package com.voxelbridge.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.voxelbridge.config.ExportRuntimeConfig;
import com.voxelbridge.core.util.color.ColorMode;
import com.voxelbridge.export.CoordinateMode;
import com.voxelbridge.export.ExportControl;
import com.voxelbridge.platform.client.ClientAccessHolder;
import com.voxelbridge.util.client.RayCastUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.CommandDispatcher;

/**
 * Client-side /voxelbridge command registration.
 * Handles selection management and export options.
 */
public final class VoxelBridgeCommands {

    private VoxelBridgeCommands() {}

    public static BlockPos getPos1() {
        return ExportControl.getPos1();
    }

    public static BlockPos getPos2() {
        return ExportControl.getPos2();
    }

    public static void setPos1(BlockPos pos) {
        ExportControl.setPos1(pos);
    }

    public static void setPos2(BlockPos pos) {
        ExportControl.setPos2(pos);
    }

    public static void clearSelection() {
        ExportControl.clearSelection();
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("voxelbridge");

        root.then(Commands.literal("pos1")
            .executes(ctx -> {
                var mc = ClientAccessHolder.get().getMinecraft();
                BlockPos hit = RayCastUtil.getLookingAt(mc, 20.0);
                if (hit == null) {
                    ctx.getSource().sendSystemMessage(Component.literal("c[VoxelBridge] No block targeted."));
                    return 0;
                }
                setPos1(hit);
                ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] pos1 set to " + getPos1().toShortString()));
                return 1;
            })
            .then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            int x = IntegerArgumentType.getInteger(ctx, "x");
                            int y = IntegerArgumentType.getInteger(ctx, "y");
                            int z = IntegerArgumentType.getInteger(ctx, "z");
                            setPos1(new BlockPos(x, y, z));
                            ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] pos1 set to " + getPos1().toShortString()));
                            return 1;
                        })))));

        root.then(Commands.literal("pos2")
            .executes(ctx -> {
                var mc = ClientAccessHolder.get().getMinecraft();
                BlockPos hit = RayCastUtil.getLookingAt(mc, 20.0);
                if (hit == null) {
                    ctx.getSource().sendSystemMessage(Component.literal("c[VoxelBridge] No block targeted."));
                    return 0;
                }
                setPos2(hit);
                ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] pos2 set to " + getPos2().toShortString()));
                return 1;
            })
            .then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            int x = IntegerArgumentType.getInteger(ctx, "x");
                            int y = IntegerArgumentType.getInteger(ctx, "y");
                            int z = IntegerArgumentType.getInteger(ctx, "z");
                            setPos2(new BlockPos(x, y, z));
                            ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] pos2 set to " + getPos2().toShortString()));
                            return 1;
                        })))));

        root.then(Commands.literal("info").executes(ctx -> {
            ctx.getSource().sendSystemMessage(Component.literal("6[VoxelBridge] Selection info:"));
                ctx.getSource().sendSystemMessage(Component.literal("e  pos1: f" + (getPos1() != null ? getPos1().toShortString() : "unset")));
                ctx.getSource().sendSystemMessage(Component.literal("e  pos2: f" + (getPos2() != null ? getPos2().toShortString() : "unset")));
            ctx.getSource().sendSystemMessage(Component.literal("e  Atlas mode: f" + ExportRuntimeConfig.getAtlasMode().getDescription()));
            ctx.getSource().sendSystemMessage(Component.literal("e  Atlas size: f" + ExportRuntimeConfig.getAtlasSize().getDescription()));
            ctx.getSource().sendSystemMessage(Component.literal("e  Atlas padding: f" + ExportRuntimeConfig.getAtlasPadding() + "px"));
            ctx.getSource().sendSystemMessage(Component.literal("e  Coordinate mode: f" +
                    (ExportRuntimeConfig.getCoordinateMode() == CoordinateMode.CENTERED ? "centered" : "world")));
            ctx.getSource().sendSystemMessage(Component.literal("e  Color mode: f" + ExportRuntimeConfig.getColorMode().getDescription()));
            ctx.getSource().sendSystemMessage(Component.literal("e  Vanilla random transform: f" +
                    (ExportRuntimeConfig.isVanillaRandomTransformEnabled() ? "on" : "off")));
            ctx.getSource().sendSystemMessage(Component.literal("e  Animation export: f" +
                    (ExportRuntimeConfig.isAnimationEnabled() ? "on" : "off")));
            ctx.getSource().sendSystemMessage(Component.literal("e  Fill cave (dark cave_air): f" +
                    (ExportRuntimeConfig.isFillCaveEnabled() ? "on" : "off")));
            ctx.getSource().sendSystemMessage(Component.literal("e  LabPBR decode: f" +
                    (ExportRuntimeConfig.isPbrDecodeEnabled() ? "on" : "off")));
            ctx.getSource().sendSystemMessage(Component.literal("e  Export threads: f" + ExportRuntimeConfig.getExportThreadCount()));
            return 1;
        }));

        root.then(Commands.literal("clear").executes(ctx -> {
            clearSelection();
            ctx.getSource().sendSystemMessage(Component.literal("e[VoxelBridge] Selection cleared."));
            return 1;
        }));

        root.then(Commands.literal("atlas")
                .executes(ctx -> {
                    ctx.getSource().sendSystemMessage(Component.literal("e[VoxelBridge] Current atlas mode: f" + ExportRuntimeConfig.getAtlasMode().getDescription()));
                    ctx.getSource().sendSystemMessage(Component.literal("7   individual: one texture per sprite"));
                    ctx.getSource().sendSystemMessage(Component.literal("7   atlas: pack into 8192 UDIM tiles"));
                    return 1;
                })
                .then(Commands.literal("individual").executes(ctx -> {
                    ExportRuntimeConfig.setAtlasMode(ExportRuntimeConfig.AtlasMode.INDIVIDUAL);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Atlas mode -> Individual textures"));
                    return 1;
                }))
                .then(Commands.literal("atlas").executes(ctx -> {
                    ExportRuntimeConfig.setAtlasMode(ExportRuntimeConfig.AtlasMode.ATLAS);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Atlas mode -> Packed atlas (UDIM 8192)"));
                    return 1;
                }))
        );

        root.then(Commands.literal("animation")
                .executes(ctx -> {
                    ctx.getSource().sendSystemMessage(Component.literal("6[VoxelBridge] Animation export is currently f"
                            + (ExportRuntimeConfig.isAnimationEnabled() ? "on" : "off")));
                    ctx.getSource().sendSystemMessage(Component.literal("7   Usage: /voxelbridge animation <on|off>"));
                    return 1;
                })
                .then(Commands.literal("on").executes(ctx -> {
                    ExportRuntimeConfig.setAnimationEnabled(true);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Animation export -> ON"));
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ExportRuntimeConfig.setAnimationEnabled(false);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Animation export -> OFF"));
                    return 1;
                }))
        );

        root.then(Commands.literal("fillcave")
                .executes(ctx -> {
                    ctx.getSource().sendSystemMessage(Component.literal("6[VoxelBridge] Fill cave is currently f"
                            + (ExportRuntimeConfig.isFillCaveEnabled() ? "on" : "off")));
                    ctx.getSource().sendSystemMessage(Component.literal("7   Usage: /voxelbridge fillcave <on|off>"));
                    ctx.getSource().sendSystemMessage(Component.literal("7   on : Treat dark cave_air (skylight=0) as solid for culling"));
                    ctx.getSource().sendSystemMessage(Component.literal("7   off: Normal culling behavior"));
                    return 1;
                })
                .then(Commands.literal("on").executes(ctx -> {
                    ExportRuntimeConfig.setFillCaveEnabled(true);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Fill cave -> ON (dark caves will be culled)"));
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ExportRuntimeConfig.setFillCaveEnabled(false);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Fill cave -> OFF"));
                    return 1;
                }))
        );

        root.then(Commands.literal("atlassize")
                .executes(ctx -> {
                    ExportRuntimeConfig.AtlasSize current = ExportRuntimeConfig.getAtlasSize();
                    ctx.getSource().sendSystemMessage(Component.literal("6[VoxelBridge] Current atlas size: f" + current.getDescription()));
                    ctx.getSource().sendSystemMessage(Component.literal("7   Available sizes:"));
                    for (ExportRuntimeConfig.AtlasSize size : ExportRuntimeConfig.AtlasSize.values()) {
                        String marker = size == current ? "a> " : "7  ";
                        ctx.getSource().sendSystemMessage(Component.literal(marker + size.getSize() + ": " + size.getDescription()));
                    }
                    ctx.getSource().sendSystemMessage(Component.literal("7   Usage: /voxelbridge atlassize <size>"));
                    return 1;
                })
                .then(Commands.literal("128").executes(ctx -> {
                    ExportRuntimeConfig.setAtlasSize(ExportRuntimeConfig.AtlasSize.SIZE_128);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Atlas size -> 128x128"));
                    return 1;
                }))
                .then(Commands.literal("256").executes(ctx -> {
                    ExportRuntimeConfig.setAtlasSize(ExportRuntimeConfig.AtlasSize.SIZE_256);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Atlas size -> 256x256"));
                    return 1;
                }))
                .then(Commands.literal("512").executes(ctx -> {
                    ExportRuntimeConfig.setAtlasSize(ExportRuntimeConfig.AtlasSize.SIZE_512);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Atlas size -> 512x512"));
                    return 1;
                }))
                .then(Commands.literal("1024").executes(ctx -> {
                    ExportRuntimeConfig.setAtlasSize(ExportRuntimeConfig.AtlasSize.SIZE_1024);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Atlas size -> 1024x1024"));
                    return 1;
                }))
                .then(Commands.literal("2048").executes(ctx -> {
                    ExportRuntimeConfig.setAtlasSize(ExportRuntimeConfig.AtlasSize.SIZE_2048);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Atlas size -> 2048x2048"));
                    return 1;
                }))
                .then(Commands.literal("4096").executes(ctx -> {
                    ExportRuntimeConfig.setAtlasSize(ExportRuntimeConfig.AtlasSize.SIZE_4096);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Atlas size -> 4096x4096"));
                    return 1;
                }))
                .then(Commands.literal("8192").executes(ctx -> {
                    ExportRuntimeConfig.setAtlasSize(ExportRuntimeConfig.AtlasSize.SIZE_8192);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Atlas size -> 8192x8192"));
                    return 1;
                }))
        );

        root.then(Commands.literal("atlaspad")
                .executes(ctx -> {
                    int current = ExportRuntimeConfig.getAtlasPadding();
                    ctx.getSource().sendSystemMessage(Component.literal("6[VoxelBridge] Current atlas padding: f" + current + "px"));
                    ctx.getSource().sendSystemMessage(Component.literal("7   Allowed values: 0, 4, 8, 12, 16"));
                    ctx.getSource().sendSystemMessage(Component.literal("7   Usage: /voxelbridge atlaspad <pixels>"));
                    return 1;
                })
                .then(Commands.argument("pixels", IntegerArgumentType.integer(0, 64)).executes(ctx -> {
                    int pixels = IntegerArgumentType.getInteger(ctx, "pixels");
                    if (!ExportRuntimeConfig.setAtlasPadding(pixels)) {
                        ctx.getSource().sendSystemMessage(Component.literal("c[VoxelBridge] Invalid padding. Allowed: 0, 4, 8, 12, 16"));
                        return 0;
                    }
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Atlas padding -> " + pixels + "px"));
                    return 1;
                }))
        );

        root.then(Commands.literal("coords")
                .executes(ctx -> {
                    String mode = ExportRuntimeConfig.getCoordinateMode() == CoordinateMode.CENTERED ? "centered" : "world";
                    ctx.getSource().sendSystemMessage(Component.literal("6[VoxelBridge] Coordinate mode is currently f" + mode));
                    ctx.getSource().sendSystemMessage(Component.literal("7   centered: model centered at origin (default)"));
                    ctx.getSource().sendSystemMessage(Component.literal("7   world: preserve original world coordinates"));
                    return 1;
                })
                .then(Commands.literal("centered").executes(ctx -> {
                    ExportRuntimeConfig.setCoordinateMode(CoordinateMode.CENTERED);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Coordinate mode -> Centered (model at origin)"));
                    return 1;
                }))
                .then(Commands.literal("world").executes(ctx -> {
                    ExportRuntimeConfig.setCoordinateMode(CoordinateMode.WORLD_ORIGIN);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Coordinate mode -> World (preserve coordinates)"));
                    return 1;
                }))
        );

        root.then(Commands.literal("poshash")
                .executes(ctx -> {
                    ctx.getSource().sendSystemMessage(Component.literal("6[VoxelBridge] Vanilla random transform is currently f"
                            + (ExportRuntimeConfig.isVanillaRandomTransformEnabled() ? "on" : "off")));
                    ctx.getSource().sendSystemMessage(Component.literal("7   Usage: /voxelbridge poshash <on|off>"));
                    ctx.getSource().sendSystemMessage(Component.literal("7   on : Apply vanilla position-hash random offsets/variants"));
                    ctx.getSource().sendSystemMessage(Component.literal("7   off: Disable offsets and keep legacy behavior"));
                    return 1;
                })
                .then(Commands.literal("on").executes(ctx -> {
                    ExportRuntimeConfig.setVanillaRandomTransformEnabled(true);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Vanilla random transform -> ON"));
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ExportRuntimeConfig.setVanillaRandomTransformEnabled(false);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Vanilla random transform -> OFF"));
                    return 1;
                }))
        );

        root.then(Commands.literal("colormode")
                .executes(ctx -> {
                    ColorMode current = ExportRuntimeConfig.getColorMode();
                    ctx.getSource().sendSystemMessage(Component.literal("6[VoxelBridge] Current color mode: f" + current.getDescription()));
                    ctx.getSource().sendSystemMessage(Component.literal("7   colormap: TEXCOORD_1 + colormap texture (default)"));
                    ctx.getSource().sendSystemMessage(Component.literal("7   vertexcolor: COLOR_0 vertex attribute"));
                    return 1;
                })
                .then(Commands.literal("colormap").executes(ctx -> {
                    ExportRuntimeConfig.setColorMode(ColorMode.COLORMAP);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Color mode -> ColorMap"));
                    return 1;
                }))
                .then(Commands.literal("vertexcolor").executes(ctx -> {
                    ExportRuntimeConfig.setColorMode(ColorMode.VERTEX_COLOR);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Color mode -> Vertex Color"));
                    return 1;
                }))
        );

        root.then(Commands.literal("threads")
                .executes(ctx -> {
                    int threads = ExportRuntimeConfig.getExportThreadCount();
                    int cpuCores = Runtime.getRuntime().availableProcessors();
                    ctx.getSource().sendSystemMessage(Component.literal("6[VoxelBridge] Export thread count: f" + threads + "7 (CPU cores: " + cpuCores + ")"));
                    ctx.getSource().sendSystemMessage(Component.literal("7   Usage: /voxelbridge threads <count> (1-32)"));
                    return 1;
                })
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 32)).executes(ctx -> {
                    int count = IntegerArgumentType.getInteger(ctx, "count");
                    ExportRuntimeConfig.setExportThreadCount(count);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] Export threads -> " + count));
                    return 1;
                }))
        );

        root.then(Commands.literal("pbrdecode")
                .executes(ctx -> {
                    ctx.getSource().sendSystemMessage(Component.literal("6[VoxelBridge] LabPBR decode is currently f"
                            + (ExportRuntimeConfig.isPbrDecodeEnabled() ? "on" : "off")));
                    ctx.getSource().sendSystemMessage(Component.literal("7   Usage: /voxelbridge pbrdecode <on|off>"));
                    return 1;
                })
                .then(Commands.literal("on").executes(ctx -> {
                    ExportRuntimeConfig.setPbrDecodeEnabled(true);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] LabPBR decode -> ON"));
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ExportRuntimeConfig.setPbrDecodeEnabled(false);
                    ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] LabPBR decode -> OFF"));
                    return 1;
                }))
        );

        root.then(Commands.literal("export").executes(ctx -> {
            var mc = ClientAccessHolder.get().getMinecraft();
            ExportControl.ExportResult result = ExportControl.startExport(mc.level);
            ctx.getSource().sendSystemMessage(Component.literal("a[VoxelBridge] " + result.message()));
            return result.started() ? 1 : 0;
        }));

        // Register the literal once and reuse the returned node for the "vb" shortcut.
        CommandNode<CommandSourceStack> rootNode = dispatcher.register(root);
        dispatcher.register(Commands.literal("vb").redirect(rootNode));
    }
}
