import {RandomKgNodePage} from "../../support/kg/pages/RandomKgNodePage";
import {TestData} from "../../support/kg/KgTestData";

context("Random KG node page", () => {
  const page = new RandomKgNodePage();

  it("should immediately redirect to a node page", () => {
    cy.visit(page.relativeUrl);
    cy.url().should(
      "contains",
      Cypress.config().baseUrl + `/kg/${TestData.kgId}/node/`
    );
  });
});