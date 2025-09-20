package io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.schema

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.ResolverContext
import sangria.schema.*

/**
 * GraphQL型定義
 */
trait TypeDefinitions extends ScalarTypes {

  /**
   * CreateUserAccount結果型（IDのみを返す）
   */
  val CreateUserAccountResultType: ObjectType[ResolverContext, CreateUserAccountResult] =
    ObjectType(
      "CreateUserAccountResult",
      "Result of creating a user account",
      fields[ResolverContext, CreateUserAccountResult](
        Field("id", StringType, resolve = _.value.id)
      )
    )

  /**
   * CreateUserAccountInput引数（直接InputFieldsを定義）
   */
  val CreateUserAccountInputArg: Argument[CreateUserAccountInput] = {
    import sangria.marshalling.circe.*
    import io.circe.generic.semiauto.*

    implicit val createUserAccountInputDecoder: io.circe.Decoder[CreateUserAccountInput] =
      deriveDecoder[CreateUserAccountInput]
    implicit val createUserAccountInputEncoder: io.circe.Encoder[CreateUserAccountInput] =
      deriveEncoder[CreateUserAccountInput]
    implicit val createUserAccountInputFromInput
      : sangria.marshalling.FromInput[CreateUserAccountInput] =
      circeDecoderFromInput[CreateUserAccountInput]

    Argument(
      "input",
      InputObjectType[CreateUserAccountInput](
        "CreateUserAccountInput",
        "Input for creating a user account",
        List(
          InputField("firstName", StringType),
          InputField("lastName", StringType),
          InputField("emailAddress", StringType)
        )
      )
    )
  }
}

/**
 * CreateUserAccount結果型
 */
case class CreateUserAccountResult(
  id: String
)

/**
 * CreateUserAccount入力型
 */
case class CreateUserAccountInput(
  firstName: String,
  lastName: String,
  emailAddress: String
)
