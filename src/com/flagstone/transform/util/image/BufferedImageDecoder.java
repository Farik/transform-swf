package com.flagstone.transform.util.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import javax.imageio.ImageIO;

import com.flagstone.transform.Strings;
import com.flagstone.transform.coder.ImageTag;
import com.flagstone.transform.coder.SWFDecoder;
import com.flagstone.transform.datatype.ImageFormat;
import com.flagstone.transform.image.DefineImage;
import com.flagstone.transform.image.DefineImage2;

/**
 * BufferedImageDecoder decodes BufferedImages so they can be used in a Flash
 * file.
 */
public final class BufferedImageDecoder implements ImageProvider, ImageDecoder {

    private transient ImageFormat format;
    private transient int width;
    private transient int height;
    private transient byte[] table;
    private transient byte[] image;

    /** TODO(method). */
    public ImageDecoder newDecoder() {
        return new BufferedImageDecoder();
    }

    /** TODO(method). */
    public void read(final File file) throws IOException, DataFormatException {
         read(new FileInputStream(file), (int) file.length());
    }

    /** TODO(method). */
    public void read(final URL url) throws IOException, DataFormatException {
        final URLConnection connection = url.openConnection();
        final int fileSize = connection.getContentLength();

        if (fileSize < 0) {
            throw new FileNotFoundException(url.getFile());
        }

        read(url.openStream(), fileSize);
    }

    /** TODO(method). */
    public void read(final InputStream stream, final int size) throws IOException, DataFormatException {
        read(ImageIO.read(stream));
    }

    /** TODO(method). */
    public ImageTag defineImage(final int identifier) {
        ImageTag object = null;

        switch (format) {
        case IDX8:
            object = new DefineImage(identifier, width, height, table.length,
                    zip(merge(adjustScan(width, height, image), table)));
            break;
        case IDXA:
            object = new DefineImage2(identifier, width, height, table.length,
                    zip(mergeAlpha(adjustScan(width, height, image), table)));
            break;
        case RGB5:
            object = new DefineImage(identifier, width, height,
                    zip(packColours(width, height, image)), 16);
            break;
        case RGB8:
            orderAlpha(image);
            object = new DefineImage(identifier, width, height, zip(image), 24);
            break;
        case RGBA:
            applyAlpha(image);
            object = new DefineImage2(identifier, width, height, zip(image));
            break;
        default:
            throw new AssertionError(Strings.INVALID_FORMAT);
        }
        return object;
    }

    /**
     * Create an image definition from a BufferedImage.
     *
     * @param identifier
     *            the unique identifier that will be used to refer to the image
     *            in the Flash file.
     *
     * @param obj
     *            the BufferedImage containing the image.
     *
     * @return an image definition that can be added to a Movie.
     *
     * @throws DataFormatException
     *             if there is a problem extracting the image, from the
     *             BufferedImage image.
     */
    public ImageTag defineImage(final int identifier, final BufferedImage obj)
            throws DataFormatException {
        ImageTag object = null;

        final BufferedImageDecoder decoder = new BufferedImageDecoder();
        decoder.read(obj);

        switch (format) {
        case IDX8:
            object = new DefineImage(identifier, width, height, table.length,
                    zip(merge(adjustScan(width, height, image), table)));
            break;
        case IDXA:
            object = new DefineImage2(identifier, width, height, table.length,
                    zip(mergeAlpha(adjustScan(width, height, image), table)));
            break;
        case RGB5:
            object = new DefineImage(identifier, width, height,
                    zip(packColours(width, height, image)), 16);
            break;
        case RGB8:
            orderAlpha(image);
            object = new DefineImage(identifier, width, height, zip(image), 24);
            break;
        case RGBA:
            applyAlpha(image);
            object = new DefineImage2(identifier, width, height, zip(image));
            break;
        default:
            throw new DataFormatException(Strings.INVALID_IMAGE);
        }
        return object;
    }

