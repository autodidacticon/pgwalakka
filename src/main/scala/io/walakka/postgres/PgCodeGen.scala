package io.walakka.postgres

import slick.jdbc.PostgresProfile._
object PgCodeGen extends App {
  slick.codegen.SourceCodeGenerator.main(
    Array("slick.jdbc.PostgresProfile", "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/postgres", "src", "io.pgwalakka.postgres.models", "postgres", "postgres")
  )
}
