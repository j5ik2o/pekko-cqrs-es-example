package io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import io.circe.Json

import scala.concurrent.ExecutionContext

class GraphQLServiceSpec extends AsyncWordSpec with Matchers {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "GraphQLService" should {
    "handle syntax errors gracefully" in {
      // GraphQLServiceの詳細なテストは、実際のデータベース接続が必要なため、
      // 統合テストで実施することを推奨
      // ここでは基本的な構造のテストのみ実施

      val invalidQuery = """
        query {
          getUserAccounts {
            id
            firstName
            // 不正な構文
          }
      """

      // Sangriaのパーサーをテスト
      val parseResult = sangria.parser.QueryParser.parse(invalidQuery)

      parseResult.isFailure shouldBe true
      parseResult.failed.get shouldBe a[sangria.parser.SyntaxError]
      succeed
    }

    "parse valid GraphQL query" in {
      val validQuery = """
        query {
          getUserAccounts {
            id
            firstName
          }
        }
      """

      val parseResult = sangria.parser.QueryParser.parse(validQuery)

      parseResult.isSuccess shouldBe true
      succeed
    }

    "parse query with variables" in {
      val queryWithVars = """
        query($id: String!) {
          getUserAccount(userAccountId: $id) {
            id
            firstName
            lastName
          }
        }
      """

      val parseResult = sangria.parser.QueryParser.parse(queryWithVars)

      parseResult.isSuccess shouldBe true
      succeed
    }

    "parse introspection query" in {
      val introspectionQuery = """
        query {
          __schema {
            types {
              name
              kind
            }
          }
        }
      """

      val parseResult = sangria.parser.QueryParser.parse(introspectionQuery)

      parseResult.isSuccess shouldBe true
      succeed
    }
  }
}