    /**
     * Create a BufferedImage from a Flash image.
     *
     * @param image
     *            an image from a Flash file.
     *
     * @return a BufferedImage containing the image.
     *
     * @throws DataFormatException
     *             if there is a problem decoding the BufferedImage.
     */
    public BufferedImage bufferedImage(final DefineImage image)
            throws DataFormatException {
        BufferedImage bufferedImage = null;

        ImageFormat format;
        int width = 0;
        int height = 0;

        byte[] colourTable = null;
        byte[] indexedImage = null;
        byte[] colorImage = null;

        width = image.getWidth();
        height = image.getHeight();

        final byte[] data = unzip(image.getData(), width, height);

        final int scanLength = (width + 3) & ~3;
        final int tableLength = image.getTableSize();
        final int pixelLength = image.getPixelSize();

        int pos = 0;
        int index = 0;

        if (tableLength > 0) {
            format = ImageFormat.IDX8;
            width = image.getWidth();
            height = image.getHeight();
            colourTable = new byte[tableLength * 4];
            indexedImage = new byte[height * width];

            for (int i = 0; i < tableLength; i++, index += 4) {
                colourTable[index + 3] = -1;
                colourTable[index + 2] = data[pos++];
                colourTable[index + 1] = data[pos++];
                colourTable[index] = data[pos++];
            }

            index = 0;

            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++, index++) {
                    indexedImage[index] = data[pos++];
                }
                pos += (scanLength - width);
            }
        } else {
            format = ImageFormat.RGB8;
            width = image.getWidth();
            height = image.getHeight();
            colorImage = new byte[height * width * 4];
            index = 0;

            if (pixelLength == 16) {
                for (int h = 0; h < height; h++) {
                    for (int w = 0; w < width; w++, index += 4) {
                        final int color = (data[pos++] << 8 | (data[pos++] & 0xFF)) & 0x7FFF;

                        colorImage[index + 3] = -1;
                        colorImage[index + 0] = (byte) (color >> 10);
                        colorImage[index + 1] = (byte) ((color >> 5) & 0x1F);
                        colorImage[index + 2] = (byte) (color & 0x1F);
                    }
                    pos += (scanLength - width);
                }
            } else {
                index = 0;

                for (int h = 0; h < height; h++) {
                    for (int w = 0; w < width; w++, index += 4) {
                        colorImage[index + 3] = -1;
                        colorImage[index + 0] = data[pos++];
                        colorImage[index + 1] = data[pos++];
                        colorImage[index + 2] = data[pos++];
                    }
                    pos += (scanLength - width);
                }
            }
        }

        switch (format) {
        case IDX8:
        case IDXA:
            final byte[] red = new byte[colourTable.length];
            final byte[] green = new byte[colourTable.length];
            final byte[] blue = new byte[colourTable.length];
            final byte[] alpha = new byte[colourTable.length];
            index = 0;

            for (int i = 0; i < colourTable.length; i++, index += 4) {
                red[i] = colourTable[index + 2];
                green[i] = colourTable[index + 1];
                blue[i] = colourTable[index + 0];
                alpha[i] = colourTable[index + 3];
            }

            bufferedImage = new BufferedImage(width, height,
                    BufferedImage.TYPE_INT_ARGB);

            final int[] indexedBuffer = new int[width];
            int color;
            index = 0;

            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++, index++) {
                    color = indexedImage[index] << 2;

                    indexedBuffer[j] = (colourTable[color + 3] & 0xFF) << 24;
                    indexedBuffer[j] = indexedBuffer[j]
                            | ((colourTable[color + 2] & 0xFF) << 16);
                    indexedBuffer[j] = indexedBuffer[j]
                            | ((colourTable[color + 1] & 0xFF) << 8);
                    indexedBuffer[j] = indexedBuffer[j]
                            | (colourTable[color + 0] & 0xFF);
                }

                bufferedImage.setRGB(0, i, width, 1, indexedBuffer, 0, width);
            }
            break;
        case RGB5:
        case RGB8:
        case RGBA:
            bufferedImage = new BufferedImage(width, height,
                    BufferedImage.TYPE_INT_ARGB);

            final int[] directBuffer = new int[width];
            index = 0;

            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++, index += 4) {
                    // int a = colorImage[i][j][3] & 0xFF;

                    /*
                     * directBuffer[j] = (colorImage[i][j][3] << 24) |
                     * (colorImage[i][j][0] << 16) | (colorImage[i][j][1] << 8)
                     * | colorImage[i][j][2];
                     */
                    directBuffer[j] = (colorImage[index + 3] & 0xFF) << 24;
                    directBuffer[j] = directBuffer[j]
                            | ((colorImage[index + 0] & 0xFF) << 16);
                    directBuffer[j] = directBuffer[j]
                            | ((colorImage[index + 1] & 0xFF) << 8);
                    directBuffer[j] = directBuffer[j]
                            | (colorImage[index + 2] & 0xFF);
                }
                bufferedImage.setRGB(0, i, width, 1, directBuffer, 0, width);
            }
            break;
        default:
            throw new DataFormatException(Strings.INVALID_IMAGE);
        }

        return bufferedImage;
    }

    /** TODO(method). */
    public int getWidth() {
        return width;
    }

    /** TODO(method). */
    public int getHeight() {
        return height;
    }

    /** TODO(method). */
    public byte[] getImage() {
        return Arrays.copyOf(image, image.length);
    }

    /**
     * Create a BufferedImage from a Flash image.
     *
     * @param image
     *            an image from a Flash file.
     *
     * @return a BufferedImage containing the image.
     *
     * @throws DataFormatException
     *             if there is a problem decoding the BufferedImage.
     */
    public BufferedImage bufferedImage(final DefineImage2 image)
            throws DataFormatException {
        BufferedImage bufferedImage = null;

        ImageFormat format;
        int width = 0;
        int height = 0;

        byte[] colourTable = null;
        byte[] indexedImage = null;
        byte[] colorImage = null;

        width = image.getWidth();
        height = image.getHeight();

        final byte[] data = unzip(image.getData(), width, height);

        final int scanLength = (width + 3) & ~3;
        final int tableLength = image.getTableSize();
        // int pixelLength = image.getPixelSize();

        int pos = 0;
        int index = 0;

        if (tableLength > 0) {
            format = ImageFormat.IDXA;
            width = image.getWidth();
            height = image.getHeight();
            colourTable = new byte[tableLength * 4];
            indexedImage = new byte[height * width];

            for (int i = 0; i < tableLength; i++, index += 4) {
                colourTable[index + 3] = data[pos++];
                colourTable[index + 2] = data[pos++];
                colourTable[index + 1] = data[pos++];
                colourTable[index] = data[pos++];
            }

            index = 0;

            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++, index++) {
                    indexedImage[index] = data[pos++];
                }
                pos += (scanLength - width);
            }
        } else {
            format = ImageFormat.RGBA;
            width = image.getWidth();
            height = image.getHeight();
            colorImage = new byte[height * width * 4];

            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++, index += 4) {
                    colorImage[index + 3] = data[pos++];
                    colorImage[index + 0] = data[pos++];
                    colorImage[index + 1] = data[pos++];
                    colorImage[index + 2] = data[pos++];
                }
            }
        }

        switch (format) {
        case IDX8:
        case IDXA:
            final byte[] red = new byte[colourTable.length];
            final byte[] green = new byte[colourTable.length];
            final byte[] blue = new byte[colourTable.length];
            final byte[] alpha = new byte[colourTable.length];
            index = 0;

            for (int i = 0; i < colourTable.length; i++, index += 4) {
                red[i] = colourTable[index + 2];
                green[i] = colourTable[index + 1];
                blue[i] = colourTable[index + 0];
                alpha[i] = colourTable[index + 3];
            }

            bufferedImage = new BufferedImage(width, height,
                    BufferedImage.TYPE_INT_ARGB);

            final int[] indexedBuffer = new int[width];
            int color;
            index = 0;

            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++, index++) {
                    color = indexedImage[index] << 2;

                    indexedBuffer[j] = (colourTable[color + 3] & 0xFF) << 24;
                    indexedBuffer[j] = indexedBuffer[j]
                            | ((colourTable[color + 2] & 0xFF) << 16);
                    indexedBuffer[j] = indexedBuffer[j]
                            | ((colourTable[color + 1] & 0xFF) << 8);
                    indexedBuffer[j] = indexedBuffer[j]
                            | (colourTable[color + 0] & 0xFF);
                }

                bufferedImage.setRGB(0, i, width, 1, indexedBuffer, 0, width);
            }
            break;
        case RGB5:
        case RGB8:
        case RGBA:
            bufferedImage = new BufferedImage(width, height,
                    BufferedImage.TYPE_INT_ARGB);

            final int[] directBuffer = new int[width];
            index = 0;

            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++, index += 4) {
                    // int a = colorImage[i][j][3] & 0xFF;

                    /*
                     * directBuffer[j] = (colorImage[i][j][3] << 24) |
                     * (colorImage[i][j][0] << 16) | (colorImage[i][j][1] << 8)
                     * | colorImage[i][j][2];
                     */
                    directBuffer[j] = (colorImage[index + 3] & 0xFF) << 24;
                    directBuffer[j] = directBuffer[j]
                            | ((colorImage[index + 0] & 0xFF) << 16);
                    directBuffer[j] = directBuffer[j]
                            | ((colorImage[index + 1] & 0xFF) << 8);
                    directBuffer[j] = directBuffer[j]
                            | (colorImage[index + 2] & 0xFF);
                }
                bufferedImage.setRGB(0, i, width, 1, directBuffer, 0, width);
            }
            break;
        default:
            throw new DataFormatException(Strings.INVALID_IMAGE);
        }

        return bufferedImage;
    }

    /**
     * Resizes a BufferedImage to the specified width and height. The aspect
     * ratio of the image is maintained so the area in the new image not covered
     * by the resized original will be transparent.
     *
     * @param image
     *            the BufferedImage to resize.
     * @param width
     *            the width of the resized image in pixels.
     * @param height
     *            the height of the resized image in pixels.
     * @return a new BufferedImage with the specified width and height.
     */
    public BufferedImage resizeImage(final BufferedImage image,
            final int width, final int height) {
        int imageType = image.getType();

        if (imageType == BufferedImage.TYPE_CUSTOM) {
            imageType = BufferedImage.TYPE_4BYTE_ABGR;
        }

        final BufferedImage resized = new BufferedImage(width, height,
                BufferedImage.TYPE_4BYTE_ABGR);

        final double widthRatio = (double) image.getWidth() / (double) width;
        final double heightRatio = (double) image.getHeight()
                / (double) height;
        double ratio = (widthRatio > heightRatio ? widthRatio : heightRatio);

        if (ratio < 1.0) {
            ratio = 1.0;
        }

        final int imageWidth = (int) (image.getWidth() / ratio);
        final int imageHeight = (int) (image.getHeight() / ratio);

        final int xCoord = (width - imageWidth) / 2;
        final int yCoord = (height - imageHeight) / 2;

        final Graphics2D graphics = resized.createGraphics();
        graphics.setColor(new Color(0.0f, 0.0f, 0.0f, 0.0f));
        graphics.fillRect(0, 0, width, height);

        final java.awt.Image scaled = image.getScaledInstance(imageWidth,
                imageHeight, java.awt.Image.SCALE_SMOOTH);
        new javax.swing.ImageIcon(scaled);

        graphics.drawImage(scaled, xCoord, yCoord, null);
        graphics.dispose();
        resized.flush();

        new javax.swing.ImageIcon(resized).getImage();

        return resized;
    }

    /** TODO(method). */
    public void read(final BufferedImage obj) throws DataFormatException {

        final DataBuffer buffer = obj.getData().getDataBuffer();

        width = obj.getWidth();
        height = obj.getHeight();

        int index;

        if (buffer.getDataType() == DataBuffer.TYPE_INT) {
            final int[] pixels = ((DataBufferInt) buffer).getData();

            switch (obj.getType()) {
            case BufferedImage.TYPE_INT_ARGB:
                format = ImageFormat.RGBA;
                image = new byte[height * width * 4];
                index = 0;

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++, index += 4) {
                        final int pixel = pixels[y * width + x];

                        image[index + 3] = (byte) (pixel >> 24);
                        image[index + 2] = (byte) (pixel >> 16);
                        image[index + 1] = (byte) (pixel >> 8);
                        image[index] = (byte) pixel;
                    }
                }
                break;
            case BufferedImage.TYPE_INT_ARGB_PRE:
                format = ImageFormat.RGBA;
                image = new byte[height * width * 4];
                index = 0;

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++, index += 4) {
                        final int pixel = pixels[y * width + x];

                        image[index + 3] = (byte) (pixel >> 24);
                        image[index + 2] = (byte) (pixel >> 16);
                        image[index + 1] = (byte) (pixel >> 8);
                        image[index] = (byte) pixel;
                    }
                }
                break;
            case BufferedImage.TYPE_INT_BGR:
                format = ImageFormat.RGB8;
                image = new byte[height * width * 4];
                index = 0;

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++, index += 4) {
                        final int pixel = pixels[y * width + x];

                        image[index + 3] = -1;
                        image[index + 2] = (byte) (pixel >> 16);
                        image[index + 1] = (byte) (pixel >> 8);
                        image[index] = (byte) pixel;
                    }
                }
                break;
            case BufferedImage.TYPE_INT_RGB:
                format = ImageFormat.RGB8;
                image = new byte[height * width * 4];
                index = 0;

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++, index += 4) {
                        final int pixel = pixels[y * width + x];

                        image[index + 3] = -1;
                        image[index] = (byte) (pixel >> 16);
                        image[index + 1] = (byte) (pixel >> 8);
                        image[index + 2] = (byte) pixel;
                    }
                }
                break;
            default:
                throw new DataFormatException(Strings.INVALID_IMAGE);
            }

        } else if (buffer.getDataType() == DataBuffer.TYPE_BYTE) {
            final byte[] pixels = ((DataBufferByte) buffer).getData();

            switch (obj.getType()) {
            case BufferedImage.TYPE_3BYTE_BGR:
                format = ImageFormat.RGB8;
                image = new byte[height * width * 4];
                index = 0;

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++, index += 4) {
                        final int offset = 3 * (y * width + x);

                        image[index + 3] = -1;
                        image[index + 2] = pixels[offset];
                        image[index + 1] = pixels[offset + 1];
                        image[index] = pixels[offset + 2];
                    }
                }
                break;
            case BufferedImage.TYPE_CUSTOM:
                if (width * height * 3 == pixels.length) {
                    format = ImageFormat.RGBA;
                    image = new byte[height * width * 4];
                    index = 0;

                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++, index += 4) {
                            final int offset = 3 * (y * width + x);

                            image[index] = pixels[offset];
                            image[index + 1] = pixels[offset + 1];
                            image[index + 2] = pixels[offset + 2];
                            image[index + 3] = -1;
                        }
                    }
                }
                if (width * height * 4 == pixels.length) {
                    format = ImageFormat.RGBA;
                    image = new byte[height * width * 4];
                    index = 0;
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++, index += 4) {
                            final int offset = 4 * (y * width + x);

                            image[index] = pixels[offset];
                            image[index + 1] = pixels[offset + 1];
                            image[index + 2] = pixels[offset + 2];
                            image[index + 3] = pixels[offset + 3];
                        }
                    }
                }
                break;
            case BufferedImage.TYPE_4BYTE_ABGR:
                format = ImageFormat.RGBA;
                image = new byte[height * width * 4];
                index = 0;

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++, index += 4) {
                        final int offset = 4 * (y * width + x);

                        image[index + 3] = pixels[offset];
                        image[index + 2] = pixels[offset + 1];
                        image[index + 1] = pixels[offset + 2];
                        image[index] = pixels[offset + 3];
                    }
                }
                break;
            case BufferedImage.TYPE_4BYTE_ABGR_PRE:
                format = ImageFormat.RGBA;
                image = new byte[height * width * 4];
                index = 0;

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++, index += 4) {
                        final int offset = 4 * (y * width + x);

                        image[index + 3] = pixels[offset];
                        image[index + 2] = pixels[offset + 1];
                        image[index + 1] = pixels[offset + 2];
                        image[index] = pixels[offset + 3];
                    }
                }
                break;
            case BufferedImage.TYPE_BYTE_BINARY:
                format = ImageFormat.IDX8;
                image = new byte[height * width];
                int depth = obj.getColorModel().getPixelSize();
                ColorModel model = obj.getColorModel();
                index = 0;

                if (model instanceof IndexColorModel) {
                    final IndexColorModel indexModel = (IndexColorModel) model;
                    final int tableSize = indexModel.getMapSize();
                    table = new byte[tableSize * 4];

                    final byte[] reds = new byte[tableSize];
                    final byte[] blues = new byte[tableSize];
                    final byte[] greens = new byte[tableSize];

                    indexModel.getReds(reds);
                    indexModel.getGreens(greens);
                    indexModel.getBlues(blues);

                    for (int i = 0; i < tableSize; i++, index += 4) {
                        table[index] = reds[i];
                        table[index + 1] = greens[i];
                        table[index + 2] = blues[i];
                        table[index + 3] = -1;
                    }
                }

                index = 0;
                final SWFDecoder coder = new SWFDecoder(pixels);

                for (int y = 0; y < height; y++) {
                    int bitsRead = 0;

                    for (int x = 0; x < width; x++, index++) {
                        image[index] = (byte) coder.readBits(depth, false);
                        bitsRead += depth;
                    }
                    if (bitsRead % 32 > 0) {
                        coder.adjustPointer(32 - (bitsRead % 32));
                    }
                }
                break;
            case BufferedImage.TYPE_BYTE_GRAY:
                format = ImageFormat.RGB8;
                image = new byte[height * width * 4];
                index = 0;

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++, index += 4) {
                        final int offset = (y * width + x);

                        image[index + 3] = -1;
                        image[index + 2] = pixels[offset];
                        image[index + 1] = pixels[offset];
                        image[index] = pixels[offset];
                    }
                }
                break;
            case BufferedImage.TYPE_BYTE_INDEXED:
                format = ImageFormat.IDX8;
                image = new byte[height * width];
                depth = obj.getColorModel().getPixelSize();
                model = obj.getColorModel();
                index = 0;

                if (model instanceof IndexColorModel) {
                    final IndexColorModel indexModel = (IndexColorModel) model;
                    final int tableSize = indexModel.getMapSize();

                    table = new byte[tableSize * 4];

                    final byte[] reds = new byte[tableSize];
                    final byte[] blues = new byte[tableSize];
                    final byte[] greens = new byte[tableSize];

                    indexModel.getReds(reds);
                    indexModel.getGreens(greens);
                    indexModel.getBlues(blues);

                    for (int i = 0; i < tableSize; i++, index += 4) {
                        table[index] = reds[i];
                        table[index + 1] = greens[i];
                        table[index + 2] = blues[i];
                        table[index + 3] = -1;
                    }
                }

                index = 0;

                for (int y = 0; y < height; y++) {
                    System.arraycopy(pixels, y * width, image, index, width);
                    index += width;
                }
                break;
            default:
                throw new DataFormatException(Strings.INVALID_IMAGE);
            }
        } else if (buffer.getDataType() == DataBuffer.TYPE_USHORT) {
            final short[] pixels = ((DataBufferUShort) buffer).getData(); // NOPMD
            // AvoidUsingShortType

            switch (obj.getType()) {
            case BufferedImage.TYPE_USHORT_555_RGB:
                throw new DataFormatException(Strings.INVALID_IMAGE);
            case BufferedImage.TYPE_USHORT_565_RGB:
                throw new DataFormatException(Strings.INVALID_IMAGE);
            case BufferedImage.TYPE_USHORT_GRAY:
                format = ImageFormat.RGB8;
                image = new byte[height * width * 4];
                index = 0;

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++, index += 4) {
                        // int row = height-y-1;
                        final int offset = (y * width + x);

                        image[index + 3] = -1;
                        image[index + 2] = (byte) pixels[offset];
                        image[index + 1] = (byte) pixels[offset];
                        image[index] = (byte) pixels[offset];
                    }
                }
                break;
            default:
                throw new DataFormatException(Strings.INVALID_IMAGE);
            }
        } else {
            throw new DataFormatException(Strings.INVALID_IMAGE);
        }
    }

    private void orderAlpha(final byte[] image) {
        byte alpha;

        for (int i = 0; i < image.length; i += 4) {
            alpha = image[i + 3];

            image[i + 3] = image[i + 2];
            image[i + 2] = image[i + 1];
            image[i + 1] = image[i];
            image[i] = alpha;
        }
    }

    private void applyAlpha(final byte[] image) {
        int alpha;

        for (int i = 0; i < image.length; i += 4) {
            alpha = image[i + 3] & 0xFF;

            image[i] = (byte) (((image[i] & 0xFF) * alpha) / 255);
            image[i + 1] = (byte) (((image[i + 1] & 0xFF) * alpha) / 255);
            image[i + 2] = (byte) (((image[i + 2] & 0xFf) * alpha) / 255);
        }
    }

    private byte[] merge(final byte[] image, final byte[] table) {
        final byte[] merged = new byte[(table.length / 4) * 3 + image.length];
        int dst = 0;

        for (int i = 0; i < table.length; i += 4) {
            merged[dst++] = table[i]; // R
            merged[dst++] = table[i + 1]; // G
            merged[dst++] = table[i + 2]; // B
        }

        for (final byte element : image) {
            merged[dst++] = element;
        }

        return merged;
    }

    private byte[] mergeAlpha(final byte[] image, final byte[] table) {
        final byte[] merged = new byte[table.length + image.length];
        int dst = 0;

        for (final byte element : table) {
            merged[dst++] = element;
        }

        for (final byte element : image) {
            merged[dst++] = element;
        }
        return merged;
    }

    private byte[] zip(final byte[] image) {
        final Deflater deflater = new Deflater();
        deflater.setInput(image);
        deflater.finish();

        final byte[] compressedData = new byte[image.length * 2];
        final int bytesCompressed = deflater.deflate(compressedData);
        final byte[] newData = Arrays.copyOf(compressedData, bytesCompressed);

        return newData;
    }

    private byte[] adjustScan(final int width, final int height,
            final byte[] image) {
        int src = 0;
        int dst = 0;
        int row;
        int col;

        int scan = 0;
        byte[] formattedImage = null;

        scan = (width + 3) & ~3;
        formattedImage = new byte[scan * height];

        for (row = 0; row < height; row++) {
            for (col = 0; col < width; col++) {
                formattedImage[dst++] = image[src++];
            }

            while (col++ < scan) {
                formattedImage[dst++] = 0;
            }
        }

        return formattedImage;
    }

    private byte[] packColours(final int width, final int height,
            final byte[] image) {
        int src = 0;
        int dst = 0;
        int row;
        int col;

        final int scan = width + (width & 1);
        final byte[] formattedImage = new byte[scan * height * 2];

        for (row = 0; row < height; row++) {
            for (col = 0; col < width; col++, src++) {
                final int red = (image[src++] & 0xF8) << 7;
                final int green = (image[src++] & 0xF8) << 2;
                final int blue = (image[src++] & 0xF8) >> 3;
                final int colour = (red | green | blue) & 0x7FFF;

                formattedImage[dst++] = (byte) (colour >> 8);
                formattedImage[dst++] = (byte) colour;
            }

            while (col < scan) {
                formattedImage[dst++] = 0;
                formattedImage[dst++] = 0;
                col++;
            }
        }
        return formattedImage;
    }

    private byte[] unzip(final byte[] bytes, final int width,
            final int height) throws DataFormatException {
        final byte[] data = new byte[width * height * 8];
        int count = 0;

        final Inflater inflater = new Inflater();
        inflater.setInput(bytes);
        count = inflater.inflate(data);
        inflater.end();

        final byte[] uncompressedData = new byte[count];

        System.arraycopy(data, 0, uncompressedData, 0, count);

        return uncompressedData;
    }
}
