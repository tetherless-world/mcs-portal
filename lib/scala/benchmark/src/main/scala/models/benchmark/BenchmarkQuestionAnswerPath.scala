package models.benchmark

import models.kg.KgEdge

final case class BenchmarkQuestionAnswerPath(path: List[String], score: Float) {
  def edges: List[KgEdge] = {
    (0 until (path.length - 1) by 2).map(pathI =>
      KgEdge(datasource = "", `object` = path(pathI + 2), other = None, predicate = path(pathI + 1), subject = path(pathI), weight = None)
    ).toList
  }
}