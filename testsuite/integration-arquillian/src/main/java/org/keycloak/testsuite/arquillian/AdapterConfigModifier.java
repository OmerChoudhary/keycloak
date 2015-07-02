package org.keycloak.testsuite.arquillian;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tools.ant.DirectoryScanner;
import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.keycloak.representations.adapters.config.BaseAdapterConfig;
import static org.keycloak.testsuite.arquillian.ContainersManager.getAdapterLibsLocationProperty;
import static org.keycloak.testsuite.arquillian.ContainersManager.hasAppServerContainerAnnotation;
import static org.keycloak.testsuite.arquillian.ContainersManager.isRelative;
import org.keycloak.testsuite.util.AdapterType;
import static org.keycloak.testsuite.util.Json.loadJson;
import org.keycloak.util.JsonSerialization;

/**
 *
 * @author tkyjovsk
 */
public class AdapterConfigModifier implements ApplicationArchiveProcessor {

    public static final String REALM_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCrVrCuTtArbgaZzL1hvh0xtL5mc7o0NqPVnYXkLvgcwiC3BjLGw1tGEGoJaXDuSaRllobm53JBhjx33UNv+5z/UMG4kytBWxheNVKnL6GgqlNabMaFfPLPCF8kAgKnsi79NMo+n6KnSY8YeUmec/p2vjO2NjsSAVcWEQMVhJ31LwIDAQAB";

    private static final Logger log = Logger.getLogger(AdapterConfigModifier.class.getName());

    public static final String ADAPTER_CONFIG_PATH = "/WEB-INF/keycloak.json";

    @Override
    public void process(Archive<?> archive, TestClass testClass) {
        if (isAdapterTest(testClass)) {
            modifyAdapterConfig(archive, testClass);
            attachKeycloakLibs(archive, testClass);
        } else {
            System.out.println(testClass.getJavaClass().getSimpleName() + " is not an AdapterTest");
        }
    }

    public static boolean isAdapterTest(TestClass testClass) {
        return hasAppServerContainerAnnotation(testClass.getJavaClass());
    }

    protected void modifyAdapterConfig(Archive<?> archive, TestClass testClass) {
        System.out.println("Modifying adapter config for " + archive.getName());
        try {
            BaseAdapterConfig adapterConfig = loadJson(archive.get(ADAPTER_CONFIG_PATH)
                    .getAsset().openStream(), BaseAdapterConfig.class);

            boolean relative = isRelative(testClass.getJavaClass());
            System.out.println(" setting " + (relative ? "" : "non-") + "relative auth-server-url");
            if (relative) {
                adapterConfig.setAuthServerUrl("/auth");
//                ac.setRealmKey(null); // TODO verify if realm key is required for relative scneario
            } else {
                adapterConfig.setAuthServerUrl(URLProvider.getAuthServerContextRoot() + "/auth");
                adapterConfig.setRealmKey(REALM_KEY);
            }

            archive.add(new StringAsset(JsonSerialization.writeValueAsPrettyString(adapterConfig)),
                    ADAPTER_CONFIG_PATH);

        } catch (IOException ex) {
            log.log(Level.SEVERE, "Cannot serialize adapter config to JSON.", ex);
        }
    }

    protected void attachKeycloakLibs(Archive<?> archive, TestClass testClass) {
        AdapterType adapterType = AdapterType.getByType(System.getProperty("adapter.type",
                AdapterType.PROVIDED.getType()));
        System.out.println("Adapter type: " + adapterType);
        if (adapterType.equals(AdapterType.BUNDLED)) {
            System.out.println("Attaching keycloak adapter libs to " + archive.getName());

            String libsLocationProperty = getAdapterLibsLocationProperty(testClass.getJavaClass());
            assert libsLocationProperty != null;
            File libsLocation = new File(System.getProperty(libsLocationProperty));
            assert libsLocation.exists();
            System.out.println("Libs location: " + libsLocation.getPath());

            WebArchive war = (WebArchive) archive;

            for (File lib : getAdapterLibs(libsLocation)) {
                System.out.println(" attaching: " + lib.getName());
                war.addAsLibrary(lib);
            }
        } else {
            System.out.println("Expecting keycloak adapter libs to be provided by the server.");
        }
    }

    DirectoryScanner scanner = new DirectoryScanner();

    protected List<File> getAdapterLibs(File adapterLibsLocation) {
        assert adapterLibsLocation.exists();
        List<File> libs = new ArrayList<>();
        scanner.setBasedir(adapterLibsLocation);
        scanner.setIncludes(new String[]{"**/*jar"});
        scanner.scan();
        for (String lib : scanner.getIncludedFiles()) {
            libs.add(new File(adapterLibsLocation, lib));
        }
        return libs;
    }

}
