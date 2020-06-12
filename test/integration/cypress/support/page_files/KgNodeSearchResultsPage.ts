import {Page} from "./Page";

class KgNodeResultsTable {
  get() {
    return cy.get("[data-cy=matchingNodesTable]");
  }

  row(index: number): KgNodeResultsTableRow {
    return new KgNodeResultsTableRow(index, this);
  }
}

class KgNodeResultsTableRow {
  constructor(
    private readonly index: number,
    private readonly table: KgNodeResultsTable
  ) {}

  get() {
    return this.table.get().find("tbody>tr").eq(this.index);
  }

  readonly nodeLink = new KgNodeResultsNodeTableRowKgNodeLink(this);
}

class KgNodeResultsNodeTableRowKgNodeLink {
  constructor(private readonly row: KgNodeResultsTableRow) {}

  click() {
    this.row.get().find("a").click();
  }
}

export class KgNodeSearchResultsPage extends Page {
  constructor(private readonly search: string) {
    super();
  }

  readonly resultsTable = new KgNodeResultsTable();

  get relativeUrl() {
    return "/node/search?text=" + encodeURIComponent(this.search);
  }

  get visualizationContainer() {
    return cy.get(
      this.frame.bodySelector + " [data-cy=visualizationContainer]"
    );
  }
}