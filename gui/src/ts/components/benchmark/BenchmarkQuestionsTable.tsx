import * as React from "react";
import MUIDataTable, {
  MUIDataTableColumnDef,
  MUIDataTableState,
} from "mui-datatables";
import {Link} from "react-router-dom";
import {Hrefs} from "Hrefs";
import {BenchmarkQuestionText} from "components/benchmark/BenchmarkQuestionText";
import {List, Typography, ListItemText} from "@material-ui/core";
import {BenchmarkQuestion} from "models/benchmark/BenchmarkQuestion";
import {BenchmarkQuestionType} from "api/graphqlGlobalTypes";
import {
  BenchmarkDatasetQuestionsPaginationQuery,
  BenchmarkDatasetQuestionsPaginationQuery_benchmarkById_datasetById_questions,
  BenchmarkDatasetQuestionsPaginationQueryVariables,
} from "api/queries/benchmark/types/BenchmarkDatasetQuestionsPaginationQuery";
import {useApolloClient} from "@apollo/react-hooks";
import * as BenchmarkDatasetQuestionsPaginationQueryDocument from "api/queries/benchmark/BenchmarkDatasetQuestionsPaginationQuery.graphql";

export const BenchmarkQuestionsTable: React.FunctionComponent<{
  benchmarkId: string;
  datasetId: string;
  initialQuestions: BenchmarkQuestion[];
  questionsTotal: number;
  submissionId?: string;
}> = ({
  benchmarkId,
  datasetId,
  initialQuestions,
  questionsTotal,
  submissionId,
}) => {
  const apolloClient = useApolloClient();

  const [questions, setQuestions] = React.useState<
    BenchmarkDatasetQuestionsPaginationQuery_benchmarkById_datasetById_questions[]
  >(initialQuestions);

  const getRowQuestionId = (rowData: any[]) => rowData[0];

  const columns: MUIDataTableColumnDef[] = [];
  columns.push({
    name: "id",
    options: {
      display: "false",
    },
  });
  columns.push({
    name: "prompts",
    label: "Text",
    options: {
      customBodyRender: (prompts, tableMeta) => {
        return (
          <span data-cy="question-text">
            {submissionId ? (
              <Link
                data-cy="question-text"
                to={Hrefs.benchmark({id: benchmarkId})
                  .dataset({id: datasetId})
                  .submission({id: submissionId})
                  .question({
                    id: getRowQuestionId(tableMeta.rowData),
                  })}
              >
                <BenchmarkQuestionText prompts={prompts} />
              </Link>
            ) : (
              <BenchmarkQuestionText prompts={prompts} />
            )}
          </span>
        );
      },
    },
  });
  if (questions.some((question) => question.type !== null)) {
    columns.push({
      name: "type",
      label: "Type",
      options: {
        customBodyRender: (type) => {
          switch (type) {
            case BenchmarkQuestionType.MultipleChoice:
              return "Multiple Choice";
            case BenchmarkQuestionType.TrueFalse:
              return "True/False";
            default:
              throw new EvalError();
          }
        },
      },
    });
  }
  if (questions.some((question) => question.categories)) {
    columns.push({
      name: "categories",
      label: "Categories",
      options: {
        customBodyRender: (
          categories: string[] | undefined,
          tableMeta: any
        ) => {
          if (categories) {
            return (
              <List>
                {categories.map((category) => (
                  <ListItemText key={category}>{category}</ListItemText>
                ))}
              </List>
            );
          } else {
            return <></>;
          }
        },
      },
    });
  }
  if (questions.some((question) => question.concept)) {
    columns.push({name: "concept", label: "Concept"});
  }

  const onChangePage = (tableState: MUIDataTableState) => {
    // Use another query to paginate instead of refetch so that we don't re-render the whole frame when loading goes back to true.
    // We also don't request redundant data.
    apolloClient
      .query<
        BenchmarkDatasetQuestionsPaginationQuery,
        BenchmarkDatasetQuestionsPaginationQueryVariables
      >({
        query: BenchmarkDatasetQuestionsPaginationQueryDocument,
        variables: {
          benchmarkId,
          datasetId,
          questionsLimit: tableState.rowsPerPage,
          questionsOffset: tableState.page * tableState.rowsPerPage,
        },
      })
      .then(({data, errors, loading}) => {
        if (errors) {
        } else if (loading) {
        } else if (!data) {
          throw new EvalError();
        }
        setQuestions(data.benchmarkById!.datasetById!.questions);
      });
  };

  return (
    <div data-cy="benchmark-questions">
      <MUIDataTable
        columns={columns}
        data={questions}
        options={{
          count: questionsTotal,
          filter: false,
          onTableChange: (action, tableState) => {
            switch (action) {
              case "changePage": {
                onChangePage(tableState);
                break;
              }
            }
          },
          rowsPerPage: questions.length,
          serverSide: true,
          setRowProps: (row) => ({
            "data-cy": "question-" + getRowQuestionId(row),
          }),
          sort: false,
        }}
        title={<Typography variant="h6">Questions</Typography>}
      />
    </div>
  );
};