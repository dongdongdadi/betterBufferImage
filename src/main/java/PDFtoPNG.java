import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;


import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.io.*;


/**
     * 应用场景：需要提取PDF文档的元素并生成一张PNG图片。
     */
public class PDFtoPNG {
    public static void main(String[] args) throws IOException {
        System.out.println("一次基于BufferedImage的图片处理优化实践.冯凯--->过程复现");
    }

    /**
     * 使用apachePDFBox库将PDF文件提取为PNG格式图片的原本过程，耗时大约4秒。
     */
    @Test
    public void version_1() throws IOException {
        // Step 1 初始化
        PDDocument pdDocument = PDDocument.load(new File("待转换.pdf"));
        PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);
        // 获取第1页PDF文档
        File dest = new File("D:" + File.separator + "image.PNG");
        OutputStream os = new FileOutputStream(dest, true);
        // Step 2
        // 为了保证图片的清晰，这里采用600DPI
        //renderImageWithDPI方法，将文档元素绘制为BufferedImage对象
        BufferedImage image = pdfRenderer.renderImageWithDPI(0, 600);
        // Step 3
        ImageIO.write(image, "PNG", os);
    }

    /**
     * BufferedImage：用来描述一张图片，其内部保存了图片的颜色模型（ColorModel）及像素数据（Raster）。
     * 这里简单解释就是，内部的Raster实现类中，以某种数据结构（如Byte数组）表示图片的所有像素数据，
     * 而ColorModel实现类，则提供了将每个像素的数据，转换为对应RGB颜色的方式。
     */
    @Test
    public void version_2() throws IOException {
        //以下代码为BufferedImage的简单应用
        //将一个GIF图片读取到BufferedImage中，在坐标（10，10）位置打出ABC三个字符，并重新编码成PNG图片
        BufferedImage image = ImageIO.read(new File("test.gif"));
        System.out.println(image);
        image.getGraphics().drawString("ABC", 10, 10);
        ImageIO.write(image, "PNG", new FileOutputStream("result.png"));
    }

    /**
     * 下面这段代码展示了另一类型的例子，它将图片中所有的红色像素点重置成黑色像素点
     * @throws IOException
     */
    @Test
    public void version_3() throws IOException {
        BufferedImage image = ImageIO.read(new File("blackOne.jpg"));
        for(int i = 0 ; i < image.getWidth() ; i++) {
            for(int j = 0 ; j < image.getHeight() ; j++) {
//                -131072
//                System.out.println(image.getRGB(i, j));
                if(image.getRGB(i, j) == Color.black.getRGB()) {
                    image.setRGB(i, j, Color.green.getRGB());
//                    System.out.println(image.getRGB(i, j));
                }
            }
        }
        //RED rgb:-65536
        //System.out.println("RED rgb:" + Color.RED.getRGB());
        ImageIO.write(image, "JPG", new FileOutputStream("greenOne.jpg"));
    }

    /**
     * 如果我们想要取得图片的数据，可以通过BufferedImage内部的Raster对象获得。下面的示例，
     * 展示了采用了字节数组形式存储时，取得内部存储的字节数组的方式。
     * 注意，当需要查询到某一个像素的数据时，需要综合像素的x,y坐标及ColorModel模型中像素数据
     * 的存储方式来决定数组下标。
     */
    @Test
    public void version_4() throws IOException {
        BufferedImage im = ImageIO.read(new File("test.gif"));
        DataBuffer dataBuffer = im.getRaster().getDataBuffer();
        if(dataBuffer instanceof DataBufferByte) {
            DataBufferByte bufferByte = (DataBufferByte) dataBuffer;
            byte[] data = bufferByte.getData();
        }
    }

    /**
     * 经过查看源码，当BufferedImage的imageType=TYPE_BYTE_BINARY（二进制）时，JDK中的PNG编码器
     * 会使用灰度的color type及1位深，而我们发现PDFRender类是有参数可控的，
     * 当传入BINARY时，绘制的BufferedImage的类型即为TYPE_BYTE_BINARY。
     * @throws IOException
     */
    @Test
    public void version_5() throws IOException {
        // Step 1 初始化
        PDDocument pdDocument = PDDocument.load(new File("待转换.pdf"));
        PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);
        // 获取第1页PDF文档
        File dest = new File("D:" + File.separator + "afterImage.PNG");
        OutputStream os = new FileOutputStream(dest, true);
        // Step 2
        // 为了保证图片的清晰，这里采用600DPI
        //renderImageWithDPI方法，将文档元素绘制为BufferedImage对象
        //version_1
        //BufferedImage image = pdfRenderer.renderImageWithDPI(0, 600);
        BufferedImage image = pdfRenderer.renderImageWithDPI(0, 304, ImageType.BINARY);
        // Step 3
        ImageIO.write(image, "PNG", os);
    }

    /**
     * 使用此方法后 version_5，ImageIO.write编码过程耗时减少到150ms左右。
     * 但是这样改后，我们发现生成的PNG图像，与原PDF文档在观感上相比，有一些发“虚”.
     */
    /**
     * 由于TYPE_BYTE_BINARY类型的BufferedImage每个像素只由0，1来表示黑白，
     * 很容易想到，这个现象的原因是出在判断“多灰才算黑”上。
     * 我们来看一下源码中，BINARY类型BufferedImage的ColorModel，是如何判断黑白的。
     * BINARY类型的BufferedImage使用的实现类为IndexColorModel, 确定颜色的代码段如下，
     * 最终由pix变量决定颜色的索引号。
     */

    /**
     *      int minDist = 256;
     *      int d;
     *      // 计算像素的灰度值
     *      int gray = (int) (red*77 + green*150 + blue*29 + 128)/256;
     *      // 在BINARY类型下，map_size = 2
     *      for (int i = 0; i < map_size; i++) {
     *       // rgb数组为调色板，每个数组元素表示一个在图片中可能出现的颜色
     *       // 在BINARY类型下，rgb只有0x00,0xFE两个元素
     *          if (this.rgb[i] == 0x0) {
     *              // For allgrayopaque colormaps, entries are 0
     *              // iff they are an invalid color and should be
     *              // ignored during color searches.
     *              continue;
     *          }
     *          // 分别计算黑&白与当前灰度值的差值
     *          d = (this.rgb[i] & 0xff) - gray;
     *          if (d < 0) d = -d;
     *          // 选择差值较小的一边
     *          if (d < minDist) {
     *              pix = i;
     *              if (d == 0) {
     *                  break;
     *              }
     *              minDist = d;
     *          }
     *      }
     */

    /**
     * 由以上代码，在JDK的实现中，通过像素的灰度值更靠近0和255的哪一个，来确定当前像素是黑是白。
     *     这种实现方式对于通用功能来说是合适的，却不适合我们的业务场景，因为我们生成的图片都是单据，
     *     大部分需要仓库等场景现场打印，需要优先保证内容的准确性，即不能因为图片上某一处灰得有点“浅”，就不显示它。
     *     对于当前业务场景，我们认为简单地设置一个固定的阈值，来区分灰度值是一个适合的方式。
     */

    /**
     *    所以，为解决这个问题，我们设计了2种思路
     *    1、继承实现自己的ColorModel，通过阈值来指定调色板索引号，所有要编码成PNG的BufferedImage
     *    都使用自己实现的ColorModel。
     *    2、不使用JDK默认的PNG编码器，使用其他开源实现,在编码阶段通过判断BufferedImage像素灰度值是
     *    否超过阈值，来决定编入PNG文件的像素数据是黑是白。
     *    从合理性上看，我认为1方案从程序结构角度是更合理的，但是实际应用中，却选择了方案2，理由如下
     *    （1）BufferedImage通常不是自己生成的，我们往往控制不了其他开源工具操作生成的BufferedImage
     *    使用哪种ColorModel，比如我们的项目里PDF Box，IcePdf, Apache poi等开源包都会提供生成
     *    BufferedImage的方法，针对每个开源工具都要重新更改源代码，生成使用自己实现的ColorModel的BufferedImage，
     *    太过于繁琐了，不具有通用性。
     *   （2）JDK提供的PNG编码器不能设置压缩级别
     */
}
