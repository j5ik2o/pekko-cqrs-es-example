package io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.schema

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.ResolverContext
import sangria.schema.*

import java.time.Instant

/**
 * GraphQL型定義
 */
trait TypeDefinitions extends ScalarTypes {
  
  /**
   * UserAccount型
   */
  val UserAccountType: ObjectType[ResolverContext, UserAccountOutput] = ObjectType(
    "UserAccount",
    "A user account",
    fields[ResolverContext, UserAccountOutput](
      Field("id", StringType, resolve = _.value.id),
      Field("name", StringType, resolve = _.value.name),
      Field("emailAddress", StringType, resolve = _.value.emailAddress),
      Field("createdAt", DateTimeType, resolve = _.value.createdAt),
      Field("updatedAt", OptionType(DateTimeType), resolve = _.value.updatedAt)
    )
  )
  
  /**
   * CreateUserAccountInput引数（直接InputFieldsを定義）
   */
  val CreateUserAccountInputArg: Argument[CreateUserAccountInput] = {
    import sangria.marshalling.circe.*
    import io.circe.generic.semiauto.*
    
    implicit val createUserAccountInputDecoder: io.circe.Decoder[CreateUserAccountInput] = deriveDecoder[CreateUserAccountInput]
    implicit val createUserAccountInputEncoder: io.circe.Encoder[CreateUserAccountInput] = deriveEncoder[CreateUserAccountInput]
    implicit val createUserAccountInputFromInput: sangria.marshalling.FromInput[CreateUserAccountInput] = circeDecoderFromInput[CreateUserAccountInput]
    
    Argument(
      "input",
      InputObjectType[CreateUserAccountInput](
        "CreateUserAccountInput",
        "Input for creating a user account",
        List(
          InputField("name", StringType),
          InputField("emailAddress", StringType)
        )
      )
    )
  }
}

/**
 * UserAccount出力型
 */
case class UserAccountOutput(
  id: String,
  name: String,
  emailAddress: String,
  createdAt: Instant,
  updatedAt: Option[Instant] = None
)

/**
 * CreateUserAccount入力型
 */
case class CreateUserAccountInput(
  name: String,
  emailAddress: String
)
