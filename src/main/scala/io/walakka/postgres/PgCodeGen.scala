package io.walakka.postgres
object PgCodeGen extends App {
  slick.codegen.SourceCodeGenerator.main(
    Array(
      "slick.jdbc.PostgresProfile",
      "org.postgresql.Driver",
      "jdbc:postgresql://localhost:5432/postgres",
      "src",
      "io.walakka.postgres.models.gen",
      "postgres",
      "postgres"
    )
  )
}
