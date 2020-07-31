package stores.kg

import data.kg.TestKgData
import models.kg.{KgEdge, KgNode}
import org.scalactic.TolerantNumerics
import org.scalatest.{Matchers, WordSpec}
import stores.StringFilter
import scala.math.abs

trait KgStoreBehaviors extends Matchers { this: WordSpec =>
  sealed trait TestMode
  object TestMode {
    case object ReadOnly extends TestMode
    case object ReadWrite extends TestMode
  }

  trait KgStoreFactory {
    def apply(testMode: TestMode)(f: (KgStore) => Unit)
  }

  private def equals(left: KgNode, right: KgNode) =
    left.id == right.id && abs(left.pageRank.getOrElse(-1.0) - right.pageRank.getOrElse(-1.0)) < 0.1 && left.sources == right.sources && left.labels == right.labels && left.pos == right.pos


  def store(storeFactory: KgStoreFactory) {
    "get edges by object" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        for (node <- TestKgData.nodes) {
          val edges = sut.getEdgesByObject(limit = 1, offset = 0, objectNodeId = node.id)
          edges.size should be(1)
          val edge = edges(0)
          edge.`object` should equal(node.id)
        }
      }
    }

    "page edges by object" in {
      val sut = storeFactory(TestMode.ReadOnly) { sut =>
        val node = TestKgData.nodes(0)
        val expected = TestKgData.edges.filter(edge => edge.`object` == node.id).sortBy(edge => (edge.subject, edge.predicate))
        expected.size should be > 1
        val actual = (0 until expected.size).flatMap(offset => sut.getEdgesByObject(limit = 1, offset = offset, objectNodeId = node.id)).sortBy(edge => (edge.subject, edge.predicate)).toList
        actual should equal(expected)
      }
    }

    "get edges by subject" in {
      val sut = storeFactory(TestMode.ReadOnly) { sut =>
        val node = TestKgData.nodes(0)
        val edges = sut.getEdgesBySubject(limit = 1, offset = 0, subjectNodeId = node.id)
        edges.size should be(1)
        val edge = edges(0)
        edge.subject should equal(node.id)
      }
    }

    "page edges by subject" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val node = TestKgData.nodes(0)
        val expected = TestKgData.edges.filter(edge => edge.subject == node.id).sortBy(edge => (edge.predicate, edge.`object`))
        expected.size should be > 1
        val actual = (0 until expected.size).flatMap(offset => sut.getEdgesBySubject(limit = 1, offset = offset, subjectNodeId = node.id)).sortBy(edge => (edge.predicate, edge.`object`)).toList
        actual should equal(expected)

      }
    }

    "get matching nodes by label" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val expected = TestKgData.nodes(0)
        val actual = sut.getMatchingNodes(filters = None, limit = 10, offset = 0, text = Some(expected.labels(0)))
        actual should not be empty
        equals(actual(0), expected) shouldEqual true
      }
    }

    "get top edges by object" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val limit = 2
        val objectNodeId = TestKgData.nodes(0).id
        val actual = sut.getTopEdgesByObject(limit, objectNodeId)

        val objectEdges = TestKgData.edges.filter(_.`object` == objectNodeId)
        var partitionStart = 0
        for (partitionEnd <- 1 until actual.size + 1) {
          if (partitionEnd == actual.size || actual(partitionEnd).predicate != actual(partitionStart).predicate) {
            partitionEnd - partitionStart should be <= limit
            actual.slice(partitionStart, partitionEnd) should equal(objectEdges.filter(_.predicate == actual(partitionStart).predicate).sortBy(edge => TestKgData.nodesById(edge.subject).pageRank.get).take(limit))

            partitionStart = partitionEnd
          }
        }
      }
    }

    "get top edges by subject" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val limit = 2
        val subjectNodeId = TestKgData.nodes(0).id
        val actual = sut.getTopEdgesBySubject(limit, subjectNodeId)

        val subjectEdges = TestKgData.edges.filter(_.subject == subjectNodeId)
        var partitionStart = 0
        for (partitionEnd <- 1 until actual.size + 1) {
          if (partitionEnd == actual.size || actual(partitionEnd).predicate != actual(partitionStart).predicate) {
            partitionEnd - partitionStart should be <= limit
            actual.slice(partitionStart, partitionEnd) should equal(subjectEdges.filter(_.predicate == actual(partitionStart).predicate).sortBy(edge => TestKgData.nodesById(edge.`object`).pageRank.get).take(limit))

            partitionStart = partitionEnd
          }
        }
      }
    }

    "get count of matching nodes by label" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val expected = TestKgData.nodes(0)
        val actual = sut.getMatchingNodesCount(filters = None, text = Some(expected.labels(0)))
        actual should be >= 1
      }
    }

    "get matching nodes by source" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val expected = TestKgData.nodes(0)
        val actual = sut.getMatchingNodes(filters = None, limit = 10, offset = 0, text = Some(s"sources:${expected.sources}"))
        actual should not be empty
        actual(0).sources should equal(expected.sources)
      }
    }

    "not return matching nodes for a non-extant source" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val actual = sut.getMatchingNodes(filters = None, limit = 10, offset = 0, text = Some(s"sources:nonextant"))
        actual.size should be(0)
      }
    }

    "get matching nodes count with no text search and no filters" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        sut.getMatchingNodesCount(filters = None, text = None) should equal(TestKgData.nodes.size)
      }
    }

    "get matching nodes count with no text search but with filters" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        sut.getMatchingNodesCount(filters = Some(KgNodeFilters(sources = Some(StringFilter(exclude = None, include = Some(List(TestKgData.nodes(0).sources(0))))))), text = None) should equal(TestKgData.nodes.size)
        sut.getMatchingNodesCount(filters = Some(KgNodeFilters(sources = Some(StringFilter(exclude = Some(List(TestKgData.nodes(0).sources(0))))))), text = None) should equal(0)
      }
    }

    "get matching nodes by source and label" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val expected = TestKgData.nodes(0)
        val actual = sut.getMatchingNodes(filters = None, limit = 10, offset = 0, text = Some(s"""sources:${expected.sources(0)} labels:"${expected.labels(0)}""""))
        actual should not be empty
        equals(actual(0), expected) shouldEqual true
      }
    }

    "get matching nodes by id" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val expected = TestKgData.nodes(0)
        val actual = sut.getMatchingNodes(filters = None, limit = 10, offset = 0, text = Some(s"""id:"${expected.id}""""))
        actual.size should be(1)
        equals(actual(0), expected) shouldEqual true
      }
    }

    "filter out matching nodes" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val text = "Test"
        val countBeforeFilters = sut.getMatchingNodesCount(filters = None, text = Some(text))
        countBeforeFilters should be > 0
        val actualCount = sut.getMatchingNodesCount(
          filters = Some(KgNodeFilters(sources = Some(StringFilter(exclude = Some(List(TestKgData.nodes(0).sources(0))), include = None)))),
          text = Some("Test")
        )
        actualCount should equal(0)
      }
    }

    "get node by id" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val expected = TestKgData.nodes(0)
        val actual = sut.getNodeById(expected.id).get
        equals(actual, expected) shouldEqual true
      }
    }

    "get a random node" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val expected = sut.getRandomNode
        val actual = sut.getNodeById(expected.id).get
        equals(actual, expected) shouldEqual true
      }
    }

    "get total edges count" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val expected = TestKgData.edges.size
        val actual = sut.getTotalEdgesCount
        actual should equal(expected)
      }
    }

    "get total nodes count" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val expected = TestKgData.nodes.size
        val actual = sut.getTotalNodesCount
        actual should equal(expected)
      }
    }

    "get a path by id" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        val expected = TestKgData.paths(0)
        sut.getPathById(expected.id) should equal(Some(expected))
      }
    }

    "return None for a non-extant path" in {
      storeFactory(TestMode.ReadOnly) { sut =>
        sut.getPathById("nonextant") should equal(None)
      }
    }

    "check if is empty" in {
      storeFactory(TestMode.ReadWrite) { sut =>
        sut.isEmpty should be(false)
        sut.clear()
        sut.isEmpty should be(true)
      }
    }

    "get sources" in {
      storeFactory(TestMode.ReadWrite) { sut =>
        val expected = TestKgData.sources.sortBy(_.id)
        val actual = sut.getSources.sortBy(_.id)
        actual should equal(expected)
      }
    }

    "put and get sources" in {
      storeFactory(TestMode.ReadWrite) { sut =>
        sut.clear()
        sut.isEmpty should be(true)
        sut.putSources(TestKgData.sources)
        sut.getSources.sortBy(_.id) should equal(TestKgData.sources.sortBy(_.id))
      }
    }
  }
}
