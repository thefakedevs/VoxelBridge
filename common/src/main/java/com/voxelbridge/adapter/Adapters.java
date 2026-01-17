package com.voxelbridge.adapter;

public final class Adapters {
    private static WorldAdapter worldAdapter;
    private static RenderAdapter renderAdapter;
    private static EntityRenderBridge entityRenderBridge;
    private static BlockEntityRenderBridge blockEntityRenderBridge;
    private static SelectionRenderBridge selectionRenderBridge;

    private Adapters() {}

    public static void init(WorldAdapter worldAdapterImpl,
                            RenderAdapter renderAdapterImpl,
                            EntityRenderBridge entityRenderBridgeImpl,
                            BlockEntityRenderBridge blockEntityRenderBridgeImpl,
                            SelectionRenderBridge selectionRenderBridgeImpl) {
        worldAdapter = worldAdapterImpl;
        renderAdapter = renderAdapterImpl;
        entityRenderBridge = entityRenderBridgeImpl;
        blockEntityRenderBridge = blockEntityRenderBridgeImpl;
        selectionRenderBridge = selectionRenderBridgeImpl;
    }

    public static WorldAdapter getWorld() {
        if (worldAdapter == null) {
            throw new IllegalStateException("WorldAdapter not initialized!");
        }
        return worldAdapter;
    }

    public static RenderAdapter getRender() {
        if (renderAdapter == null) {
            throw new IllegalStateException("RenderAdapter not initialized!");
        }
        return renderAdapter;
    }

    public static EntityRenderBridge getEntityRender() {
        if (entityRenderBridge == null) {
            throw new IllegalStateException("EntityRenderBridge not initialized!");
        }
        return entityRenderBridge;
    }

    public static BlockEntityRenderBridge getBlockEntityRender() {
        if (blockEntityRenderBridge == null) {
            throw new IllegalStateException("BlockEntityRenderBridge not initialized!");
        }
        return blockEntityRenderBridge;
    }

    public static SelectionRenderBridge getSelectionRender() {
        if (selectionRenderBridge == null) {
            throw new IllegalStateException("SelectionRenderBridge not initialized!");
        }
        return selectionRenderBridge;
    }
}
