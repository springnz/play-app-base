package ylabs.play.common.services

import java.awt.Color
import java.awt.image.BufferedImage

import ylabs.play.common.test.{ MyPlaySpec, OneAppPerTestWithOverrides }

class IconServiceTest extends MyPlaySpec with OneAppPerTestWithOverrides {
  "test background" in {
    val height = 30
    val width = 30
    val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    for {
      i ← 0 until height
      j ← 0 until width
    } yield {
      val color = new Color(0, 0, 0, i % 2)
      image.setRGB(i, j, color.getRGB)
    }

    val service = new IconService
    val newImage = service.createImageWithBackground(image, List(Color.red, Color.blue, Color.green))

    for {
      i ← 0 until height
      j ← 0 until width
    } yield {
      val origColor = new Color(0, 0, 0, i % 2)
      image.getRGB(i, j) shouldBe origColor.getRGB

      if (i % 2 == 0) newImage.getRGB(i, j) should (equal(Color.red.getRGB) or equal(Color.blue.getRGB) or equal(Color.green.getRGB))
      else newImage.getRGB(i, j) shouldBe origColor.getRGB
    }

  }
}
