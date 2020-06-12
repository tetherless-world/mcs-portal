package stores.kg

import org.scalatest.{Matchers, WordSpec}
import stores.StringFilter

trait KgStoreBehaviors extends Matchers { this: WordSpec =>
  def store(sut: KgStore) {
    "get edges by object" in {
      for (node <- KgTestData.nodes) {
        val edges = sut.getEdgesByObject(limit = 1, offset = 0, objectNodeId = node.id)
        edges.size should be(1)
        val edge = edges(0)
        edge.`object` should equal(node.id)
      }
    }

    "page edges by object" in {
      val node = KgTestData.nodes(0)
      val expected = KgTestData.edges.filter(edge => edge.`object` == node.id).sortBy(edge => (edge.subject, edge.predicate))
      expected.size should be > 10
      val actual = (0 until expected.size).flatMap(offset => sut.getEdgesByObject(limit = 1, offset = offset, objectNodeId = node.id)).sortBy(edge => (edge.subject, edge.predicate)).toList
      actual should equal(expected)
    }

    "get edges by subject" in {
      val node = KgTestData.nodes(0)
      val edges = sut.getEdgesBySubject(limit = 1, offset = 0, subjectNodeId = node.id)
      edges.size should be(1)
      val edge = edges(0)
      edge.subject should equal(node.id)
    }

    "page edges by subject" in {
      val node = KgTestData.nodes(0)
      val expected = KgTestData.edges.filter(edge => edge.subject == node.id).sortBy(edge => (edge.predicate, edge.`object`))
      expected.size should be > 10
      val actual = (0 until expected.size).flatMap(offset => sut.getEdgesBySubject(limit = 1, offset = offset, subjectNodeId = node.id)).sortBy(edge => (edge.predicate, edge.`object`)).toList
      actual should equal(expected)
    }


    "get matching nodes by label" in {
      val expected = KgTestData.nodes(0)
      val actual = sut.getMatchingNodes(filters = None, limit = 10, offset = 0, text = expected.label)
      actual should not be empty
      actual(0) should equal(expected)
    }

    "get count of matching nodes by label" in {
      val expected = KgTestData.nodes(0)
      val actual = sut.getMatchingNodesCount(filters = None, text = expected.label)
      actual should be >= 1
    }

    "get matching nodes by datasource" in {
      val expected = KgTestData.nodes(0)
      val actual = sut.getMatchingNodes(filters = None, limit = 10, offset = 0, text = s"datasource:${expected.datasource}")
      actual should not be empty
      actual(0).datasource should equal(expected.datasource)
    }

    "not return matching nodes for a non-extant datasource" in {
      val actual = sut.getMatchingNodes(filters = None, limit = 10, offset = 0, text = s"datasource:nonextant")
      actual.size should be(0)
    }

    "get matching nodes by datasource and label" in {
      val expected = KgTestData.nodes(0)
      val actual = sut.getMatchingNodes(filters = None, limit = 10, offset = 0, text = s"""datasource:${expected.datasource} label:"${expected.label}"""")
      actual should not be empty
      actual(0) should equal(expected)
    }

    "get matching nodes by id" in {
      val expected = KgTestData.nodes(0)
      val actual = sut.getMatchingNodes(filters = None, limit = 10, offset = 0, text = s"""id:"${expected.id}"""")
      actual.size should be(1)
      actual(0) should equal(expected)
    }

    "get node by id" in {
      val expected = KgTestData.nodes(0)
      val actual = sut.getNodeById(expected.id)
      actual should equal(Some(expected))
    }

    "get a random node" in {
      val node = sut.getRandomNode
      sut.getNodeById(node.id) should equal(Some(node))
    }

    "get total edges count" in {
      val expected = KgTestData.edges.size
      val actual = sut.getTotalEdgesCount
      actual should equal(expected)
    }

    "get total nodes count" in {
      val expected = KgTestData.nodes.size
      val actual = sut.getTotalNodesCount
      actual should equal(expected)
    }

    "get datasources" in {
      val expected = KgTestData.nodes.flatMap(_.datasource.split(",")).toSet
      val actual = sut.getDatasources.toSet
      // Convert list to set to compare content
      actual should equal(expected)
    }

    "filter out matching nodes" in {
      val text = "Test"
      val countBeforeFilters = sut.getMatchingNodesCount(filters = None, text = text)
      countBeforeFilters should be > 0
      val actualCount = sut.getMatchingNodesCount(
        filters = Some(KgNodeFilters(datasource = Some(StringFilter(exclude = Some(List(KgTestData.nodes(0).datasource)), include = None)))),
        text = "Test"
      )
      actualCount should equal(0)
    }

    "get paths" in {
      sut.getPaths.sortBy(path => path.id) should equal(KgTestData.paths.sortBy(path => path.id))
    }

    "get a path by id" in {
      val expected = KgTestData.paths(0)
      sut.getPathById(expected.id) should equal(Some(expected))
    }

    "return None for a non-extant path" in {
      sut.getPathById("nonextant") should equal(None)
    }

    "check if is empty" in {
      sut.isEmpty should be(false)
      sut.clear()
      sut.isEmpty should be(true)
    }
  }
}