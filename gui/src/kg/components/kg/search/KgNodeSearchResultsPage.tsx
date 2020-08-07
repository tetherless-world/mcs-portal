import * as React from "react";
import {Grid} from "@material-ui/core";
import {KgFrame} from "kg/components/frame/KgFrame";
import * as ReactDOM from "react-dom";
import {useApolloClient, useQuery} from "@apollo/react-hooks";
import {
  KgNodeSearchResultsPageInitialQuery,
  KgNodeSearchResultsPageInitialQuery_kgById_matchingNodes as KgNode,
  KgNodeSearchResultsPageInitialQueryVariables,
} from "kg/api/queries/types/KgNodeSearchResultsPageInitialQuery";
import * as KgNodeSearchResultsPageInitialQueryDocument from "kg/api/queries/KgNodeSearchResultsPageInitialQuery.graphql";
import * as KgNodeSearchResultsPagePaginationQueryDocument from "kg/api/queries/KgNodeSearchResultsPagePaginationQuery.graphql";
import {KgNodeTable} from "shared/components/kg/node/KgNodeTable";
import {KgNodeQuery} from "kg/api/graphqlGlobalTypes";
import {KgNodeSearchVariables} from "shared/models/kg/node/KgNodeSearchVariables";
import {kgId} from "shared/api/kgId";
import {KgSource} from "shared/models/kg/source/KgSource";
import {NumberParam, QueryParamConfig, useQueryParams} from "use-query-params";
import * as _ from "lodash";
import {
  KgNodeSearchResultsPagePaginationQuery,
  KgNodeSearchResultsPagePaginationQueryVariables,
} from "kg/api/queries/types/KgNodeSearchResultsPagePaginationQuery";

const LIMIT_DEFAULT = 10;
const OFFSET_DEFAULT = 0;

const queryQueryParamConfig: QueryParamConfig<KgNodeQuery | undefined> = {
  decode: (value) => (value ? JSON.parse(value as string) : undefined),
  encode: (value) => (!_.isEmpty(value) ? JSON.stringify(value) : undefined),
  equals: (left, right) => JSON.stringify(left) === JSON.stringify(right),
};

const makeTitle = (kwds: {
  count: number;
  query?: KgNodeQuery;
  sources: KgSource[];
}): string => {
  const {count, query, sources} = kwds;

  let title: string[] = [];

  title.push(count + "" || "No");

  title.push("results");

  if (query && query.text) {
    title.push(`for "${query.text}"`);
  }

  if (query && query.filters) {
    if (query.filters.sourceIds) {
      const {include: includeSourceIds} = query.filters.sourceIds;

      if (includeSourceIds) {
        title.push("in");

        const includeSourceLabels = [];
        for (const includeSourceId of includeSourceIds) {
          const includeSource = sources.find(
            (source) => source.id === includeSourceId
          );
          includeSourceLabels.push(
            includeSource ? includeSource.label : includeSourceId
          );
        }

        title.push(includeSourceLabels.join(", "));
      }
    }
  }

  return title.join(" ");
};

export const KgNodeSearchResultsPage: React.FunctionComponent = () => {
  let [queryParams, setQueryParams] = useQueryParams({
    limit: NumberParam,
    offset: NumberParam,
    query: queryQueryParamConfig,
  });
  const searchVariables: KgNodeSearchVariables = {
    __typename: "KgNodeSearchVariables",
    limit: queryParams.limit ?? LIMIT_DEFAULT,
    offset: queryParams.offset ?? OFFSET_DEFAULT,
    query: queryParams.query ?? {},
  };

  const {
    data: initialData,
    loading: initialLoading,
    error: initialError,
  } = useQuery<
    KgNodeSearchResultsPageInitialQuery,
    KgNodeSearchResultsPageInitialQueryVariables
  >(KgNodeSearchResultsPageInitialQueryDocument, {
    variables: {
      kgId,
      limit: searchVariables.limit!,
      offset: searchVariables.offset!,
      query: searchVariables.query!,
      queryText: searchVariables.query!.text,
    },
  });

  const apolloClient = useApolloClient();

  const [nodes, setNodes] = React.useState<KgNode[] | null>(null);

  const tableUpdateQuery = (newSearchVariables: KgNodeSearchVariables) => {
    const limit = newSearchVariables.limit ?? LIMIT_DEFAULT;
    const offset = newSearchVariables.offset ?? OFFSET_DEFAULT;
    apolloClient
      .query<
        KgNodeSearchResultsPagePaginationQuery,
        KgNodeSearchResultsPagePaginationQueryVariables
      >({
        query: KgNodeSearchResultsPagePaginationQueryDocument,
        variables: {
          kgId,
          limit,
          offset,
          query: newSearchVariables.query ?? {},
        },
      })
      .then(({data, errors, loading}) => {
        if (errors) {
        } else if (loading) {
        } else if (!data) {
          throw new EvalError();
        }
        // React does not batch updates called in
        // "timouts, promises, async" code, so we
        // manually do it
        // Might be change in v17
        ReactDOM.unstable_batchedUpdates(() => {
          setNodes(data.kgById.matchingNodes);
          const newSearchVariablesCopy = Object.assign({}, newSearchVariables);
          delete newSearchVariablesCopy["__typename"];
          setQueryParams(newSearchVariablesCopy);
        });
      });
  };

  return (
    <KgFrame data={initialData} error={initialError} loading={initialLoading}>
      {({
        data: {
          kgById: {
            matchingNodes: initialNodes,
            matchingNodesCount: count,
            sources,
          },
        },
      }) => {
        return (
          <Grid container spacing={3}>
            <Grid item xs>
              <KgNodeTable
                title={makeTitle({
                  count,
                  sources,
                  ...searchVariables,
                })}
                nodes={nodes ?? initialNodes}
                rowsPerPage={searchVariables.limit!}
                count={count}
                page={searchVariables.offset! / searchVariables.limit!}
                onChangePage={(newPage: number) =>
                  tableUpdateQuery(
                    Object.assign({}, searchVariables, {
                      offset: newPage * searchVariables.limit!,
                    })
                  )
                }
                onChangeRowsPerPage={(newRowsPerPage: number) =>
                  tableUpdateQuery(
                    Object.assign({}, searchVariables, {
                      limit: newRowsPerPage,
                      offset: 0,
                    })
                  )
                }
              />
            </Grid>
          </Grid>
        );
      }}
    </KgFrame>
  );
};
