package xyz.nucleoid.plasmid.game.config;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import fr.catcore.server.translations.api.ServerTranslations;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.codecs.MoreCodecs;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.game.GameType;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public record GameConfig<C>(
        @Nullable Identifier source,
        GameType<C> type,
        @Nullable Text name,
        CustomValuesConfig custom,
        C config
) {
    public static final Codec<GameConfig<?>> CODEC = codecFrom(null);

    public static Codec<GameConfig<?>> codecFrom(Identifier source) {
        return new ConfigCodec(source).codec();
    }

    public GameOpenProcedure openProcedure(MinecraftServer server) {
        var context = new GameOpenContext<C>(server, this);
        return this.type.open(context);
    }

    /**
     * @return the source location that this config was loaded from, if loaded from a file.
     */
    @Override
    @Nullable
    public Identifier source() {
        return this.source;
    }

    /**
     * @return the name for this game config, defaulted to the game type name if none is specified
     */
    @Override
    public Text name() {
        var name = this.name;
        if (name != null) {
            return name;
        }

        var translationKey = this.translationKey();
        if (translationKey != null && hasTranslationFor(translationKey)) {
            return new TranslatableText(translationKey);
        }

        return this.type.name();
    }

    private static boolean hasTranslationFor(String translationKey) {
        return ServerTranslations.INSTANCE.getDefaultLanguage().local().contains(translationKey);
    }

    @Nullable
    public String translationKey() {
        if (this.source != null) {
            return Util.createTranslationKey("game", this.source);
        } else {
            return null;
        }
    }

    static final class ConfigCodec extends MapCodec<GameConfig<?>> {
        private final Identifier source;

        ConfigCodec(@Nullable Identifier source) {
            this.source = source;
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of(
                    ops.createString("type"),
                    ops.createString("name"),
                    ops.createString("config")
            );
        }

        @Override
        public <T> DataResult<GameConfig<?>> decode(DynamicOps<T> ops, MapLike<T> input) {
            var typeResult = GameType.REGISTRY.decode(ops, input.get("type")).map(Pair::getFirst);

            return typeResult.flatMap(type -> {
                var name = tryDecode(MoreCodecs.TEXT, ops, input.get("name")).orElse(null);
                var custom = tryDecode(CustomValuesConfig.CODEC, ops, input.get("custom"))
                        .orElse(CustomValuesConfig.empty());

                var configCodec = type.configCodec();
                return this.decodeConfig(ops, input, configCodec).map(config -> {
                    return this.createConfigUnchecked(type, name, custom, config);
                });
            });
        }

        private <T> DataResult<?> decodeConfig(DynamicOps<T> ops, MapLike<T> input, Codec<?> configCodec) {
            if (configCodec instanceof MapCodecCodec<?> mapCodec) {
                return mapCodec.codec().decode(ops, input).map(Function.identity());
            } else {
                return configCodec.decode(ops, input.get("config")).map(Pair::getFirst);
            }
        }

        private static <T, A> Optional<A> tryDecode(Codec<A> codec, DynamicOps<T> ops, T input) {
            return codec.decode(ops, input).result().map(Pair::getFirst);
        }

        @SuppressWarnings("unchecked")
        private <C> GameConfig<C> createConfigUnchecked(GameType<C> type, @Nullable Text name, CustomValuesConfig custom, Object config) {
            return new GameConfig<>(this.source, type, name, custom, (C) config);
        }

        @Override
        public <T> RecordBuilder<T> encode(GameConfig<?> game, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return this.encodeUnchecked(game, ops, prefix);
        }

        private <T, C> RecordBuilder<T> encodeUnchecked(GameConfig<C> game, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            var codec = game.type.configCodec();
            if (codec instanceof MapCodecCodec<C> mapCodec) {
                prefix = mapCodec.codec().encode(game.config, ops, prefix);
            } else {
                prefix.add("config", codec.encodeStart(ops, game.config));
            }

            prefix.add("type", GameType.REGISTRY.encodeStart(ops, game.type));

            if (game.name != null) {
                prefix.add("name", MoreCodecs.TEXT.encodeStart(ops, game.name));
            }

            if (!game.custom.isEmpty()) {
                prefix.add("custom", CustomValuesConfig.CODEC.encodeStart(ops, game.custom));
            }

            return prefix;
        }
    }
}
