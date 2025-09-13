package io.github.j5ik2o.pcqrses.domain.staff

/**
 * EmailAddress生成時のバリデーションエラー。
 *
 * メールアドレスの形式エラーを型安全に表現し、 不正なアドレスによるシステムエラーを防止する。
 */
enum EmailAddressError extends DomainError {
  case Empty
  case TooLong(actualLength: Int)
  case InvalidFormat

  def message: String = this match {
    case Empty => "Email address cannot be empty"
    case TooLong(actualLength) =>
      s"Email address is too long: $actualLength characters (max ${EmailAddress.MaxLength} characters)"
    case InvalidFormat => "Invalid email format"
  }
}
