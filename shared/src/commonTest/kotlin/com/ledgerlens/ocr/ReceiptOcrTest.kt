package com.ledgerlens.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReceiptOcrTest {
    @Test fun boundingBox_properties() { val b = BoundingBox(10, 20, 100, 50); assertEquals(110, b.right); assertEquals(70, b.bottom); assertEquals(60, b.centerX); assertEquals(5000, b.area) }
    @Test fun boundingBox_intersects() { assertTrue(BoundingBox(0, 0, 100, 100).intersects(BoundingBox(50, 50, 100, 100))); assertFalse(BoundingBox(0, 0, 100, 100).intersects(BoundingBox(200, 200, 50, 50))) }
    @Test fun boundingBox_contains() { assertTrue(BoundingBox(10, 10, 100, 100).contains(50, 50)); assertFalse(BoundingBox(10, 10, 100, 100).contains(0, 0)) }
    @Test fun boundingBox_expand() { val e = BoundingBox(10, 10, 100, 100).expand(5); assertEquals(5, e.x); assertEquals(110, e.width) }
    @Test fun textRegion_confidence() { assertTrue(TextRegion("Hi", BoundingBox.EMPTY, 0.95f).isHighConfidence); assertTrue(TextRegion("Lo", BoundingBox.EMPTY, 0.3f).needsReview) }
    @Test fun textLine_combines() { val l = TextLine(listOf(TextRegion("Hello", BoundingBox(0, 0, 50, 20), 0.9f, 1), TextRegion("World", BoundingBox(60, 0, 50, 20), 0.8f, 1)), 1); assertEquals("Hello World", l.text); assertEquals(0.85f, l.confidence, 0.01f) }
    @Test fun ocrResult_success() { val s = OcrResult.Success("Test", 0.85f, listOf(TextRegion("Test", BoundingBox.EMPTY, 0.9f))); assertTrue(s.isHighConfidence); assertFalse(s.needsReview) }
    @Test fun ocrResult_findRegions() { val s = OcrResult.Success("Coffee Shop", 0.9f, listOf(TextRegion("Coffee", BoundingBox.EMPTY, 0.9f), TextRegion("Shop", BoundingBox.EMPTY, 0.85f))); assertEquals(1, s.findRegions("Coffee").size) }
    @Test fun ocrResult_failure() { val f = OcrResult.Failure(OcrError("No text"), OcrErrorCode.NO_TEXT_DETECTED); assertIs<OcrResult.Failure>(f); assertEquals(OcrErrorCode.NO_TEXT_DETECTED, f.errorCode) }
    @Test fun ocrOptions_defaults() { val o = OcrOptions(); assertEquals("en", o.language); assertEquals(RecognitionMode.BALANCED, o.recognitionMode); assertTrue(o.preprocessImage) }
    @Test fun ocrOptions_presets() { assertEquals(RecognitionMode.FAST, OcrOptions.FAST.recognitionMode); assertEquals(RecognitionMode.ACCURATE, OcrOptions.ACCURATE.recognitionMode) }
    @Test fun confidenceLevel() { assertEquals(OcrConfidenceLevel.VERY_HIGH, OcrConfidenceLevel.fromScore(0.95f)); assertEquals(OcrConfidenceLevel.LOW, OcrConfidenceLevel.fromScore(0.5f)) }
    @Test fun imageFormat_extension() { assertEquals(ImageFormat.JPEG, ImageFormat.fromExtension("jpg")); assertEquals(ImageFormat.PNG, ImageFormat.fromExtension("png")); assertEquals(null, ImageFormat.fromExtension("xyz")) }
    @Test fun imageFormat_mimeType() { assertEquals(ImageFormat.JPEG, ImageFormat.fromMimeType("image/jpeg")); assertEquals(null, ImageFormat.fromMimeType("application/pdf")) }
    @Test fun preprocessOptions_presets() { assertFalse(PreprocessOptions.MINIMAL.convertToGrayscale); assertTrue(PreprocessOptions.RECEIPT.enhanceContrast); assertTrue(PreprocessOptions.AGGRESSIVE.sharpen); assertTrue(PreprocessOptions.DOCUMENT_SCAN.binarize) }
    @Test fun imageSize_calculations() { val s = ImageSize(1000, 500); assertEquals(2.0f, s.aspectRatio, 0.01f); assertEquals(500_000L, s.pixels); assertEquals(500, s.scale(0.5f).width); assertEquals(800, s.fitWidth(800).width) }
    @Test fun imageValidation() { assertTrue(ImageValidation.VALID.isValid); assertFalse(ImageValidation.VALID.hasErrors); val inv = ImageValidation.invalid(ValidationIssue(ValidationIssueCode.IMAGE_TOO_SMALL, "Too small", IssueSeverity.ERROR)); assertFalse(inv.isValid); assertTrue(inv.hasErrors) }
    @Test fun ocrLimits() { assertEquals(50 * 1024 * 1024L, OcrLimits.MAX_IMAGE_SIZE_BYTES); assertEquals(10_000, OcrLimits.MAX_IMAGE_DIMENSION); assertEquals(50, OcrLimits.MIN_IMAGE_DIMENSION) }
    @Test fun textBlock() { val b = TextBlock(listOf(TextLine(listOf(TextRegion("L1", BoundingBox.EMPTY, 0.9f, 1)), 1), TextLine(listOf(TextRegion("L2", BoundingBox.EMPTY, 0.8f, 2)), 2)), BlockType.BODY); assertEquals("L1\nL2", b.text) }
}
