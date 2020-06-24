package models.benchmark

final case class BenchmarkQuestion(
                                    categories: Option[List[String]],
                                    choices: List[BenchmarkQuestionChoice],
                                    concept: Option[String],
                                    correctChoiceId: String,
                                    id: String,
                                    datasetId: String,
                                    prompts: List[BenchmarkQuestionPrompt],
                                    `type`: Option[BenchmarkQuestionType]
                                  )
