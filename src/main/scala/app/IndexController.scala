package app

import util._
import util.Implicits._
import service._
import jp.sf.amateras.scalatra.forms._

class IndexController extends IndexControllerBase 
  with RepositoryService with ActivityService with AccountService with UsersAuthenticator

trait IndexControllerBase extends ControllerBase {
  self: RepositoryService with ActivityService with AccountService with UsersAuthenticator =>

  case class SignInForm(userName: String, password: String)

  val form = mapping(
    "userName" -> trim(label("Username", text(required))),
    "password" -> trim(label("Password", text(required)))
  )(SignInForm.apply)

  get("/"){
    val loginAccount = context.loginAccount

    html.index(getRecentActivities(),
      getVisibleRepositories(loginAccount, baseUrl),
      loadSystemSettings(),
      loginAccount.map{ account => getUserRepositories(account.userName, baseUrl) }.getOrElse(Nil)
    )
  }

  get("/signin"){
    val redirect = params.get("redirect")
    if(redirect.isDefined && redirect.get.startsWith("/")){
      session.setAttribute(Keys.Session.Redirect, redirect.get)
    }
    html.signin(loadSystemSettings())
  }

  post("/signin", form){ form =>
    authenticate(loadSystemSettings(), form.userName, form.password) match {
      case Some(account) => signin(account)
      case None          => redirect("/signin")
    }
  }

  get("/signout"){
    session.invalidate
    redirect("/")
  }

  /**
   * Set account information into HttpSession and redirect.
   */
  private def signin(account: model.Account) = {
    session.setAttribute(Keys.Session.LoginAccount, account)
    updateLastLoginDate(account.userName)

    session.getAndRemove[String](Keys.Session.Redirect).map { redirectUrl =>
      if(redirectUrl.replaceFirst("/$", "") == request.getContextPath){
        redirect("/")
      } else {
        redirect(redirectUrl)
      }
    }.getOrElse {
      redirect("/")
    }
  }

  /**
   * JSON API for collaborator completion.
   *
   * TODO Move to other controller?
   */
  get("/_user/proposals")(usersOnly {
    contentType = formats("json")
    org.json4s.jackson.Serialization.write(
      Map("options" -> getAllUsers().filter(!_.isGroupAccount).map(_.userName).toArray)
    )
  })


}
