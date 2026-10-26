package org.starset.dfsui.client;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * 数据生成入口。当前无自定义数据，仅占位以保持 Fabric 约定。
 */
public class DeltaForceStrikeClientUIDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        fabricDataGenerator.createPack();
    }
}
