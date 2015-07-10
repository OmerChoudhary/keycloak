package org.keycloak.testsuite.page.console;

import java.net.URI;
import org.jboss.arquillian.graphene.findby.FindByJQuery;
import org.keycloak.protocol.oidc.OIDCLoginProtocolService;
import org.openqa.selenium.WebElement;

/**
 *
 * @author tkyjovsk
 */
public class Realm extends RealmsRoot {

    public static final String REALM = "realm";
    public static final String MASTER = "master";
    public static final String DEMO = "demo";

    public Realm() {
        setTemplateValue(REALM, MASTER);
    }

    @Override
    public RealmsRoot setTemplateValues(String realm) {
        setTemplateValues(MASTER, realm);
        return this;
    }

    public RealmsRoot setTemplateValues(String consoleRealm, String realm) {
        setTemplateValue(CONSOLE_REALM, consoleRealm);
        setTemplateValue(REALM, realm);
        return this;
    }

    @Override
    public String getFragment() {
        return super.getFragment() + "/{" + REALM + "}";
    }

    @FindByJQuery("a:contains('Users')")
    private WebElement usersLink;

    public void clickUsers() {
        usersLink.click();
    }

    public URI getOIDCLoginUrl() {
        return OIDCLoginProtocolService.authUrl(getUriBuilder())
                .build(DEMO);
    }

}
