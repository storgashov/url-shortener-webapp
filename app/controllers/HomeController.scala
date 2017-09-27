package controllers

import javax.inject._

import play.api.data.Forms._
import play.api.data._
import play.api.mvc._
import services.UrlShortenerService

@Singleton
class HomeController @Inject()(components: MessagesControllerComponents,
                               urlShortenerService: UrlShortenerService)
  extends MessagesAbstractController(components) {

  private lazy val urlForm = Form(single("url" -> nonEmptyText))

  /** Returns the home page with the URL input field.
    */
  def index(url: Option[String]) = Action { implicit request: MessagesRequestHeader =>
    Ok(views.html.index(urlForm, url))
  }

  /** Receives URL from the input field, shorten it and shows the result URL.
    */
  def submitUrl() = Action { implicit request =>
    urlForm.bindFromRequest().fold(
      formWithErrors => {
        BadRequest(views.html.index(formWithErrors))
      },
      url => {
        val shortUrlId = urlShortenerService.shortenUrl(url)
        // TODO: make a real URL from URL ID.
        Redirect(routes.HomeController.index(Some(shortUrlId)))
      }
    )
  }
}
