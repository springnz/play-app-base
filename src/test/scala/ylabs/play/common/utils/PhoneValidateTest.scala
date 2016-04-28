package ylabs.play.common.utils

import org.scalatest.{Matchers, WordSpec}
import ylabs.play.common.models.User.Phone

class PhoneValidateTest extends WordSpec with Matchers {
  "accept valid mobiles" when {
    "missing country code" in {
      val number = "021234567"
      PhoneValidator.validate(Phone(number)) shouldBe Some(Phone(s"+6421234567"))
    }

    "containing country code" in {
      val number = "+6421234567"
      PhoneValidator.validate(Phone(number)) shouldBe Some(Phone("+6421234567"))
    }

    "containing country code and zero" in {
      val number = "+64021234567"
      PhoneValidator.validate(Phone(number)) shouldBe Some(Phone("+6421234567"))
    }

    "consisting of seven digits after code" in {
      val number = "027777777"
      PhoneValidator.validate(Phone(number)) shouldBe Some(Phone("+6427777777"))
    }

    "consisting of eight digits after code" in {
      val number = "0288888888"
      PhoneValidator.validate(Phone(number)) shouldBe Some(Phone("+64288888888"))
    }

    "consisting of nine digits after code" in {
      val number = "02999999999"
      PhoneValidator.validate(Phone(number)) shouldBe Some(Phone("+642999999999"))
    }

    "containing dashes" in {
      val number = Phone("021-234-5678")
      PhoneValidator.validate(number) shouldBe Some(Phone("+64212345678"))
    }

    "containing spaces" in {
      val number = Phone("021 234  5678")
      PhoneValidator.validate(number) shouldBe Some(Phone("+64212345678"))
    }

    "containing parentheses" in {
      val number = Phone("(021)2345678")
      PhoneValidator.validate(number) shouldBe Some(Phone("+64212345678"))
    }

    "containing everything" in {
      val number = Phone("+64(021) 234-5678")
      PhoneValidator.validate(number) shouldBe Some(Phone("+64212345678"))
    }

    "containing non-breaking space unicode" in {
      val number = Phone("021 234 5678")
      PhoneValidator.validate(number) shouldBe Some(Phone("+64212345678"))
    }

    "missing leading zero" in {
      val number = Phone("212345678")
      PhoneValidator.validate(number) shouldBe Some(Phone("+64212345678"))
    }
  }

  "should reject" when {
    "too few digits" in {
      val number = Phone("021 234 56")
      PhoneValidator.validate(number) shouldBe None
    }

    "too many digits" in {
      val number = Phone("021 234 567890")
      PhoneValidator.validate(number) shouldBe None
    }

    "wrong code" in {
      val number = Phone("012 345 6789")
      PhoneValidator.validate(number) shouldBe None
    }

    "wrong country prefix" in {
      val number = Phone("+61021234567")
      PhoneValidator.validate(number) shouldBe None
    }

    "invalid characters" in {
      val number = Phone("021as123 45")
      PhoneValidator.validate(number) shouldBe None
    }
  }
}

