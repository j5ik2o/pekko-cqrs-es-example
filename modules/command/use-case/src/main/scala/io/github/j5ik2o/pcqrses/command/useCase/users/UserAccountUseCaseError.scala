package io.github.j5ik2o.pcqrses.command.useCase.users

enum UserAccountUseCaseError extends UseCaseError {
  case CreationFailed(msg: String)
  case UnexpectedError(msg: String, cause: Option[Throwable])

  override def message: String = this match {
    case CreationFailed(msg) => msg
    case UnexpectedError(msg, _) => msg
  }
}
