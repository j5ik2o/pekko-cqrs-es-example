package io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.validators

import zio.prelude.Validation
import zio.NonEmptyChunk

object QueryInputValidator {
  type ErrorInfo = String
  
  /**
   * ユーザーアカウントIDのバリデーション
   */
  def validateUserAccountId(id: String): Validation[ErrorInfo, String] = {
    if (id.trim.isEmpty) {
      Validation.fail("User account ID cannot be empty")
    } else if (!id.matches("^[a-zA-Z0-9-]+$")) {
      Validation.fail("User account ID contains invalid characters")
    } else {
      Validation.succeed(id)
    }
  }
  
  /**
   * 複数のユーザーアカウントIDのバリデーション
   */
  def validateUserAccountIds(ids: Seq[String]): Validation[ErrorInfo, Seq[String]] = {
    if (ids.isEmpty) {
      Validation.fail("User account IDs list cannot be empty")
    } else if (ids.length > 100) {
      Validation.fail("Cannot query more than 100 user accounts at once")
    } else {
      ids.map(validateUserAccountId).foldLeft(Validation.succeed(Seq.empty[String])) { 
        (acc, validation) =>
          Validation.validateWith(acc, validation)((accIds, id) => accIds :+ id)
      }
    }
  }
  
  /**
   * 検索文字列のバリデーション
   */
  def validateSearchTerm(searchTerm: String): Validation[ErrorInfo, String] = {
    val trimmed = searchTerm.trim
    if (trimmed.isEmpty) {
      Validation.fail("Search term cannot be empty")
    } else if (trimmed.length < 2) {
      Validation.fail("Search term must be at least 2 characters long")
    } else if (trimmed.length > 100) {
      Validation.fail("Search term cannot exceed 100 characters")
    } else {
      Validation.succeed(trimmed)
    }
  }
}