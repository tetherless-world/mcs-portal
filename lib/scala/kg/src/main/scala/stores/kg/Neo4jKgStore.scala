package stores.kg

import com.google.inject.Inject
import io.github.tetherlessworld.twxplore.lib.base.WithResource
import javax.inject.Singleton
import models.kg.{KgEdge, KgNode, KgPath}
import org.neo4j.driver._
import org.slf4j.LoggerFactory
import stores.{Neo4jStoreConfiguration, StringFilter}

import scala.collection.JavaConverters._
import scala.collection.mutable

final case class CypherBinding(variableName: String, value: Any)
final case class CypherFilter(cypher: String, binding: Option[CypherBinding] = None)
final case class CypherFilters(filters: List[CypherFilter]) {
  def toCypherBindingsMap: Map[String, Any] =
    filters.flatMap(filter => filter.binding).map(binding => (binding.variableName -> binding.value)).toMap

  def toCypherString: String =
    if (!filters.isEmpty) {
      s"WHERE ${filters.map(filter => filter.cypher).mkString(" AND ")}"
    } else {
      ""
    }
}
object CypherFilters {
  def apply(nodeFilters: Option[KgNodeFilters]): CypherFilters =
    if (nodeFilters.isDefined) {
      apply(nodeFilters.get)
    } else {
      CypherFilters(List())
    }

  def apply(nodeFilters: KgNodeFilters): CypherFilters =
    if (nodeFilters.sources.isDefined) {
      CypherFilters(toCypherFilters(bindingVariableNamePrefix = "nodeSource", property = "node.sources", stringFilter = nodeFilters.sources.get))
    } else {
      CypherFilters(List())
    }

  private def toCypherFilters(bindingVariableNamePrefix: String, property: String, stringFilter: StringFilter): List[CypherFilter] = {
    stringFilter.exclude.getOrElse(List()).zipWithIndex.map(excludeWithIndex => {
      val bindingVariableName = s"${bindingVariableNamePrefix}Exclude${excludeWithIndex._2}"
      CypherFilter(binding = Some(CypherBinding(variableName = bindingVariableName, value = excludeWithIndex._1)), cypher = s"NOT ${property} = $$${bindingVariableName}")
    }) ++
      stringFilter.include.getOrElse(List()).zipWithIndex.map(includeWithIndex => {
        val bindingVariableName = s"${bindingVariableNamePrefix}Include${includeWithIndex._2}"
        CypherFilter(binding = Some(CypherBinding(variableName = bindingVariableName, value = includeWithIndex._1)), cypher = s"${property} = $$${bindingVariableName}")
      })
  }
}

final case class PathRecord(
                            sources: List[String],
                            objectNodeId: String,
                            pathEdgeIndex: Int,
                            pathEdgeRelation: String,
                            pathId: String,
                            subjectNodeId: String
                           ) {
  def toEdge: KgEdge =
    KgEdge(
      id = s"${pathId}-${pathEdgeIndex}",
      labels = List(),
      `object` = objectNodeId,
      origins = List(),
      questions = List(),
      relation = pathEdgeRelation,
      sentences = List(),
      sources = sources,
      subject = subjectNodeId,
      weight = None
    )
}

@Singleton
final class Neo4jKgStore @Inject()(configuration: Neo4jStoreConfiguration) extends KgStore with WithResource {
  private var bootstrapped: Boolean = false
  private val driver = GraphDatabase.driver(configuration.uri, AuthTokens.basic(configuration.user, configuration.password))
  private val edgePropertyNameList = List("id", "labels", "object", "origins", "questions", "sentences", "sources", "subject", "weight")
  private val edgePropertyNamesString = edgePropertyNameList.map(edgePropertyName => "edge." + edgePropertyName).mkString(", ")
  private val logger = LoggerFactory.getLogger(getClass)
  private val nodePropertyNameList = List("id", "labels", "pos", "sources")
  private val nodePropertyNamesString = nodePropertyNameList.map(nodePropertyName => "node." + nodePropertyName).mkString(", ")
  private val pathPropertyNameList = List("id", "objectNode", "pathEdgeIndex", "pathEdgeRelation", "sources", "subjectNode")
  private val pathPropertyNamesString = pathPropertyNameList.map(pathPropertyName => "path." + pathPropertyName).mkString(", ")

