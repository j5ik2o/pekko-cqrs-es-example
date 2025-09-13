package io.github.j5ik2o.pcqrses.command.domain.users

import io.github.j5ik2o.pcqrses.command.domain.support.DomainError

enum EmailAddressError extends DomainError {
  case Empty
  case TooLong(actualLength: Int)
  case InvalidFormat

  override def message: String = this match {
    case Empty => "Email address cannot be empty"
    case TooLong(actualLength) =>
      s"Email address is too long: $actualLength characters (max ${EmailAddress.MaxLength} characters)"
    case InvalidFormat => "Invalid email format"
  }
}
