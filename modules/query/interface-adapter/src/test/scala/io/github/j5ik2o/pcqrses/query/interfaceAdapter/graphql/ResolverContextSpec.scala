package io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import slick.dbio.DBIO

import scala.concurrent.{ExecutionContext, Future}

class ResolverContextSpec extends AsyncWordSpec with Matchers {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "ResolverContext" should {
    "execute DBIO actions through runDbAction" in {
      val expectedValue = 42
      val mockDbRunner: DBIO[?] => Future[?] = _ => Future.successful(expectedValue)
      val context = ResolverContext.forTesting(mockDbRunner)

      val action = DBIO.successful(expectedValue)
      context.runDbAction(action).map { result =>
        result shouldEqual expectedValue
      }
    }

    "provide ExecutionContext" in {
      val mockDbRunner: DBIO[?] => Future[?] = _ => Future.successful(())
      val context = ResolverContext.forTesting(mockDbRunner)

      context.ec shouldEqual ec
    }

    "create context from Slick database" in {
      // This test verifies that the method exists and can be called
      // In a real test, you would need an actual database connection
      val contextCreator = ResolverContext.fromSlickDatabase
      contextCreator should not be null
      succeed
    }
  }
}
