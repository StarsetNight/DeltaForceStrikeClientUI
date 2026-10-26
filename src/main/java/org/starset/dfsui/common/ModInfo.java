package org.starset.dfsui.common;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 集中管理的 MOD 常量。
 * <p>{@link #MOD_ID} 是 Fabric 暴露给用户的 mod id，不可随意更改；
 * {@link #version()} 从 fabric.mod.json 展开后的元数据读取，避免在多处硬编码版本号。</p>
 */
public final class ModInfo {

    public static final String MOD_ID = "deltaforcestrikeclientui";

    private ModInfo() {
    }

    /**
     * 读取运行时 MOD 版本（fabric.mod.json 中 {@code ${version}} 展开后的值，如 "1.3.0"）。
     */
    public static String version() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
