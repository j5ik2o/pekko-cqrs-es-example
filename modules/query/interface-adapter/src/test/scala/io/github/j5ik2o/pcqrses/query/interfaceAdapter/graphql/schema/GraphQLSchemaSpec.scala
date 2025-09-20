package io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.schema

import io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.ResolverContext
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import sangria.execution.Executor
import sangria.marshalling.circe.*
import io.circe.Json
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile

import scala.concurrent.{ExecutionContext, Future}

class GraphQLSchemaSpec extends AsyncWordSpec with Matchers {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "GraphQLSchema" should {
    "create a valid schema with Query type" in {
      val schema = GraphQLSchema(PostgresProfile)

      schema.schema should not be null
      schema.schema.query.name shouldEqual "Query"
      succeed
    }

    "have getUserAccount field in Query type" in {
      val schema = GraphQLSchema(PostgresProfile)
      val queryType = schema.schema.query

      val field = queryType.fieldsByName.get("getUserAccount")
      field should not be None
      field.get.head.name shouldEqual "getUserAccount"
      succeed
    }

    "have getUserAccounts field in Query type" in {
      val schema = GraphQLSchema(PostgresProfile)
      val queryType = schema.schema.query

      val field = queryType.fieldsByName.get("getUserAccounts")
      field should not be None
      field.get.head.name shouldEqual "getUserAccounts"
      succeed
    }

    "have getUserAccountsByIds field in Query type" in {
      val schema = GraphQLSchema(PostgresProfile)
      val queryType = schema.schema.query

      val field = queryType.fieldsByName.get("getUserAccountsByIds")
      field should not be None
      field.get.head.name shouldEqual "getUserAccountsByIds"
      succeed
    }

    "have searchUserAccounts field in Query type" in {
      val schema = GraphQLSchema(PostgresProfile)
      val queryType = schema.schema.query

      val field = queryType.fieldsByName.get("searchUserAccounts")
      field should not be None
      field.get.head.name shouldEqual "searchUserAccounts"
      succeed
    }

    "execute simple introspection query" in {
      val schema = GraphQLSchema(PostgresProfile)
      val mockDbRunner: DBIO[?] => Future[?] = _ => Future.successful(Seq.empty)
      val context = ResolverContext.forTesting(mockDbRunner)

      val introspectionQuery = """
        query {
          __schema {
            types {
              name
            }
          }
        }
      """

      Executor
        .execute(
          schema = schema.schema,
          queryAst = sangria.parser.QueryParser.parse(introspectionQuery).get,
          userContext = context
        )
        .map { result =>
          val json = result.asInstanceOf[Json]
          val types = json.hcursor
            .downField("data")
            .downField("__schema")
            .downField("types")
            .as[List[Json]]

          types match {
            case Right(typeList) =>
              val typeNames = typeList.flatMap(_.hcursor.get[String]("name").toOption)
              typeNames should contain("UserAccount")
              typeNames should contain("Query")
              typeNames should contain("String")
              typeNames should contain("DateTime")
            case Left(err) =>
              fail(s"Failed to parse introspection response: $err")
          }
        }
    }
  }
}