  private implicit class RecordWrapper(record: Record) {
    def toEdge: KgEdge = {
      val recordMap = record.asMap().asScala.toMap.asInstanceOf[Map[String, Object]]
      KgEdge(
        id = recordMap("edge.id").asInstanceOf[String],
        labels = recordMap("labels").asInstanceOf[String].split(ListDelim).toList,
        `object` = recordMap("object.id").asInstanceOf[String],
        origins = recordMap("origins").asInstanceOf[String].split(ListDelim).toList,
        questions = recordMap("questions").asInstanceOf[String].split(ListDelim).toList,
        sentences = recordMap("sentences").asInstanceOf[String].split(ListDelim).toList,
        relation = recordMap("type(edge)").asInstanceOf[String],
        sources = recordMap("sources").asInstanceOf[String].split(ListDelim).toList,
        subject = recordMap("subject.id").asInstanceOf[String],
        weight = Option(recordMap("edge.weight")).map(weight => weight.asInstanceOf[Double].floatValue())
      )
    }

    def toNode: KgNode = {
      val recordMap = record.asMap().asScala.toMap.asInstanceOf[Map[String, String]]
      KgNode(
        id = recordMap("node.id"),
        labels = recordMap("labels").split(ListDelim).toList,
        pos = Option(recordMap("node.pos")),
        sources = recordMap("sources").split(ListDelim).toList
      )
    }

    def toPathRecord: PathRecord = {
      PathRecord(
        objectNodeId = record.get("objectNode.id").asString(),
        pathId = record.get("path.id").asString(),
        pathEdgeIndex = record.get("path.pathEdgeIndex").asInt(),
        pathEdgeRelation = record.get("path.pathEdgeRelation").asString(),
        sources = record.get("path.sources").asString().split(ListDelim).toList,
        subjectNodeId = record.get("subjectNode.id").asString()
      )
    }
  }

  private implicit class ResultsWrapper(result: Result) {
    def toEdges: List[KgEdge] =
      result.asScala.toList.map(record => record.toEdge)

    def toNodes: List[KgNode] =
      result.asScala.toList.map(record => record.toNode)

    def toPaths: List[KgPath] = {
      result.asScala.toList.map(record => record.toPathRecord).groupBy(pathRecord => pathRecord.pathId).map(pathRecordsEntry =>
        pathRecordsEntry match {
          case (pathId, pathRecords) =>
            KgPath(
              edges = pathRecords.sortBy(pathRecord => pathRecord.pathEdgeIndex).map(pathRecord => pathRecord.toEdge),
              id = pathId,
              sources = pathRecords(0).sources,
            )
        }
      ).toList
    }
  }

  bootstrapStore()

  private def bootstrapStore(): Unit = {
    this.synchronized {
      if (bootstrapped) {
        return
      }

      withSession { session =>
        val hasConstraints =
          session.readTransaction { transaction =>
            val result =
              transaction.run("CALL db.constraints")
            result.hasNext
          }

        if (hasConstraints) {
          logger.info("neo4j indices already exist")
          bootstrapped = true
          return
        }

        logger.info("bootstrapping neo4j indices")

        val bootstrapCypherStatements = List(
          """CALL db.index.fulltext.createNodeIndex("node",["Node"],["id", "labels", "sources"]);""",
          """CREATE CONSTRAINT node_id_constraint ON (n:Node) ASSERT n.id IS UNIQUE;"""
        )

        session.writeTransaction { transaction =>
          for (bootstrapCypherStatement <- bootstrapCypherStatements) {
            transaction.run(bootstrapCypherStatement)
          }
          transaction.commit()
        }
      }

      logger.info("bootstrapped neo4j indices")
    }
  }

  final def clear(): Unit = {
    // It would be simpler to use CREATE OR REPLACE DATABASE, but the free Neo4j 4.0 Community Edition doesn't support it,
    // and the open source fork of the Neo4j Enterprise Edition doesn't include 4.0 features yet.
    withSession { session =>
      session.writeTransaction { transaction =>
        // https://neo4j.com/developer/kb/large-delete-transaction-best-practices-in-neo4j/
        transaction.run(
          """CALL apoc.periodic.iterate("MATCH (n) return n", "DETACH DELETE n", {batchSize:1000})
            |YIELD batches, total
            |RETURN batches, total
            |""".stripMargin)
        transaction.commit()
      }
    }
    while (!isEmpty) {
      logger.info("waiting for neo4j to clear")
      Thread.sleep(100)
    }
  }

  final override def getSources: List[String] =
    withSession { session =>
      session.readTransaction { transaction =>
        val result =
          transaction.run("MATCH (node:Node) RETURN DISTINCT node.sources AS sources")
        val sourceValues = result.asScala.toList.map(_.get("sources").asString)
        // Returns list of source values which can contain multiple sources
        // so need to extract unique sources
        sourceValues.flatMap(_.split(ListDelim)).distinct
      }
    }

