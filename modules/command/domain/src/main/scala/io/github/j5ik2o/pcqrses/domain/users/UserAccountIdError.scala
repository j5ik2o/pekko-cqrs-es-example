package io.github.j5ik2o.pcqrses.domain.users

import io.github.j5ik2o.pcqrses.domain.support.DomainError

enum UserAccountIdError extends DomainError {
  case Empty
  case InvalidLength
  case InvalidFormat

  override def message: String = this match {
    case Empty => "Staff ID cannot be empty"
    case InvalidLength => "Staff ID must be exactly 26 characters"
    case InvalidFormat => "Invalid Staff ID format"
  }
}
