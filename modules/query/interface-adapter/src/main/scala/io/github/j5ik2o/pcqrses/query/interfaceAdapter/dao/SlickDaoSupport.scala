package io.github.j5ik2o.pcqrses.query.interfaceAdapter.dao

import slick.jdbc.JdbcProfile

trait SlickDaoSupport {
  val profile: JdbcProfile
  import profile.api.*

  trait Record

  trait SoftDeletableRecord extends Record {
    val status: String
  }

  abstract class TableBase[T](
    _tableTag: profile.api.Tag,
    _tableName: String,
    _schemaName: Option[String] = None)
    extends profile.api.Table[T](_tableTag, _schemaName, _tableName)

  trait SoftDeletableTableSupport[T] {
    this: TableBase[T] =>
    def status: profile.api.Rep[String]
  }
}
