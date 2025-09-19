package io.github.j5ik2o.pcqrses.query.interfaceAdapter.dao
// このファイルは自動生成ファイルなので直接編集しないでください。
import slick.lifted.PrimaryKey
import slick.lifted.ProvenShape

trait UserAccountsComponent extends SlickDaoSupport with UserAccountsExtensions {
  import profile.api._

  final case class UserAccountsRecord(
    id: String,
    firstName: String,
    lastName: String,
    createdAt: java.sql.Timestamp,
    updatedAt: java.sql.Timestamp
  ) extends Record

  final case class UserAccounts(tag: Tag)
    extends TableBase[UserAccountsRecord](tag, "user_accounts") {
    def id: Rep[String] = column[String]("id")
    def firstName: Rep[String] = column[String]("first_name")
    def lastName: Rep[String] = column[String]("last_name")
    def createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
    def updatedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("updated_at")

    def pk: PrimaryKey = primaryKey("pk", id)

    override def * : ProvenShape[UserAccountsRecord] =
      (id, firstName, lastName, createdAt, updatedAt) <> (
        UserAccountsRecord.apply,
        UserAccountsRecord.unapply)
  }

  object UserAccountsDao extends TableQuery(UserAccounts.apply) with UserAccountsDaoExtensions

}
