package io.github.j5ik2o.pcqrses.domain.staff

/**
 * メールアドレスを表現するValue Object。
 *
 * スタッフへの主要な連絡手段として機能し、 システム通知やパスワードリセット等の重要なプロセスで使用される。 RFC5322準拠のバリデーションにより有効性を保証する。
 */
trait EmailAddress {

  /** 文字列表現として取得する。メール送信やUI表示で使用 */
  def asString: String
}

/**
 * EmailAddressのファクトリーオブジェクト。
 *
 * RFC5322準拠のバリデーション機能により、 有効なメールアドレスのみを生成することを保証する。
 */
object EmailAddress {

  private[staff] final val MaxLength = 100

  /**
   * 文字列からEmailAddressを作成する。
   *
   * @param value
   *   メールアドレスの文字列
   * @return
   *   作成されたEmailAddressインスタンス
   * @throws IllegalArgumentException
   *   バリデーション失敗時
   */
  def apply(value: String): EmailAddress =
    parseFromString(value) match {
      case Right(result) => result
      case Left(error) => throw new IllegalArgumentException(error.toString)
    }

  def unapply(self: EmailAddress): Option[String] = Some(self.asString)

  private final val EmailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".r

  /**
   * バリデーション付きでEmailAddressをパースする。
   *
   * 外部入力値の検証が必要な場面で使用し、 型安全なエラーハンドリングを提供する。
   *
   * @param value
   *   パース対象の文字列
   * @return
   *   パース結果。成功時はEmailAddress、失敗時はEmailAddressError
   */
  def parseFromString(value: String): Either[EmailAddressError, EmailAddress] =
    if (value.isEmpty) {
      Left(EmailAddressError.Empty)
    } else if (value.length > MaxLength) {
      Left(EmailAddressError.TooLong(value.length))
    } else if (!EmailRegex.matches(value)) {
      Left(EmailAddressError.InvalidFormat)
    } else {
      Right(EmailAddressImpl(value))
    }

  private final case class EmailAddressImpl(private val value: String) extends EmailAddress {
    override def asString: String = value
  }

}
