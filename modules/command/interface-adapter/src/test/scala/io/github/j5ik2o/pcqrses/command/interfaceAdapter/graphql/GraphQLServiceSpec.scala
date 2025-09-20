package io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.ExecutionContext

class GraphQLServiceSpec extends AsyncWordSpec with Matchers {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "GraphQLService" should {
    "handle syntax errors gracefully" in {
      val invalidMutation = """
        mutation {
          createUserAccount(input: {
            name: "Test User"
            emailAddress: "test@example.com"
            // 不正な構文
          }) {
            id
          }
      """

      // Sangriaのパーサーをテスト
      val parseResult = sangria.parser.QueryParser.parse(invalidMutation)

      parseResult.isFailure shouldBe true
      parseResult.failed.get shouldBe a[sangria.parser.SyntaxError]
      succeed
    }

    "parse valid GraphQL mutation" in {
      val validMutation = """
        mutation {
          createUserAccount(input: {
            name: "Test User"
            emailAddress: "test@example.com"
          }) {
            id
            name
            emailAddress
          }
        }
      """

      val parseResult = sangria.parser.QueryParser.parse(validMutation)

      parseResult.isSuccess shouldBe true
      succeed
    }

    "parse mutation with variables" in {
      val mutationWithVars = """
        mutation($input: CreateUserAccountInput!) {
          createUserAccount(input: $input) {
            id
            name
            emailAddress
          }
        }
      """

      val parseResult = sangria.parser.QueryParser.parse(mutationWithVars)

      parseResult.isSuccess shouldBe true
      succeed
    }

    "parse introspection query" in {
      val introspectionQuery = """
        query {
          __schema {
            mutationType {
              name
              fields {
                name
                description
              }
            }
          }
        }
      """

      val parseResult = sangria.parser.QueryParser.parse(introspectionQuery)

      parseResult.isSuccess shouldBe true
      succeed
    }

    "handle query with empty Query type" in {
      // Command側はQueryが空実装なので、Mutation中心のテストを行う
      val emptyQuery = """
        query {
          __typename
        }
      """

      val parseResult = sangria.parser.QueryParser.parse(emptyQuery)

      parseResult.isSuccess shouldBe true
      succeed
    }
  }
}
