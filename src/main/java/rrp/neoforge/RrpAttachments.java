package rrp.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Données attachées aux villageois (rôle, jauges, camp) : enregistrées avec l'entité dans la sauvegarde du monde. */
public final class RrpAttachments {

    /** Contenu persistant d'un villageois ; {@code named} : le nom affiché est le nôtre, pas celui d'un joueur. */
    public record VillagerData(String role, double morale, double satiety, double loyalty, String faction, boolean named) {
        public static final Codec<VillagerData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("role").forGetter(VillagerData::role),
                Codec.DOUBLE.fieldOf("morale").forGetter(VillagerData::morale),
                Codec.DOUBLE.fieldOf("satiety").forGetter(VillagerData::satiety),
                Codec.DOUBLE.fieldOf("loyalty").forGetter(VillagerData::loyalty),
                Codec.STRING.fieldOf("faction").forGetter(VillagerData::faction),
                Codec.BOOL.optionalFieldOf("named", false).forGetter(VillagerData::named)
        ).apply(i, VillagerData::new));
    }

    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, RrpMod.MOD_ID);

    public static final Supplier<AttachmentType<VillagerData>> VILLAGER = REGISTER.register("villager",
            () -> AttachmentType.builder(() -> new VillagerData("", 70, 80, 70, "", false)).serialize(VillagerData.CODEC).build());

    private RrpAttachments() {
    }
}
