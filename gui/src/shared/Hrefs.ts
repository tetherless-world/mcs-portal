import * as qs from "qs";
import {KgNodeSearchVariables} from "kg/models/kg/KgNodeSearchVariables";

export class Hrefs {
  static readonly contact = "mailto:gordom6@rpi.edu";
  static readonly gitHub = "https://github.com/tetherless-world/mcs-portal";
  static readonly home = "/";
  static readonly kgs = "/kg/";
  static kg(kwds: {id: string; idEncoded?: boolean}) {
    const kgId = kwds.idEncoded ? kwds.id : encodeURIComponent(kwds.id);
    const kgPrefix = `${Hrefs.kgs}${kgId}/`;
    return {
      get home() {
        return kgPrefix;
      },

      node(kwds: {id: string; idEncoded?: boolean}) {
        const nodeId = kwds.idEncoded ? kwds.id : encodeURIComponent(kwds.id);
        return kgPrefix + `node/${nodeId}`;
      },

      nodeSearch(kwds?: KgNodeSearchVariables) {
        if (!kwds) {
          return kgPrefix + "node/search";
        }

        const {__typename, ...searchVariables} = kwds;
        return (
          kgPrefix +
          "node/search" +
          qs.stringify(searchVariables, {addQueryPrefix: true})
        );
      },

      randomNode: kgPrefix + "randomNode",
    };
  }
}
