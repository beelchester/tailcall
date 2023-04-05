package tailcall.runtime

import tailcall.runtime.http.Method
import tailcall.runtime.model.{Endpoint, TSchema}
import tailcall.runtime.transcoder.Endpoint2Config.NameGenerator
import tailcall.runtime.transcoder.{Endpoint2Config, Transcoder}
import zio.test.{Spec, TestEnvironment, TestResult, ZIOSpecDefault, assertTrue}
import zio.{Scope, ZIO}

object Endpoint2GraphQLSchemaSpec extends ZIOSpecDefault with Endpoint2Config {
  private val User = TSchema
    .obj("username" -> TSchema.Str, "id" -> TSchema.Num, "name" -> TSchema.Str, "email" -> TSchema.Str)

  private val InputUser = TSchema.obj("username" -> TSchema.Str, "name" -> TSchema.Str, "email" -> TSchema.Str)

  private val jsonEndpoint = Endpoint.make("jsonplaceholder.typicode.com").withHttps

  def assertSchema(endpoint: Endpoint, encodeDirectives: Boolean)(expected: String): ZIO[Any, String, TestResult] = {
    val schema = Transcoder.toGraphQLSchema(endpoint, encodeDirectives, NameGenerator.incremental)
      .map(_.stripMargin.trim)
    for { result <- schema.toZIO } yield assertTrue(result == expected)
  }

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("endpoint to graphql schema")(
      test("output schema") {
        val endpoint = jsonEndpoint.withHttps.withOutput(Option(User.arr)).withPath("/users")
        val expected = """
                         |schema @server(baseURL: "https://jsonplaceholder.typicode.com") {
                         |  query: Query
                         |}
                         |
                         |type Query {
                         |  fieldType_1: [Type_1!] @steps(value: [{http: {path: "/users"}}])
                         |}
                         |
                         |type Type_1 {
                         |  username: String!
                         |  id: Int!
                         |  name: String!
                         |  email: String!
                         |}
                         |""".stripMargin
        assertSchema(endpoint, true)(expected.trim)
      },
      test("nested output schema") {
        val output   = TSchema.obj("a" -> TSchema.obj("b" -> TSchema.obj("c" -> TSchema.int)))
        val endpoint = Endpoint.make("abc.com").withOutput(Option(output)).withPath("/abc")
        val expected = """
                         |schema @server(baseURL: "http://abc.com") {
                         |  query: Query
                         |}
                         |
                         |type Query {
                         |  fieldType_1: Type_1! @steps(value: [{http: {path: "/abc"}}])
                         |}
                         |
                         |type Type_1 {
                         |  a: Type_2!
                         |}
                         |
                         |type Type_2 {
                         |  b: Type_3!
                         |}
                         |
                         |type Type_3 {
                         |  c: Int!
                         |}
                         |
                         |""".stripMargin
        assertSchema(endpoint, true)(expected.trim)
      },
      test("argument schema") {
        val endpoint = jsonEndpoint.withOutput(Option(User.opt)).withInput(Option(TSchema.obj("userId" -> TSchema.Num)))
          .withPath("/user")

        val expected = """
                         |schema @server(baseURL: "https://jsonplaceholder.typicode.com") {
                         |  query: Query
                         |}
                         |
                         |input Type_2 {
                         |  userId: Int!
                         |}
                         |
                         |type Query {
                         |  fieldType_1(value: Type_2!): Type_1 @steps(value: [{http: {path: "/user"}}])
                         |}
                         |
                         |type Type_1 {
                         |  username: String!
                         |  id: Int!
                         |  name: String!
                         |  email: String!
                         |}
                         |""".stripMargin
        assertSchema(endpoint, true)(expected.trim)
      },
      test("nested argument schema") {
        val endpoint = Endpoint.make("abc.com")
          .withInput(Option(TSchema.obj("a" -> TSchema.obj("b" -> TSchema.obj("c" -> TSchema.int)))))
          .withOutput(Option(TSchema.int))

        val expected = """
                         |schema @server(baseURL: "http://abc.com") {
                         |  query: Query
                         |}
                         |
                         |input Type_1 {
                         |  a: Type_2!
                         |}
                         |
                         |input Type_2 {
                         |  b: Type_3!
                         |}
                         |
                         |input Type_3 {
                         |  c: Int!
                         |}
                         |
                         |type Query {
                         |  fieldInt(value: Type_1!): Int! @steps(value: [{http: {path: ""}}])
                         |}
                         |""".stripMargin
        assertSchema(endpoint, true)(expected.trim)
      },
      test("mutation schema") {
        val endpoint = jsonEndpoint.withOutput(Option(User)).withInput(Option(InputUser)).withPath("/user")
          .withMethod(Method.POST)

        val expected = """
                         |schema @server(baseURL: "https://jsonplaceholder.typicode.com") {
                         |  query: Query
                         |  mutation: Mutation
                         |}
                         |
                         |input Type_2 {
                         |  username: String!
                         |  name: String!
                         |  email: String!
                         |}
                         |
                         |type Mutation {
                         |  fieldType_1(value: Type_2!): Type_1! @steps(value: [{http: {path: "/user",method: "POST"}}])
                         |}
                         |
                         |type Query
                         |
                         |type Type_1 {
                         |  username: String!
                         |  id: Int!
                         |  name: String!
                         |  email: String!
                         |}
                         |""".stripMargin
        assertSchema(endpoint, true)(expected.trim)
      },
    )
}