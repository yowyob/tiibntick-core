package com.yowyob.tiibntick.core.media.domain;

/**
 * Output format for generated QR codes.
 *
 * @author MANFOUO Braun
 */
public enum QRFormat {
    /** Portable Network Graphics — raster, suitable for print and display. */
    PNG,
    /** Scalable Vector Graphics — resolution-independent, preferred for web. */
    SVG,
    /** Embedded inside a PDF page. */
    PDF
}
