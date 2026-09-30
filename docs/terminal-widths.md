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

Joined emoji sequences, spacing marks and variation-selector width changes still
need work. Check glyph placement and font fallback on a device.

The data is covered by [Unicode License V3](../licenses/Unicode.txt). The same
notice is included in the app's assets.
