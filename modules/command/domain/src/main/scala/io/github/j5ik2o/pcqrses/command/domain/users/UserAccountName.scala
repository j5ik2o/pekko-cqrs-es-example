package io.github.j5ik2o.pcqrses.command.domain.users

trait UserAccountName {
  def breachEncapsulationOfFirstName: FirstName
  def breachEncapsulationOfLastName: LastName
  def asString: String
}

object UserAccountName {
  def apply(firstName: FirstName, lastName: LastName): UserAccountName =
    UserAccountNameImpl(firstName, lastName)

  def unapply(self: UserAccountName): Option[(FirstName, LastName)] =
    Some((self.breachEncapsulationOfFirstName, self.breachEncapsulationOfLastName))

  private final case class UserAccountNameImpl(firstName: FirstName, lastName: LastName)
    extends UserAccountName {
    override def breachEncapsulationOfFirstName: FirstName = firstName
    override def breachEncapsulationOfLastName: LastName = lastName
    override def asString: String = s"${firstName.asString} ${lastName.asString}"
  }
}
