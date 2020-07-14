import * as React from "react";
import {
  Link,
  Route,
  Switch,
  useLocation,
  useRouteMatch,
} from "react-router-dom";
import {
  Card,
  CardContent,
  CardHeader,
  Grid,
  List,
  ListItemText,
  Tab,
  Tabs,
} from "@material-ui/core";
import {KgNodePredicateGrid} from "shared/components/kg/node/KgNodePredicateGrid";
import {KgNodePredicateList} from "shared/components/kg/node/KgNodePredicateList";
import {KgDatasourceLink} from "shared/components/kg/search/KgDatasourceLink";
import {KgNodeSubjectOfEdge} from "shared/models/kg/KgNodeSubjectOfEdge";

export const KgNodeViews: React.FunctionComponent<{
  node: {
    aliases: string[] | null;
    datasource: string;
    id: string;
    label: string | null;
    pos: string | null;
    subjectOfEdges: KgNodeSubjectOfEdge[];
  };
}> = ({node}) => {
  const location = useLocation();
  const {path, url} = useRouteMatch();

  const nodeLabel = node.label ? node.label : node.id;

  let title = nodeLabel;
  if (node.pos) {
    title += " (" + node.pos + ")";
  }

  const predicateSubjects: {
    [index: string]: KgNodeSubjectOfEdge[];
  } = {};
  for (const edge of node.subjectOfEdges) {
    if (!edge.objectNode) {
      continue;
    } else if (!edge.predicate) {
      continue;
    }
    const edges = (predicateSubjects[edge.predicate] =
      predicateSubjects[edge.predicate] ?? []);
    edges.push(edge);
  }

  class TabRoute {
    constructor(
      private relPath: string,
      readonly label: string,
      readonly dataCy: string
    ) {}

    get url() {
      return url + this.relPath;
    }

    get path() {
      return path + this.relPath;
    }
  }

  const tabRoutes = {
    grid: new TabRoute("", "Predicate Grid", "predicate-grid"),
    list: new TabRoute("/list", "Predicate List", "predicate-list"),
  };

  return (
    <Grid container direction="column">
      <Tabs value={location.pathname}>
        {Object.values(tabRoutes).map((tabRoute) => (
          <Tab
            component={Link}
            value={tabRoute.url}
            to={tabRoute.url}
            key={tabRoute.url}
            label={tabRoute.label}
            data-cy={`${tabRoute.dataCy}`}
          />
        ))}
      </Tabs>

      <Grid item container>
        <Grid item xs={10}>
          <h1 data-cy="node-title">{title}</h1>
          <Switch>
            <Route exact path={tabRoutes.grid.path}>
              <KgNodePredicateGrid
                predicateSubjects={predicateSubjects}
                datasource={node.datasource}
              />
            </Route>
            <Route path={tabRoutes.list.path}>
              <KgNodePredicateList
                predicateSubjects={predicateSubjects}
                datasource={node.datasource}
              />
            </Route>
          </Switch>
        </Grid>
        <Grid item xs={2}>
          <Grid container direction="column" spacing={6}>
            <Grid item>
              <Card>
                <CardHeader title="Datasource(s)"></CardHeader>
                <CardContent>
                  <List>
                    <ListItemText data-cy="node-datasource">
                      <KgDatasourceLink datasource={node.datasource} />
                    </ListItemText>
                  </List>
                </CardContent>
              </Card>
            </Grid>
            {node.aliases ? (
              <Grid item>
                <Card>
                  <CardHeader title="Aliases" />
                  <CardContent>
                    <List>
                      {[...new Set(node.aliases)].map((alias) => (
                        <ListItemText key={alias}>{alias}</ListItemText>
                      ))}
                    </List>
                  </CardContent>
                </Card>
              </Grid>
            ) : null}
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );
};