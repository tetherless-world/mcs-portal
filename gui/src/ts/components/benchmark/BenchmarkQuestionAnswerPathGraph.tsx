import * as React from "react";
import * as d3 from "d3";
import {BenchmarkAnswerPageQuery_benchmarkById_datasetById_submissionById_answerByQuestionId_explanation_choiceAnalyses_questionAnswerPaths as QuestionAnswerPath} from "api/queries/benchmark/types/BenchmarkAnswerPageQuery";
import {
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Grid,
  withStyles,
} from "@material-ui/core";
import {
  ForceGraph,
  ForceGraphNode,
  ForceGraphArrowLink,
} from "components/data/forceGraph";
import {BenchmarkAnswerChoiceAnalysisGraphLinkDatum} from "models/benchmark/BenchmarkAnswerChoiceAnalysisGraphLinkDatum";
import {BenchmarkAnswerChoiceAnalysisGraphNodeDatum} from "models/benchmark/BenchmarkAnswerChoiceAnalysisGraphNodeDatum";
import {getAnswerPathId} from "util/benchmark/getAnswerPathId";
import {BenchmarkAnswerChoiceAnalysisGraphLegendCircle} from "components/benchmark/BenchmarkAnswerChoiceAnalysisGraphLegendCircle";

const HorizontalList = withStyles({
  root: {
    display: "flex",
    flexDirection: "row",
    padding: 0,
    whiteSpace: "nowrap",
  },
})(List);

// Initialize color scale
const colorScale = d3.interpolateRgb("red", "green");

// Scale radius of nodes by number of incoming edges
const nodeRadius = (node: BenchmarkAnswerChoiceAnalysisGraphNodeDatum) =>
  node.incomingEdges > 0 ? 8 * Math.log2(node.incomingEdges) + 10 : 10;

export const BenchmarkQuestionAnswerPathGraph: React.FunctionComponent<{
  questionAnswerPath: QuestionAnswerPath;
  nodes: BenchmarkAnswerChoiceAnalysisGraphNodeDatum[];
  nodesIndexed: {[nodeId: string]: BenchmarkAnswerChoiceAnalysisGraphNodeDatum};
  links: BenchmarkAnswerChoiceAnalysisGraphLinkDatum[];
}> = ({questionAnswerPath, nodes, links, nodesIndexed}) => {
  const {
    startNode,
    startNodeId,
    endNode,
    endNodeId,
    score,
    paths,
  } = questionAnswerPath;

  const highestScorePath = paths.sort((p1, p2) => p2.score - p1.score)[0];
  const highestScorePathIndex = paths.findIndex(
    (path) => path === highestScorePath
  );

  const highestScorePathId = getAnswerPathId(
    questionAnswerPath,
    highestScorePathIndex
  );

  return (
    <div>
      <HorizontalList>
        <ListItem>
          <ListItemText primary={"Score: " + score.toFixed(3)} />
        </ListItem>
        <ListItem>
          <ListItemAvatar>
            <BenchmarkAnswerChoiceAnalysisGraphLegendCircle
              color="none"
              border="2px solid blue"
            />
          </ListItemAvatar>
          <ListItemText
            primary={"Start: " + (startNode?.label ?? startNodeId)}
          />
        </ListItem>
        <ListItem>
          <ListItemAvatar>
            <BenchmarkAnswerChoiceAnalysisGraphLegendCircle
              color="none"
              border="2px solid purple"
            />
          </ListItemAvatar>
          <ListItemText primary={"End: " + (endNode?.label ?? endNodeId)} />
        </ListItem>
      </HorizontalList>
      <Grid container>
        <Grid item md={4}>
          <ForceGraph height={400} width={400}>
            {nodes.map((node) => {
              const score =
                node.paths.reduce(
                  (totalScore, path) =>
                    totalScore + (path.questionAnswerPathId ? path.score : 0),
                  0
                ) / node.paths.length;
              let stroke = undefined;
              if (node.id === startNodeId) {
                stroke = "blue";
              } else if (node.id === endNodeId) {
                stroke = "purple";
              }

              return (
                <ForceGraphNode
                  key={node.id}
                  node={node}
                  r={nodeRadius(node)}
                  fill={colorScale(score)}
                  opacity={
                    node.paths.some((path) => path.id === highestScorePathId)
                      ? 1
                      : 0.2
                  }
                  cursor="pointer"
                  stroke={stroke}
                  strokeWidth={4}
                >
                  <title>{node.label}</title>
                </ForceGraphNode>
              );
            })}
            {links.map((link) => (
              <ForceGraphArrowLink
                key={link.id}
                link={link}
                stroke={colorScale(link.score)}
                targetRadius={nodeRadius(nodesIndexed[link.targetId])}
                opacity={highestScorePathId === link.pathId ? 1 : 0.2}
              />
            ))}
          </ForceGraph>
        </Grid>
        <Grid item md={8}>
          <code>{JSON.stringify(questionAnswerPath)}</code>
        </Grid>
      </Grid>
    </div>
  );
};
