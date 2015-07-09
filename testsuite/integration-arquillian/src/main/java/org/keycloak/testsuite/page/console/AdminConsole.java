/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.testsuite.page.console;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.UriBuilder;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 *
 * @author Petr Mensik
 */
public class AdminConsole extends AdminRoot {

    public AdminConsole() {
        setTemplateValue("consoleRealm", Realm.MASTER);
    }

    @Override
    public UriBuilder createUriBuilder() {
        return super.createUriBuilder().path("{consoleRealm}/console");
    }

    @FindBy(css = ".btn-danger")
    protected WebElement dangerButton;

    //@FindByJQuery(".btn-primary:visible")
    @FindBy(css = ".btn-primary")
    protected WebElement primaryButton;

    @FindBy(css = ".btn-primary")
    protected List<WebElement> primaryButtons;

    @FindBy(css = ".ng-binding.btn.btn-danger")
    protected WebElement deleteConfirmationButton;

    public URI getUrlString(String consoleRealm) {
        Map<String,Object> tpl = new HashMap<>();
        tpl.put("consoleRealm", consoleRealm);
        return getUri(tpl);
    }

}
