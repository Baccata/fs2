package fs2

import cats.implicits._
import cats.effect.{IO, Resource}
import cats.effect.concurrent.{Deferred, Ref}
import scala.concurrent.duration._

class ResourceCompilationSpec extends AsyncFs2Spec {

  "compile.toResource - concurrently" in {
    pending
    val prog: Resource[IO, IO[Unit]] =
      Stream
        .eval(Deferred[IO, Unit].product(Deferred[IO, Unit]))
        .flatMap {
          case (startCondition, waitForStream) =>
            val worker = Stream.eval(startCondition.get) ++ Stream.eval(waitForStream.complete(()))
            val result = startCondition.complete(()) >> waitForStream.get

            Stream.emit(result).concurrently(worker)
        }
        .compile
        .toResource
        .lastOrError

    prog.use(x => x).timeout(5.seconds).unsafeToFuture
  }

  "compile.toResource - onFinalise" in {
    pending
    val expected = List(
      "stream - start",
      "stream - done",
      "io - done",
      "io - start",
      "resource - start",
      "resource - done"
    )

    Ref[IO]
      .of(List.empty[String])
      .flatMap { st =>
        def record(s: String): IO[Unit] = st.update(_ :+ s)

        def stream =
          Stream
            .emit("stream - start")
            .onFinalize(record("stream - done"))
            .evalMap(x => record(x))
            .compile
            .lastOrError

        def io =
          Stream
            .emit("io - start")
            .onFinalize(record("io - done"))
            .compile
            .lastOrError
            .flatMap(x => record(x))

        def resource =
          Stream
            .emit("resource - start")
            .onFinalize(record("resource - done"))
            .compile
            .toResource
            .lastOrError
            .use(x => record(x))

        stream >> io >> resource >> st.get
      }
      .unsafeToFuture
      .map(_ shouldBe expected)
  }
}

object ResourceCompilationSpec {

  /** This should compile */
  val pure: List[Int] = Stream.range(0, 5).compile.toList
  val io: IO[List[Int]] = Stream.range(0, 5).covary[IO].compile.toList
  val resource: Resource[IO, List[Int]] = Stream.range(0, 5).covary[IO].compile.toResource.toList
}
