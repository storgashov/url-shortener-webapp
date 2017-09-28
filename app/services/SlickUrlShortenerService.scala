package services

import java.nio.ByteBuffer
import java.util.Base64
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.H2Profile
import slick.jdbc.H2Profile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object SlickUrlShortenerService {

  class Urls(tag: Tag) extends Table[(Long, String)](tag, "URLS") {

    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def originalUrl = column[String]("original_url", O.Unique)

    override def * = (id, originalUrl)
  }

  val urls = TableQuery[Urls]
}

import services.SlickUrlShortenerService._

/** Slick-based implementation of `UrlShortenerService`
  */
@Singleton
class SlickUrlShortenerService @Inject()(override protected val dbConfigProvider: DatabaseConfigProvider)
                                        (implicit ex: ExecutionContext)
  extends UrlShortenerService
    with HasDatabaseConfigProvider[H2Profile] {

  private val dbInit = db.run(urls.schema.create)

  // Allows to run any queries to DB only when it is completely initialized.
  private def initialized[T](block: => Future[T]): Future[T] = dbInit.flatMap(_ => block)

  override def shortenUrl(sourceUrl: String): Future[String] = initialized {
    // TODO: build a single-expression query instead of executing two expressions transactionally.
    db.
      run {
        {
          // Search for existing URLs.
          for (url <- urls if url.originalUrl === sourceUrl) yield url.id
        }.
          result.headOption.
          flatMap {
            case Some(existingUrlId) =>
              // There is existing URL in the DB already, just return it.
              DBIO.successful(existingUrlId)
            case None =>
              // The existing URL not found, create a new one.
              urls.returning(urls.map(_.id)) += (0, sourceUrl)
          }.
          transactionally
      }.
      map(encodeId)
  }

  override def restoreUrl(urlId: String): Future[String] = initialized {
    decodeId(urlId).
      fold(Future.failed[String](new NoSuchElementException(urlId))) { decodedUrlId =>
        db.
          run {
            {
              // Search for URL entry by its ID.
              for (url <- urls if url.id === decodedUrlId) yield url.originalUrl
            }.
              result.map(_.headOption)
          }.
          map {
            case Some(foundUrl) => foundUrl
            case None => throw new NoSuchElementException(urlId)
          }
      }
  }

  private def createByteBuffer() = {
    ByteBuffer.allocate(8)
  }

  private def encodeId(id: Long): String = {
    val idBytes = createByteBuffer().putLong(id).array()
    Base64.getUrlEncoder.encodeToString(idBytes)
  }

  private def decodeId(encodedId: String): Option[Long] = {
    Try {
      val idBytes = Base64.getUrlDecoder.decode(encodedId)
      createByteBuffer().put(idBytes).getLong(0)
    }.toOption
  }
}
