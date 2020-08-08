package fr.catcore.deacoudre.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class DeACoudreMapConfig {

    public static final Codec<DeACoudreMapConfig> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Codec.INT.fieldOf("radius").forGetter(map -> map.radius),
                Codec.INT.fieldOf("height").forGetter(map -> map.height)
        ).apply(instance, DeACoudreMapConfig::new);
    });

    public final int height;
    public final int radius;

    public DeACoudreMapConfig(int radius, int height) {
        this.height = height + 1;
        this.radius = radius;
    }

    public int getHeight() {
        return height;
    }

    public int getRadius() {
        return radius;
    }
}
