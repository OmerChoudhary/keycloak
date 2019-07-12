/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.testsuite.pages.webauthn;

import org.jboss.arquillian.test.api.ArquillianResource;
import org.keycloak.testsuite.pages.LanguageComboboxAwarePage;
import org.keycloak.testsuite.util.OAuthClient;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import static org.keycloak.testsuite.util.UIUtils.clickLink;

public class LoginPage extends LanguageComboboxAwarePage {

    @ArquillianResource
    protected OAuthClient oauth;

    @FindBy(id = "username")
    private WebElement usernameInput;

    @FindBy(id = "password")
    private WebElement passwordInput;

    @FindBy(id = "totp")
    private WebElement totp;

    @FindBy(id = "rememberMe")
    private WebElement rememberMe;

    @FindBy(name = "login")
    private WebElement submitButton;

    @FindBy(name = "cancel")
    private WebElement cancelButton;

    @FindBy(linkText = "Register")
    private WebElement registerLink;

    @FindBy(linkText = "Forgot Password?")
    private WebElement resetPasswordLink;

    @FindBy(linkText = "Username")
    private WebElement recoverUsernameLink;

    @FindBy(className = "alert-error")
    private WebElement loginErrorMessage;

    @FindBy(className = "alert-warning")
    private WebElement loginWarningMessage;

    @FindBy(className = "alert-success")
    private WebElement loginSuccessMessage;


    @FindBy(className = "alert-info")
    private WebElement loginInfoMessage;

    @FindBy(className = "instruction")
    private WebElement instruction;


    public void login(String username, String password) {
        driver.findElement(By.id("username")).clear();
        driver.findElement(By.id("username")).sendKeys(username);

        driver.findElement(By.id("password")).clear();
        driver.findElement(By.id("password")).sendKeys(password);

        driver.findElement(By.name("login")).click();
        
        driver.findElement(By.cssSelector("input[type=\"button\"]")).click();
    }

    public void login(String password) {
        driver.findElement(By.id("password")).clear();
        driver.findElement(By.id("password")).sendKeys(password);

        driver.findElement(By.name("login")).click();
    }

    public void missingPassword(String username) {
        driver.findElement(By.id("username")).clear();
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).clear();
        driver.findElement(By.name("login")).click();

    }
    public void missingUsername() {
        driver.findElement(By.id("username")).clear();
        driver.findElement(By.name("login")).click();

    }

    public String getUsername() {
        return driver.findElement(By.id("username")).getAttribute("value");
    }

    public boolean isUsernameInputEnabled() {
        return driver.findElement(By.id("username")).isEnabled();
    }

    public String getPassword() {
        return driver.findElement(By.id("password")).getAttribute("value");
    }

    public void cancel() {
        driver.findElement(By.name("cancel")).click();
    }

    public String getError() {
        return loginErrorMessage != null ? loginErrorMessage.getText() : null;
    }

    public String getInstruction() {
        return instruction != null ? instruction.getText() : null;
    }

    public String getSuccessMessage() {
        return loginSuccessMessage != null ? loginSuccessMessage.getText() : null;
    }
    public String getInfoMessage() {
        return loginInfoMessage != null ? loginInfoMessage.getText() : null;
    }


    public boolean isCurrent() {
        String realm = "test";
        return isCurrent(realm);
    }

    public boolean isCurrent(String realm) {
        return driver.getTitle().equals("Log in to " + realm) || driver.getTitle().equals("Anmeldung bei " + realm);
    }

    public void clickRegister() {
        driver.findElement(By.linkText("Register")).click();
    }

    public void clickSocial(String providerId) {
        WebElement socialButton = findSocialButton(providerId);
        clickLink(socialButton);
    }

    public WebElement findSocialButton(String providerId) {
        String id = "zocial-" + providerId;
        return this.driver.findElement(By.id(id));
    }

    public void resetPassword() {
        resetPasswordLink.click();
    }

    public void recoverUsername() {
        recoverUsernameLink.click();
    }

    public void setRememberMe(boolean enable) {
        boolean current = rememberMe.isSelected();
        if (current != enable) {
            rememberMe.click();
        }
    }

    public boolean isRememberMeChecked() {
        return rememberMe.isSelected();
    }

    @Override
    public void open() {
        oauth.openLoginForm();
        assertCurrent();
    }

}
