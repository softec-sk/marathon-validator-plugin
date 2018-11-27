package sk.softec.dcos.validations.example

import com.wix.accord._
import com.wix.accord.dsl._
import com.wix.accord.ViolationBuilder._
import mesosphere.marathon.state._
import org.scalatest.{FunSpec, Matchers}

import scala.util.matching.Regex

class MaintainerValidatorTest extends FunSpec with Matchers with AppValidatorTest {
  private val appValidator = validator[AppDefinition] { app =>
    app.labels is valid(MaintainerValidator)
  }

  private val sut = create(appValidator)

  describe("MaintainerValidator") {
    it("should reject App without MAINTAINER label") {
      // setup
      val app = AppDefinition(
        id = PathId("/app")
      )
      // run
      val result = sut(app)
      // verify
      result.isFailure shouldBe true
    }

    it("should reject App with empty MAINTAINER label") {
      // setup
      val app = AppDefinition(
        id = PathId("/app"),
        labels = Map("MAINTAINER" -> "")
      )
      // run
      val result = sut(app)
      // verify
      result.isFailure shouldBe true
    }

    it("should reject App with MAINTAINER label outside @softec domain") {
      // setup
      val app = AppDefinition(
        id = PathId("/app"),
        labels = Map("MAINTAINER" -> "firstName.lastName@gmail.com")
      )
      // run
      val result = sut(app)
      // verify
      result.isFailure shouldBe true
    }

    it("should allow App with MAINTAINER label with multiple valid email addresses from @softec domain") {
      // setup
      val app = AppDefinition(
        id = PathId("/app"),
        labels = Map("MAINTAINER" -> "firstName.lastName@softec.sk,anotherFirstName.anotherLastName@softec.cz,login@softec.sk")
      )
      // run
      val result = sut(app)
      // verify
      result.isSuccess shouldBe true
    }
  }
}

object EmailRegexp {
  val softec: Regex = "[a-zA-Z.]+@softec\\.(sk|cz)(,[a-zA-Z.]+@softec\\.(sk|cz))*".r
}

case object MaintainerValidator
    extends BaseValidator[Map[String, String]](
      labels => labels.get("MAINTAINER").exists(label => EmailRegexp.softec.pattern.matcher(label).matches()),
      _ -> s"Application must have label MAINTAINER with comma separated email addresses of task maintainers."
    )
