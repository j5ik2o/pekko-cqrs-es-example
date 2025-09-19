package io.github.j5ik2o.pcqrses.driver.dao.slick.support

import slick.jdbc.JdbcProfile

trait SlickDaoSupport {
  val profile: JdbcProfile
  import profile.api._
  
  // Common database operations and utilities
  def withTransaction[T](action: DBIO[T]): DBIO[T] = {
    action.transactionally
  }
}