# Terminal widths

The character tables use Unicode 17.0.0:

- [EastAsianWidth.txt](https://www.unicode.org/Public/17.0.0/ucd/EastAsianWidth.txt): Wide and Fullwidth ranges use two columns.
- [DerivedGeneralCategory.txt](https://www.unicode.org/Public/17.0.0/ucd/extracted/DerivedGeneralCategory.txt): Nonspacing and Enclosing Mark ranges use zero columns, even when their width property is Wide.

Other characters use one column. Ambiguous width defaults to one. These tables
do not determine the width of an entire emoji sequence.

TerminalWidth merges adjacent ranges and searches their endpoints. The test
fixture keeps the source ranges unmerged and compares every Unicode code point.

The table is used for combining marks and two-column grid placement, so older
Android versions use the same widths. Continuation cells hold no text. Copying
either half includes the complete character once. Edits and resizing remove
orphan halves. A wide character wraps before a one-column gap; without wrapping
it is ignored when it cannot fit. A one-column grid uses a replacement character.

Joined emoji, skin tones and flag pairs share one cell. A character listed with
an emoji style in
[emoji-variation-sequences.txt](https://www.unicode.org/Public/17.0.0/ucd/emoji/emoji-variation-sequences.txt)
becomes two columns when U+FE0F follows it and the next cell is free; ASCII
characters stay one column. Spacing marks and the text-style selector (U+FE0E)
still need work. Check glyph placement and font fallback on a device.

Spacing marks currently occupy their own grid cells. Devanagari U+093E and
U+093F and Bengali U+09BE use one column; U+16FF0 uses two. A regression test
keeps those widths separate from the zero-width mark table. This does not
verify shaping across cells.

Character boundaries and column widths need separate handling. Unicode 17's
[text segmentation rules](https://www.unicode.org/reports/tr29/tr29-47.html#Grapheme_Cluster_Boundary_Rules)
keep SpacingMark characters with the preceding character, but that property
is not identical to the general Spacing Mark category. Shaping and selection
must use the boundary properties without assuming every mark has zero width.

The data is covered by [Unicode License V3](../licenses/Unicode.txt). The same
notice is included in the app's assets.
