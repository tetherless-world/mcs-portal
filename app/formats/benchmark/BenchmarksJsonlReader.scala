package formats.benchmark

import formats.JsonlReader
import io.circe.{Decoder, Json}
import models.benchmark.{Benchmark, BenchmarkDataset}
import io.circe.generic.semiauto.deriveDecoder

import scala.io.Source

final class BenchmarksJsonlReader(source: Source) extends JsonlReader[Benchmark](source) {
  private implicit val benchmarkDatasetDecoder: Decoder[BenchmarkDataset] = deriveDecoder
  protected val decoder: Decoder[Benchmark] = deriveDecoder
}
