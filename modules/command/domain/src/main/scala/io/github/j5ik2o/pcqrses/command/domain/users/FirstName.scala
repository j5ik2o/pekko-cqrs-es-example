package io.github.j5ik2o.pcqrses.command.domain.users

trait FirstName {
  def asString: String
}

object FirstName {
  def apply(value: String): FirstName = parseFromString(value) match {
    case Right(v) => v
    case Left(e) => throw new IllegalArgumentException(e.message)
  }

  def parseFromString(value: String): Either[FirstNameError, FirstName] =
    if (value.length > 256) Left(FirstNameError.TooLong)
    else Right(FirstNameImpl(value))

  private case class FirstNameImpl(value: String) extends FirstName {
    override def asString: String = value
  }
}
