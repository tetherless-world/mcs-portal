import * as React from "react";

import {
  Typography,
  AppBar,
  Toolbar,
  makeStyles,
  createStyles,
  Button,
} from "@material-ui/core";

import {NodeSearchBox} from "components/search/NodeSearchBox";

import {Hrefs} from "Hrefs";

const useStyles = makeStyles((theme) =>
  createStyles({
    brand: {
      marginRight: theme.spacing(2),
      color: "white",
    },
  })
);

export const Navbar: React.FunctionComponent<{}> = () => {
  const classes = useStyles();

  return (
    <AppBar position="static" data-cy="navbar">
      <Toolbar>
        <Button href={Hrefs.home} className={classes.brand}>
          <Typography variant="h6">MOWGLI</Typography>
        </Button>
        <NodeSearchBox showIcon={true} />
      </Toolbar>
    </AppBar>
  );
};
