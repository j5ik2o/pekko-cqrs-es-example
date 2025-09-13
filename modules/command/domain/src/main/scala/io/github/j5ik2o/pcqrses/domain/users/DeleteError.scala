package io.github.j5ik2o.pcqrses.domain.users

import io.github.j5ik2o.pcqrses.domain.support.DomainError

enum DeleteError extends DomainError{

  case AlreadyDeleted

  override def message: String = this match {
    case AlreadyDeleted => "The user has already been deleted"
  }
}
