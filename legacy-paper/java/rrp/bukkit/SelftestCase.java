package rrp.bukkit;

/** Un cas de test d'intégration exécuté sur le vrai serveur par `/respublica selftest`. */
public interface SelftestCase {

    String name();

    /** Lève une exception (par exemple via {@link TestWorld#check}) pour signaler un échec. */
    void run(TestWorld world) throws Exception;
}
