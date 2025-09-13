package io.github.j5ik2o.pcqrses.domain.users

trait LastName {
  def asString: String
}

object LastName {
  def apply(value: String): LastName = parseFromString(value) match {
    case Right(v) => v
    case Left(e) => throw new IllegalArgumentException(e.message)
  }

  def parseFromString(value: String): Either[LastNameError, LastName] =
    if (value.length > 256) Left(LastNameError.TooLong)
    else Right(LastNameImpl(value))

  private case class LastNameImpl(value: String) extends LastName {
    override def asString: String = value
  }
}
