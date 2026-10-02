"""Generates the synthetic TrueType test fonts used by the SPEC006 UI-foundation tests.

The fonts contain no third-party glyph data: every glyph is a generated box whose size depends on
the font variant and code point. They cover Basic Latin, Latin-1 Supplement, Latin Extended-A
(Czech and Hungarian letters) and Cyrillic including Ukrainian letters.

Usage: python3 generate_test_fonts.py   (requires fontTools)
"""
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen

RANGES = [(0x21, 0x7E), (0xA1, 0x17F), (0x400, 0x45F), (0x490, 0x491)]
SPACES = [0x20, 0xA0]


def build(name, base_width, height):
    codepoints = [cp for start, end in RANGES for cp in range(start, end + 1)]
    glyph_order = [".notdef", "space"] + ["u%04X" % cp for cp in codepoints]
    cmap = {cp: "space" for cp in SPACES}
    cmap.update({cp: "u%04X" % cp for cp in codepoints})

    def box(width, top):
        pen = TTGlyphPen(None)
        pen.moveTo((60, 0))
        pen.lineTo((60, top))
        pen.lineTo((width - 60, top))
        pen.lineTo((width - 60, 0))
        pen.closePath()
        return pen.glyph(), width

    glyphs, metrics = {}, {}
    glyphs[".notdef"], width = box(base_width, height)
    metrics[".notdef"] = (width, 60)
    glyphs["space"] = TTGlyphPen(None).glyph()
    metrics["space"] = (base_width // 2, 0)
    for cp in codepoints:
        width = base_width + (cp % 5) * 40
        top = height - (cp % 3) * 60
        glyphs["u%04X" % cp], _ = box(width, top)
        metrics["u%04X" % cp] = (width, 60)

    fb = FontBuilder(1000, isTTF=True)
    fb.setupGlyphOrder(glyph_order)
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=800, descent=-200)
    fb.setupNameTable({"familyName": name, "styleName": "Regular"})
    fb.setupOS2(sTypoAscender=800, sTypoDescender=-200, usWinAscent=800, usWinDescent=200)
    fb.setupPost()
    fb.save(name + ".ttf")


build("UiTestSansA", 500, 700)
build("UiTestSansB", 700, 600)
