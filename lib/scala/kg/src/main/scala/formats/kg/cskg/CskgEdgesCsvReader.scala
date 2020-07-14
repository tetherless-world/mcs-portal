package formats.kg.cskg

import java.io.InputStream
import java.nio.file.Path

import com.github.tototoshi.csv.CSVReader
import models.kg.KgEdge
import org.slf4j.LoggerFactory

final class CskgEdgesCsvReader(csvReader: CSVReader) extends CskgCsvReader[KgEdge](csvReader) {
  private val logger = LoggerFactory.getLogger(getClass)

  def iterator: Iterator[KgEdge] =
    csvReader.iteratorWithHeaders.map(row =>
      KgEdge(
        datasource = row("datasource"),
        datasources = List(row("datasource")),
        id = row("subject") + "-" + row("predicate") + "-" + row("object"),
        `object` = row("object"),
        other = row.getNonBlank("other"),
        predicate = row("predicate"),
        subject = row("subject"),
        weight = row.getNonBlank("weight").flatMap(weight => {
          try {
            Some(weight.toFloat)
          } catch {
            case e: NumberFormatException => {
              logger.warn("invalid edge weight: {}", weight)
              None
            }
          }
        })
    ))
}

object CskgEdgesCsvReader {
  def open(filePath: Path) = new CskgEdgesCsvReader(CskgCsvReader.openCsvReader(filePath))
  def open(inputStream: InputStream) =
    if (inputStream != null) {
      new CskgEdgesCsvReader(CskgCsvReader.openCsvReader(inputStream))
    } else {
      throw new NullPointerException
    }
}