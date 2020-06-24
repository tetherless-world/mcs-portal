package formats.benchmark

import java.io.InputStream

import formats.JsonlReader
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, Json}
import models.benchmark.{BenchmarkDataset, BenchmarkQuestion, BenchmarkQuestionChoice, BenchmarkQuestionChoiceType, BenchmarkQuestionPrompt}

import scala.io.Source

final class BenchmarkQuestionsJsonlReader(source: Source) extends JsonlReader[BenchmarkQuestion](source) {
  private implicit val benchmarkQuestionChoiceDecoder: Decoder[BenchmarkQuestionChoice] = deriveDecoder
  private implicit val benchmarkQuestionPromptDecoder: Decoder[BenchmarkQuestionPrompt] = deriveDecoder
  protected val decoder: Decoder[BenchmarkQuestion] = deriveDecoder
}

object BenchmarkQuestionsJsonlReader {
  def open(inputStream: InputStream): BenchmarkQuestionsJsonlReader =
    new BenchmarkQuestionsJsonlReader(JsonlReader.openSource(inputStream))
}
