package io.github.j5ik2o.pcqrses.command.domain.basic

import io.github.j5ik2o.pcqrses.command.domain.support.DomainError

enum DateTimeError extends DomainError {
  case InvalidFormat(value: String)

  override def message: String =
    this match {
      case InvalidFormat(value) => s"Invalid date time format: $value"
    }
}
