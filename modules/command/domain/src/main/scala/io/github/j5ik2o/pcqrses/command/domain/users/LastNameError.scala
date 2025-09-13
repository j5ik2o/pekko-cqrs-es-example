package io.github.j5ik2o.pcqrses.command.domain.users

import io.github.j5ik2o.pcqrses.command.domain.support.DomainError

enum LastNameError extends DomainError {
  case TooLong

  override def message: String =
    this match {
      case TooLong => "Last name is too long"
    }
}
