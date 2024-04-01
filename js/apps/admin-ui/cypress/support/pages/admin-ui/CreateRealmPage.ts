export default class CreateRealmPage {
  #clearBtn = ".pf-c-file-upload__file-select button:last-child";
  #modalClearBtn = "clear-button";
  #realmIDInput = "realm";
  #enabledSwitch = ".pf-c-toolbar .pf-c-switch__toggle";
  #createBtn = '.pf-c-form__group:last-child button[type="submit"]';
  #cancelBtn = '.pf-c-form__group:last-child button[type="button"]';
  #codeEditor = ".pf-c-code-editor__code";

  fillRealmID(realmID: string) {
    cy.findByTestId(this.#realmIDInput).clear().type(realmID);

    return this;
  }

  fillCodeEditor() {
    cy.get(this.#codeEditor).click().type("clear this field");

    return this;
  }

  createRealm() {
    cy.get(this.#createBtn).click();

    return this;
  }

  disableRealm() {
    cy.get(this.#enabledSwitch).click();

    return this;
  }

  cancelRealmCreation() {
    cy.get(this.#cancelBtn).click();

    return this;
  }

  clearTextField() {
    cy.get(this.#clearBtn).click();
    cy.findByTestId(this.#modalClearBtn).click();

    return this;
  }

  verifyRealmIDFieldInvalid() {
    cy.findByTestId(this.#realmIDInput)
      .next("div")
      .contains("Required field")
      .should("have.class", "pf-m-error");

    return this;
  }
}