  override final def getEdgesByObject(limit: Int, objectNodeId: String, offset: Int): List[KgEdge] = {
    withSession { session =>
      session.readTransaction { transaction => {
        transaction.run(
          s"""
             |MATCH (subject:Node)-[edge]->(object:Node {id: $$objectNodeId})
             |RETURN type(edge), object.id, subject.id, ${edgePropertyNamesString}
             |ORDER BY type(edge), subject.id, edge
             |SKIP ${offset}
             |LIMIT ${limit}
             |""".stripMargin,
          toTransactionRunParameters(Map(
            "objectNodeId" -> objectNodeId
          ))
        ).toEdges
      }
      }
    }
  }

  override final def getEdgesBySubject(limit: Int, offset: Int, subjectNodeId: String): List[KgEdge] = {
    withSession { session =>
      session.readTransaction { transaction => {
        transaction.run(
          s"""
             |MATCH (subject:Node {id: $$subjectNodeId})-[edge]->(object:Node)
             |RETURN type(edge), object.id, subject.id, ${edgePropertyNamesString}
             |ORDER BY type(edge), object.id, edge
             |SKIP ${offset}
             |LIMIT ${limit}
             |""".stripMargin,
          toTransactionRunParameters(Map(
            "subjectNodeId" -> subjectNodeId
          ))
        ).toEdges
      }
      }
    }
  }

  final override def getMatchingNodes(filters: Option[KgNodeFilters], limit: Int, offset: Int, text: Option[String]): List[KgNode] = {
    val cypherFilters = CypherFilters(filters)

    withSession { session =>
      session.readTransaction { transaction =>
        transaction.run(
          s"""${textMatchToCypherMatch(text)}
             |${cypherFilters.toCypherString}
             |RETURN ${nodePropertyNamesString}
             |SKIP ${offset}
             |LIMIT ${limit}
             |""".stripMargin,
          toTransactionRunParameters(textMatchToCypherBindingsMap(text) ++ cypherFilters.toCypherBindingsMap)
        ).toNodes
      }
    }
  }

  final override def getMatchingNodesCount(filters: Option[KgNodeFilters], text: Option[String]): Int = {
    val cypherFilters = CypherFilters(filters)

    withSession { session =>
      session.readTransaction { transaction =>
        val result =
          transaction.run(
            s"""${textMatchToCypherMatch(text)}
               |${cypherFilters.toCypherString}
               |RETURN COUNT(node)
               |""".stripMargin,
            toTransactionRunParameters(textMatchToCypherBindingsMap(text) ++ cypherFilters.toCypherBindingsMap)
          )
        val record = result.single()
        record.get("COUNT(node)").asInt()
      }
    }
  }

  override final def getNodeById(id: String): Option[KgNode] = {
    withSession { session =>
      session.readTransaction { transaction => {
        transaction.run(
          s"MATCH (node:Node {id: $$id}) RETURN ${nodePropertyNamesString};",
          toTransactionRunParameters(Map("id" -> id))
        ).toNodes.headOption
      }
      }
    }
  }

//  override def getPaths: List[KgPath] =
//    withSession { session =>
//      session.readTransaction { transaction =>
//        transaction.run(
//          s"""MATCH (subjectNode:Node)-[path:PATH]->(objectNode:Node)
//            |RETURN objectNode.id, subjectNode.id, ${pathPropertyNamesString}
//            |""".stripMargin
//        ).toPaths
//      }
//    }
//
  override def getPathById(id: String): Option[KgPath] = {
    withSession { session =>
      session.readTransaction { transaction =>
        transaction.run(
          s"""MATCH (subjectNode:Node)-[path:PATH {id: $$id}]->(objectNode:Node)
             |RETURN objectNode.id, subjectNode.id, ${pathPropertyNamesString}
             |""".stripMargin,
          toTransactionRunParameters(Map("id" -> id))
        ).toPaths.headOption
      }
    }
  }

  final override def getRandomNode: KgNode =
    withSession { session =>
      session.readTransaction { transaction => {
        transaction.run(
          s"MATCH (node:Node) RETURN ${nodePropertyNamesString}, rand() as rand ORDER BY rand ASC LIMIT 1"
        ).toNodes.head
      }
      }
    }

  final override def getTotalEdgesCount: Int =
    withSession { session =>
      session.readTransaction { transaction =>
        transaction.run(
          """
            |MATCH (subject:Node)-[r]->(object:Node)
            |WHERE NOT type(r) = "PATH"
            |RETURN COUNT(r) as count
            |""".stripMargin
        ).single().get("count").asInt()
      }
    }

  final override def getTotalNodesCount: Int =
    withSession { session =>
      session.readTransaction { transaction =>
        transaction.run("MATCH (n:Node) RETURN COUNT(n) as count").single().get("count").asInt()
      }
    }

  final override def isEmpty: Boolean =
    withSession { session =>
      session.readTransaction { transaction =>
        transaction.run("MATCH (n) RETURN COUNT(n) as count").single().get("count").asInt() == 0
      }
    }

