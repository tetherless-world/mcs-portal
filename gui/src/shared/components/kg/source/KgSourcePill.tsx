import * as React from "react";
import {KgSource} from "shared/models/kg/source/KgSource";
import {Chip, ChipProps} from "@material-ui/core";
import {useHistory} from "react-router-dom";
import {Hrefs} from "shared/Hrefs";
import {kgId} from "shared/api/kgId";
import * as d3 from "d3";

const colors = d3.scaleOrdinal(d3.schemeTableau10);

export const KgSourcePill: React.FunctionComponent<
  {hrefs: Hrefs; idOnly?: boolean; source: KgSource} & ChipProps
> = ({hrefs, idOnly, source, ...chipProps}) => {
  const history = useHistory();

  const color = colors(source.id);

  return (
    <Chip
      data-cy="source-link"
      label={idOnly ? source.id : `${source.id}: ${source.label}`}
      variant="outlined"
      onClick={() => {
        history.push(hrefs.kg({id: kgId}).source({sourceId: source.id}));
      }}
      style={{color, borderColor: color, margin: "2px"}}
      {...chipProps}
    />
  );
};
