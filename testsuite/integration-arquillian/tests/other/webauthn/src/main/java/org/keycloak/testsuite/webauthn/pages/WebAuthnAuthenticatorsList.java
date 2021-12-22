/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.testsuite.webauthn.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.keycloak.testsuite.util.UIUtils.getTextFromElement;

/**
 * Helper class for getting available authenticators on WebAuthnLogin page
 *
 * @author <a href="mailto:mabartos@redhat.com">Martin Bartos</a>
 */
public class WebAuthnAuthenticatorsList {

    @FindBy(id = "kc-webauthn-authenticator")
    private List<WebElement> authenticators;

    public List<WebAuthnAuthenticatorItem> getItems() {
        try {
            List<WebAuthnAuthenticatorItem> items = new ArrayList<>();
            for (WebElement auth : authenticators) {
                String name = getTextFromElement(auth.findElement(By.id("kc-webauthn-authenticator-label")));
                String createdAt = getTextFromElement(auth.findElement(By.id("kc-webauthn-authenticator-created")));
                items.add(new WebAuthnAuthenticatorItem(name, createdAt));
            }
            return items;
        } catch (NoSuchElementException e) {
            return Collections.emptyList();
        }
    }

    public int getCount() {
        try {
            return authenticators.size();
        } catch (NoSuchElementException e) {
            return 0;
        }
    }

    public List<String> getLabels() {
        try {
            return getItems().stream()
                    .filter(Objects::nonNull)
                    .map(WebAuthnAuthenticatorItem::getName)
                    .collect(Collectors.toList());
        } catch (NoSuchElementException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Get same DateFormat as in <code>org.keycloak.forms.login.freemarker.model.WebAuthnAuthenticatorsBean</code>
     */
    public static DateFormat getDateFormat(Locale locale) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale);
    }

    public static class WebAuthnAuthenticatorItem {
        private final String name;
        private final String createdAt;

        public WebAuthnAuthenticatorItem(String name, String createdAt) {
            this.name = name;
            this.createdAt = createdAt;
        }

        public String getName() {
            return name;
        }

        public String getCreatedDate() {
            return createdAt;
        }
    }
}
