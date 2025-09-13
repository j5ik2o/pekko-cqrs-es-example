package io.github.j5ik2o.pcqrses.command.domain.users

import io.github.j5ik2o.pcqrses.command.domain.support.DomainError

enum FirstNameError extends DomainError {
  case TooLong

  override def message: String =
    this match {
      case TooLong => "First name is too long"
    }
}
