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
package org.keycloak.testsuite.account.page;

import org.keycloak.testsuite.model.Account;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 *
 * @author Petr Mensik
 */
public class AccountSection extends AccountPage {

    @FindBy(id = "username")
    private WebElement username;

    @FindBy(id = "email")
    private WebElement email;

    @FindBy(id = "lastName")
    private WebElement lastName;

    @FindBy(id = "firstName")
    private WebElement firstName;

    public Account getAccount() {
        return new Account(
                username.getAttribute("value"),
                email.getAttribute("value"),
                lastName.getAttribute("value"),
                firstName.getAttribute("value"));
    }

    public void setAccount(Account account) {
        email.clear();
        email.sendKeys(account.getEmail());
        lastName.clear();
        lastName.sendKeys(account.getLastName());
        firstName.clear();
        firstName.sendKeys(account.getFirstName());
    }

}