  private def toTransactionRunParameters(map: Map[String, Any]) =
    map.asJava.asInstanceOf[java.util.Map[String, Object]]

  final override def putEdges(edges: Iterator[KgEdge]): Unit =
    putModels(edges) { (transaction, edge) => {
      //          CREATE (:Node { id: node.id, label: node.label, aliases: node.aliases, pos: node.pos, datasource: node.datasource, other: node.other });
      transaction.run(
        """MATCH (subject:Node {id: $subject}), (object:Node {id: $object})
          |CALL apoc.create.relationship(subject, relation, {id: $id, labels: $labels, origins: $origins, questions: $questions, sentences: $sentences, sources: $sources, weight: toFloat($weight)}, object) YIELD rel
          |REMOVE rel.noOp
          |""".stripMargin,
        toTransactionRunParameters(Map(
          "id" -> edge.id,
          "labels" -> edge.labels.mkString(ListDelim),
          "object" -> edge.`object`,
          "origins" -> edge.origins.mkString(ListDelim),
          "questions" -> edge.questions.mkString(ListDelim),
          "relation" -> edge.relation,
          "sentences" -> edge.sentences.mkString(ListDelim),
          "sources" -> edge.sources.mkString(ListDelim),
          "subject" -> edge.subject,
          "weight" -> edge.weight.getOrElse(null)
        ))
      )
    }
    }

  final override def putNodes(nodes: Iterator[KgNode]): Unit =
    putModels(nodes) { (transaction, node) =>
      //          CREATE (:Node { id: node.id, label: node.label, aliases: node.aliases, pos: node.pos, datasource: node.datasource, other: node.other });
      transaction.run(
        "CREATE (:Node { id: $id, labels: $labels, pos: $pos, sources: $sources });",
        toTransactionRunParameters(Map(
          "id" -> node.id,
          "labels" -> node.labels.mkString(ListDelim),
          "pos" -> node.pos.getOrElse(null),
          "sources" -> node.sources.mkString(ListDelim),
        ))
      )
    }

  override def putPaths(paths: Iterator[KgPath]): Unit =
    putModels(paths) { (transaction, path) => {
      for (pathEdgeWithIndex <- path.edges.zipWithIndex) {
        val (pathEdge, pathEdgeIndex) = pathEdgeWithIndex
        transaction.run(
          """
            |MATCH (subject:Node), (object: Node)
            |WHERE subject.id = $subject AND object.id = $object
            |CREATE (subject)-[path:PATH {id: $pathId, pathEdgeIndex: $pathEdgeIndex, pathEdgeRelation: $pathEdgeRelation, sources: $sources}]->(object)
            |""".stripMargin,
          toTransactionRunParameters(Map(
            "object" -> pathEdge.`object`,
            "pathEdgeIndex" -> pathEdgeIndex,
            "pathEdgeRelation" -> pathEdge.relation,
            "pathId" -> path.id,
            "sources" -> path.sources.mkString(ListDelim),
            "subject" -> pathEdge.subject
          ))
        )
      }
    }
    }

  private def putModels[T](models: Iterator[T])(putModel: (Transaction, T)=>Unit): Unit =
    withSession { session => {
      // Batch the models in order to put them all in a transaction.
      // My (MG) first implementation looked like:
//      for (modelWithIndex <- models.zipWithIndex) {
//        val (model, modelIndex) = modelWithIndex
//        putModel(transaction, model)
//        if (modelIndex > 0 && (modelIndex + 1) % PutCommitInterval == 0) {
//          tryOperation(() => transaction.commit())
//          transaction = session.beginTransaction()
//        }
//      }
      // tryOperation handled TransientException, but the first transaction always failed and was rolled back.
      // I don't have time to investigate that. Batching models should be OK for now.
      val modelBatch = new mutable.MutableList[T]
      while (models.hasNext) {
        while (modelBatch.size < configuration.commitInterval && models.hasNext) {
          modelBatch += models.next()
        }
        if (!modelBatch.isEmpty) {
//          logger.info("putting batch of {} models in a transaction", modelBatch.size)
          session.writeTransaction { transaction =>
            for (model <- modelBatch) {
              putModel(transaction, model)
            }
          }
          modelBatch.clear()
        }
      }
    }
  }

  private def textMatchToCypherBindingsMap(text: Option[String]) =
    if (text.isDefined) {
      Map(
        "text" -> text.get
      )
    } else {
      Map()
    }

  private def textMatchToCypherMatch(text: Option[String]) =
    if (text.isDefined) {
      s"""CALL db.index.fulltext.queryNodes("node", $$text) YIELD node, score"""
    } else {
      "MATCH (node: Node)"
    }

  private def withSession[V](f: Session => V): V =
    withResource[Session, V](driver.session())(f)
}
