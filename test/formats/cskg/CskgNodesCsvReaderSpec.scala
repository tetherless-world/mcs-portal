package formats.cskg

import org.scalatest.{Matchers, WordSpec}
import stores.TestData

class CskgNodesCsvReaderSpec extends WordSpec with Matchers {
  "CSKG nodes CSV reader" can {
    val sut = new CskgNodesCsvReader()

    "read the test data" in {
      val inputStream = TestData.getNodesCsvResourceAsStream()
      try {
        val nodes = sut.read(inputStream).toList
        nodes.size should be > 0
        for (node <- nodes) {
          node.id should not be empty
          node.label should not be empty
          node.datasource should not be empty
        }
      } finally {
        inputStream.close()
      }
    }
  }
}
