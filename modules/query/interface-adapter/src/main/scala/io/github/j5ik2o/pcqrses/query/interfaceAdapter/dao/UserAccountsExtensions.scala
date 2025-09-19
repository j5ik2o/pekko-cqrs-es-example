package io.github.j5ik2o.pcqrses.query.interfaceAdapter.dao

trait UserAccountsExtensions {
  this: UserAccountsComponent =>
  trait UserAccountsDaoExtensions { dao: UserAccountsDao.type =>
    import profile.api._
    def findAll(): DBIO[Seq[UserAccountsRecord]] =
      dao.result
    def findByIds(ids: Seq[String]): DBIO[Seq[UserAccountsRecord]] =
      dao.filter(_.id inSet ids).result
  }
}
