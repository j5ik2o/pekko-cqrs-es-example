package io.github.j5ik2o.pcqrses.domain.support

/**
 * ドメインエラーの共通インターフェース
 *
 * すべてのドメインエラーが実装すべきトレイト。 エラーメッセージの統一的な取得方法を提供し、 エラーハンドリングの型安全性を向上させる。
 */
trait DomainError {

  /**
   * エラーメッセージを取得する
   *
   * @return
   *   人間が読めるエラーメッセージ
   */
  def message: String
}
