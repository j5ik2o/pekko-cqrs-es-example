package io.github.j5ik2o.pcqrses.command.domain.users

trait EmailAddress {

  def asString: String
}

object EmailAddress {

  private[users] final val MaxLength = 100

  def apply(value: String): EmailAddress =
    parseFromString(value) match {
      case Right(result) => result
      case Left(error) => throw new IllegalArgumentException(error.toString)
    }

  def unapply(self: EmailAddress): Option[String] = Some(self.asString)

  private final val EmailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".r

  def parseFromString(value: String): Either[EmailAddressError, EmailAddress] =
    if (value.isEmpty) {
      Left(EmailAddressError.Empty)
    } else if (value.length > MaxLength) {
      Left(EmailAddressError.TooLong(value.length))
    } else if (!EmailRegex.matches(value)) {
      Left(EmailAddressError.InvalidFormat)
    } else {
      Right(EmailAddressImpl(value))
    }

  private final case class EmailAddressImpl(private val value: String) extends EmailAddress {
    override def asString: String = value
  }

}
