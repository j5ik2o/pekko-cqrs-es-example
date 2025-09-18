package io.github.j5ik2o.pcqrses.infrastructure.effect

import zio.{Duration, IO, Runtime, Task, Unsafe, ZIO}

import scala.concurrent.{ExecutionContext, Future}

/**
 * PekkoとZIOの相互運用ユーティリティ
 */
object PekkoInterop {

  /**
   * FutureをZIOに変換
   * エラーはThrowableとして扱う
   */
  def fromFuture[A](future: => Future[A])(implicit ec: ExecutionContext): Task[A] =
    ZIO.fromFuture(implicit ec => future)

  /**
   * Future[Either[E, A]]をZIOに変換
   * Leftの場合はZIOのエラーチャネルに、RightはZIOの成功値に
   * FutureのThrowableもEitherのエラーと統合
   */
  def fromFutureEither[E, A](future: => Future[Either[E, A]], wrapThrowable: Throwable => E)(implicit ec: ExecutionContext): IO[E, A] =
    ZIO.fromFuture(implicit ec => future)
      .mapError(wrapThrowable)
      .flatMap {
        case Right(value) => ZIO.succeed(value)
        case Left(error) => ZIO.fail(error)
      }

  /**
   * Future[Option[A]]をZIOに変換
   * Noneの場合は指定されたエラーを返す
   * FutureのThrowableもオプションのエラーと統合
   */
  def fromFutureOption[E, A](future: => Future[Option[A]], ifNone: => E, wrapThrowable: Throwable => E)(implicit ec: ExecutionContext): IO[E, A] =
    ZIO.fromFuture(implicit ec => future)
      .mapError(wrapThrowable)
      .flatMap {
        case Some(value) => ZIO.succeed(value)
        case None => ZIO.fail(ifNone)
      }

  /**
   * ZIOをFutureに変換（Runtime必要）
   * エラーは例外として投げられる
   */
  def toFuture[E, A](zio: IO[E, A])(implicit runtime: Runtime[Any]): Future[A] =
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.runToFuture(zio.mapError {
        case t: Throwable => t
        case e => new RuntimeException(s"ZIO error: $e")
      })
    }

  /**
   * ZIOをFuture[Either[E, A]]に変換
   * エラーをEitherのLeftとして保持
   */
  def toFutureEither[E, A](zio: IO[E, A])(implicit runtime: Runtime[Any]): Future[Either[E, A]] =
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.runToFuture(zio.either)
    }

  /**
   * 複数のFutureを並行実行してZIOに変換
   */
  def collectPar[A](futures: Iterable[Future[A]])(implicit ec: ExecutionContext): Task[List[A]] =
    ZIO.collectAllPar(futures.map(fromFuture(_)).toList)

  /**
   * タイムアウト付きのFuture変換
   */
  def fromFutureWithTimeout[A](future: => Future[A], timeout: Duration)(implicit ec: ExecutionContext): Task[A] =
    fromFuture(future).timeout(timeout).flatMap {
      case Some(value) => ZIO.succeed(value)
      case None => ZIO.fail(new java.util.concurrent.TimeoutException(s"Future timed out after $timeout"))
    }
}