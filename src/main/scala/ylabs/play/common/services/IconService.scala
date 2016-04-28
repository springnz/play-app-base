package ylabs.play.common.services

import java.awt.Color
import java.awt.image.{ WritableRaster, BufferedImage }

import scala.util.Random

class IconService {
  def createImageWithBackground(image: BufferedImage, palette: List[Color]): BufferedImage = {
    val index = new Random().nextInt(palette.length)
    val backColor = palette.slice(index, index + 1).head
    val raster = image.copyData(null)
    val copy = new BufferedImage(image.getColorModel, raster, image.isAlphaPremultiplied, null)
    for {
      i ← 0 until copy.getWidth
      j ← 0 until copy.getHeight
    } yield {
      new Color(copy.getRGB(i, j), true) match {
        case c if c.getAlpha == 0 ⇒
          copy.setRGB(i, j, backColor.getRGB)
        case _ ⇒
      }
    }
    copy
  }
}
