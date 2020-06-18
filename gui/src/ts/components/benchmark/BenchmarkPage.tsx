import * as React from "react";
import {Link, useParams} from "react-router-dom";
import {BenchmarkPageQuery} from "api/queries/benchmark/types/BenchmarkPageQuery";
import * as BenchmarkPageQueryDocument from "api/queries/benchmark/BenchmarkPageQuery.graphql";
import {useQuery} from "@apollo/react-hooks";
import {
  Grid,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@material-ui/core";
import {Hrefs} from "Hrefs";
import {BenchmarkFrame} from "components/benchmark/BenchmarkFrame";

export const BenchmarkPage: React.FunctionComponent = () => {
  const {benchmarkId} = useParams<{benchmarkId: string}>();

  const {data, error, loading} = useQuery<BenchmarkPageQuery>(
    BenchmarkPageQueryDocument,
    {variables: {benchmarkId}}
  );

  const benchmark = data?.benchmarkById;

  return (
    <BenchmarkFrame
      data={data}
      error={error}
      loading={loading}
      routeParams={{benchmarkId}}
    >
      <Grid container direction="column" spacing={3}>
        <Grid item>
          <Typography data-cy="benchmark-name" variant="h4">
            {benchmark?.name}
          </Typography>
        </Grid>
        <Grid item>
          <Typography variant="h5">Datasets</Typography>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                {/*<TableCell>Type</TableCell>*/}
              </TableRow>
            </TableHead>
            <TableBody>
              {benchmark?.datasets.map((dataset) => (
                <TableRow key={dataset.id}>
                  <TableCell data-cy={"dataset-name-" + dataset.id}>
                    <Link
                      to={
                        Hrefs.benchmark({id: benchmarkId}).dataset({
                          id: dataset.id,
                        }).home
                      }
                    >
                      {dataset.name}
                    </Link>
                  </TableCell>
                  {/*<TableCell></TableCell>*/}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Grid>
      </Grid>
    </BenchmarkFrame>
  );
};
